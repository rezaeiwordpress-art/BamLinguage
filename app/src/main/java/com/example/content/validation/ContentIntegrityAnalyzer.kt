package com.example.content.validation

import com.example.content.schema.ContentPackageJsonDto
import com.example.core.common.CefrLevel
import com.example.domain.model.ContentPackageBundle
import com.example.domain.model.GlossaryEntry
import com.example.domain.model.ImportValidationError
import com.example.domain.model.PronunciationInfo
import com.example.domain.model.Sentence
import com.example.domain.model.WordOccurrence
import com.example.domain.vocabulary.MorphologyAnalyzer
import com.example.domain.vocabulary.VocabularyResolutionEngine

enum class IntegritySeverity(val persianLabel: String) {
  FATAL("خطای ساختاری بحرانی"),
  MISSING_VOCAB("هشدار واژگان پوشش‌داده‌نشده"),
  LINGUISTIC_QUALITY("هشدار کیفیت زبان‌شناختی"),
}

data class IntegrityIssue(
  val severity: IntegritySeverity,
  val fieldPath: String,
  val messageFa: String,
  val messageEn: String,
)

data class ContentIntegrityReport(
  val packageId: String,
  val title: String,
  val version: Int,
  val sentenceCount: Int,
  val glossaryCount: Int,
  val questionCount: Int,
  val totalMeaningfulTokens: Int,
  val coveredTokens: Int,
  val uncoveredTokens: List<String>,
  val fatalErrors: List<ImportValidationError>,
  val missingVocabularyWarnings: List<IntegrityIssue>,
  val linguisticWarnings: List<IntegrityIssue>,
) {
  val coveragePercent: Int
    get() =
      if (totalMeaningfulTokens <= 0) 0
      else ((coveredTokens * 100.0) / totalMeaningfulTokens).toInt().coerceIn(0, 100)

  val isValidForImport: Boolean
    get() = fatalErrors.isEmpty()

  val hasCompleteDictionaryCoverage: Boolean
    get() = fatalErrors.isEmpty() && totalMeaningfulTokens > 0 && uncoveredTokens.isEmpty()
}

class ContentIntegrityAnalyzer(
  private val validator: ContentPackageValidator = ContentPackageValidator(),
  private val resolutionEngine: VocabularyResolutionEngine = VocabularyResolutionEngine(),
) {

  fun analyzeDto(dto: ContentPackageJsonDto): ContentIntegrityReport {
    val fatalErrors =
      when (val outcome = validator.validate(dto)) {
        is ValidationOutcome.Valid -> emptyList()
        is ValidationOutcome.Invalid -> outcome.errors
      }

    val missingVocabWarnings = mutableListOf<IntegrityIssue>()
    val linguisticWarnings = mutableListOf<IntegrityIssue>()

    // Map DTO to transient structures for token coverage and phrase determinism inspection
    val sentenceTexts = dto.sentences.associate { it.id to it.text }
    val allGlossaryEntries =
      dto.glossary.map { g ->
        GlossaryEntry(
          id = g.id,
          packageId = dto.id,
          sourceGlossaryId = g.id,
          term = g.term.trim(),
          normalizedTerm = g.term.trim().lowercase(),
          lemma = g.lemma.ifBlank { g.term }.trim().lowercase(),
          forms = g.forms.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct(),
          partOfSpeech = g.pos.trim(),
          cefr = CefrLevel.fromCode(g.cefr) ?: CefrLevel.A1,
          senses = emptyList(),
          phonetic = g.pronunciation?.us ?: g.pronunciation?.uk,
          pronunciation = PronunciationInfo(uk = g.pronunciation?.uk, us = g.pronunciation?.us),
          persianMeaning = g.meaningFa.ifBlank { g.senses.firstOrNull()?.meaningFa.orEmpty() }.trim(),
          englishDefinition = g.definitionEn.ifBlank { g.senses.firstOrNull()?.definitionEn.orEmpty() }.trim(),
          exampleSentence = g.senses.firstOrNull()?.examples?.firstOrNull()?.text,
        )
      }

    val allOccurrences =
      buildList {
        dto.glossary.forEach { g ->
          g.occurrences.forEach { occ ->
            add(
              WordOccurrence(
                packageId = dto.id,
                sentenceId = occ.sentenceId,
                glossaryEntryId = g.id,
                surfaceForm = occ.surface,
                startOffset = occ.start,
                endOffset = occ.end,
              )
            )
          }
        }
        dto.wordOccurrences.forEach { occ ->
          add(
            WordOccurrence(
              packageId = dto.id,
              sentenceId = occ.sentenceId,
              glossaryEntryId = occ.glossaryEntryId,
              surfaceForm = occ.surfaceForm,
              startOffset = occ.startOffset,
              endOffset = occ.endOffset,
            )
          )
        }
      }

    val sentences =
      dto.sentences.mapIndexed { idx, s ->
        Sentence(
          id = s.id,
          packageId = dto.id,
          sourceSentenceId = s.id,
          orderIndex = s.orderIndex ?: idx,
          speakerId = s.speakerId,
          englishText = s.text,
          persianTranslation = s.translationFa,
          phoneticHint = s.phoneticHint,
          grammarNoteFa = s.grammarNoteFa,
        )
      }

    val tokenStats = inspectVocabularyCoverageAndDeterminism(sentences, allGlossaryEntries, allOccurrences)
    if (tokenStats.uncoveredTokens.isNotEmpty()) {
      val sample = tokenStats.uncoveredTokens.take(12).joinToString(", ")
      missingVocabWarnings +=
        IntegrityIssue(
          severity = IntegritySeverity.MISSING_VOCAB,
          fieldPath = "glossary",
          messageFa =
            "${tokenStats.uncoveredTokens.size} واژه متمایز در متن هنوز مدخل واژه‌نامه ندارند: $sample",
          messageEn = "${tokenStats.uncoveredTokens.size} distinct meaningful tokens lack glossary entries: $sample",
        )
    }

    linguisticWarnings += tokenStats.determinismWarnings

    // Linguistic quality checks on glossary entries
    dto.glossary.forEachIndexed { index, entry ->
      if (entry.lemma.isBlank()) {
        linguisticWarnings +=
          IntegrityIssue(
            severity = IntegritySeverity.LINGUISTIC_QUALITY,
            fieldPath = "glossary[$index].lemma",
            messageFa = "واژه «${entry.term}» فیلد lemma ندارد.",
            messageEn = "Glossary entry '${entry.term}' is missing explicit lemma.",
          )
      } else if (!entry.term.contains(' ')) {
        val candidates = MorphologyAnalyzer.resolveLemmas(entry.term)
        val normalizedLemma = entry.lemma.trim().lowercase()
        val exactMatch = entry.term.trim().lowercase() == normalizedLemma
        val candidateMatch = candidates.any { it.lemma.equals(normalizedLemma, ignoreCase = true) }
        if (!exactMatch && !candidateMatch) {
          linguisticWarnings +=
            IntegrityIssue(
              severity = IntegritySeverity.LINGUISTIC_QUALITY,
              fieldPath = "glossary[$index].lemma",
              messageFa = "ریشه ثبت‌شده «${entry.lemma}» برای واژه «${entry.term}» با تحلیلگر صرفی تطابق ندارد.",
              messageEn = "Lemma '${entry.lemma}' for '${entry.term}' differs from morphology candidates.",
            )
        }
      }

      if (entry.pos.isBlank()) {
        linguisticWarnings +=
          IntegrityIssue(
            severity = IntegritySeverity.LINGUISTIC_QUALITY,
            fieldPath = "glossary[$index].pos",
            messageFa = "نقش دستوری (pos) برای «${entry.term}» مشخص نشده است.",
            messageEn = "Missing part of speech (pos) for '${entry.term}'.",
          )
      }

      if (entry.pronunciation == null || (entry.pronunciation.uk.isNullOrBlank() && entry.pronunciation.us.isNullOrBlank())) {
        linguisticWarnings +=
          IntegrityIssue(
            severity = IntegritySeverity.LINGUISTIC_QUALITY,
            fieldPath = "glossary[$index].pronunciation",
            messageFa = "تلفظ آوایی (IPA) برای «${entry.term}» ثبت نشده است.",
            messageEn = "Missing IPA pronunciation for '${entry.term}'.",
          )
      }

      val hasDefinition = entry.definitionEn.isNotBlank() || entry.senses.any { it.definitionEn.isNotBlank() }
      if (!hasDefinition) {
        linguisticWarnings +=
          IntegrityIssue(
            severity = IntegritySeverity.LINGUISTIC_QUALITY,
            fieldPath = "glossary[$index].definitionEn",
            messageFa = "تعریف انگلیسی برای «${entry.term}» ثبت نشده است.",
            messageEn = "Missing English definition for '${entry.term}'.",
          )
      }
    }

    if (dto.metadata.sentenceCount > 0 && dto.metadata.sentenceCount != dto.sentences.size) {
      linguisticWarnings +=
        IntegrityIssue(
          severity = IntegritySeverity.LINGUISTIC_QUALITY,
          fieldPath = "metadata.sentenceCount",
          messageFa = "تعداد جملات در metadata (${dto.metadata.sentenceCount}) با تعداد واقعی (${dto.sentences.size}) متفاوت است.",
          messageEn = "metadata.sentenceCount (${dto.metadata.sentenceCount}) != actual (${dto.sentences.size}).",
        )
    }
    if (dto.metadata.glossaryCount > 0 && dto.metadata.glossaryCount != dto.glossary.size) {
      linguisticWarnings +=
        IntegrityIssue(
          severity = IntegritySeverity.LINGUISTIC_QUALITY,
          fieldPath = "metadata.glossaryCount",
          messageFa = "تعداد مدخل‌های metadata (${dto.metadata.glossaryCount}) با تعداد واقعی (${dto.glossary.size}) متفاوت است.",
          messageEn = "metadata.glossaryCount (${dto.metadata.glossaryCount}) != actual (${dto.glossary.size}).",
        )
    }

    return ContentIntegrityReport(
      packageId = dto.id.ifBlank { "unknown" },
      title = dto.title.ifBlank { dto.id },
      version = dto.version,
      sentenceCount = sentenceTexts.size,
      glossaryCount = dto.glossary.size,
      questionCount = dto.questions.size,
      totalMeaningfulTokens = tokenStats.totalTokens,
      coveredTokens = tokenStats.coveredTokens,
      uncoveredTokens = tokenStats.uncoveredTokens,
      fatalErrors = fatalErrors,
      missingVocabularyWarnings = missingVocabWarnings,
      linguisticWarnings = linguisticWarnings,
    )
  }

  fun analyzeBundle(bundle: ContentPackageBundle): ContentIntegrityReport {
    val tokenStats =
      inspectVocabularyCoverageAndDeterminism(
        sentences = bundle.sentences,
        glossaryEntries = bundle.glossaryEntries,
        wordOccurrences = bundle.wordOccurrences,
      )
    val missingVocabWarnings = mutableListOf<IntegrityIssue>()
    val linguisticWarnings = mutableListOf<IntegrityIssue>()

    if (tokenStats.uncoveredTokens.isNotEmpty()) {
      val sample = tokenStats.uncoveredTokens.take(12).joinToString(", ")
      missingVocabWarnings +=
        IntegrityIssue(
          severity = IntegritySeverity.MISSING_VOCAB,
          fieldPath = "glossary",
          messageFa = "${tokenStats.uncoveredTokens.size} واژه متمایز در متن پوشش واژه‌نامه ندارند: $sample",
          messageEn = "${tokenStats.uncoveredTokens.size} distinct meaningful tokens lack glossary entries: $sample",
        )
    }
    linguisticWarnings += tokenStats.determinismWarnings

    bundle.glossaryEntries.forEachIndexed { index, entry ->
      if (entry.pronunciation.primary().isNullOrBlank()) {
        linguisticWarnings +=
          IntegrityIssue(
            severity = IntegritySeverity.LINGUISTIC_QUALITY,
            fieldPath = "glossary[$index].pronunciation",
            messageFa = "تلفظ آوایی برای «${entry.term}» ثبت نشده است.",
            messageEn = "Missing IPA pronunciation for '${entry.term}'.",
          )
      }
      if (entry.englishDefinition.isBlank() && entry.senses.all { it.definitionEn.isBlank() }) {
        linguisticWarnings +=
          IntegrityIssue(
            severity = IntegritySeverity.LINGUISTIC_QUALITY,
            fieldPath = "glossary[$index].definitionEn",
            messageFa = "تعریف انگلیسی برای «${entry.term}» ثبت نشده است.",
            messageEn = "Missing English definition for '${entry.term}'.",
          )
      }
    }

    return ContentIntegrityReport(
      packageId = bundle.contentPackage.packageId,
      title = bundle.contentPackage.title,
      version = bundle.contentPackage.contentVersion,
      sentenceCount = bundle.sentences.size,
      glossaryCount = bundle.glossaryEntries.size,
      questionCount = bundle.questions.size,
      totalMeaningfulTokens = tokenStats.totalTokens,
      coveredTokens = tokenStats.coveredTokens,
      uncoveredTokens = tokenStats.uncoveredTokens,
      fatalErrors = emptyList(),
      missingVocabularyWarnings = missingVocabWarnings,
      linguisticWarnings = linguisticWarnings,
    )
  }

  private data class TokenCoverageStats(
    val totalTokens: Int,
    val coveredTokens: Int,
    val uncoveredTokens: List<String>,
    val determinismWarnings: List<IntegrityIssue>,
  )

  private fun inspectVocabularyCoverageAndDeterminism(
    sentences: List<Sentence>,
    glossaryEntries: List<GlossaryEntry>,
    wordOccurrences: List<WordOccurrence>,
  ): TokenCoverageStats {
    val occurrencesBySentence = wordOccurrences.groupBy { it.sentenceId }
    val multiwordEntries = glossaryEntries.filter { it.isMultiword }
    val determinismWarnings = mutableListOf<IntegrityIssue>()

    // Check deterministic phrase matching (distinct normalized multiword forms shouldn't collide across entries)
    val seenPhraseForms = mutableMapOf<String, String>()
    multiwordEntries.forEach { entry ->
      val forms = (listOf(entry.normalizedTerm) + entry.forms).map { it.trim().lowercase() }.filter { it.contains(' ') }.distinct()
      forms.forEach { form ->
        val existingEntryId = seenPhraseForms[form]
        if (existingEntryId != null && existingEntryId != entry.id) {
          determinismWarnings +=
            IntegrityIssue(
              severity = IntegritySeverity.LINGUISTIC_QUALITY,
              fieldPath = "glossary",
              messageFa = "عبارت «$form» بین دو مدخل متفاوت مشترک است و ممکن است تطابق غیرقطعی ایجاد کند.",
              messageEn = "Multiword form '$form' collides between entries '$existingEntryId' and '${entry.id}'.",
            )
        } else {
          seenPhraseForms[form] = entry.id
        }
      }
    }

    var totalTokens = 0
    var coveredTokens = 0
    val uncoveredDistinct = linkedSetOf<String>()

    sentences.forEach { sentence ->
      val tokens = resolutionEngine.tokenizeSentence(sentence.englishText)
      val sentenceOccurrences = occurrencesBySentence[sentence.id].orEmpty()
      tokens.forEach { token ->
        // Numbers alone are non-lexical tokens
        if (token.normalized.all { it.isDigit() }) return@forEach
        totalTokens++
        val resolved =
          resolutionEngine.resolveInSentence(
            sentence = sentence,
            tappedOffset = token.startOffset,
            packageGlossary = glossaryEntries,
            sentenceOccurrences = sentenceOccurrences,
            fallbackGlobalGlossary = emptyList(),
          )
        if (resolved?.primaryMatch != null) {
          coveredTokens++
        } else {
          uncoveredDistinct += token.surface
        }
      }
    }

    return TokenCoverageStats(
      totalTokens = totalTokens,
      coveredTokens = coveredTokens,
      uncoveredTokens = uncoveredDistinct.toList(),
      determinismWarnings = determinismWarnings,
    )
  }
}
