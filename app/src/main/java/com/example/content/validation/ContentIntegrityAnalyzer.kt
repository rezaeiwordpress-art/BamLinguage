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
import com.example.domain.vocabulary.SentenceToken
import com.example.domain.vocabulary.VocabularyResolutionEngine

enum class IntegritySeverity(val persianLabel: String) {
  FATAL("خطای ساختاری بحرانی"),
  MISSING_VOCAB("هشدار واژگان و عبارات پوشش‌داده‌نشده"),
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
  val missingWords: List<String> = uncoveredTokens,
  val missingPhrases: List<String> = emptyList(),
  val detectedPhraseCount: Int = 0,
  val coveredPhraseCount: Int = 0,
  val fatalErrors: List<ImportValidationError>,
  val missingVocabularyWarnings: List<IntegrityIssue>,
  val linguisticWarnings: List<IntegrityIssue>,
) {
  val isValidForImport: Boolean
    get() = fatalErrors.isEmpty()

  val isCoverageVerified: Boolean
    get() =
      fatalErrors.isEmpty() &&
        totalMeaningfulTokens > 0 &&
        uncoveredTokens.isEmpty() &&
        missingWords.isEmpty() &&
        missingPhrases.isEmpty() &&
        missingVocabularyWarnings.isEmpty()

  val coveragePercent: Int
    get() {
      val totalUnits = totalMeaningfulTokens + detectedPhraseCount
      if (totalUnits <= 0 || glossaryCount <= 0) return 0
      val coveredUnits = coveredTokens + coveredPhraseCount
      val rawPercent = ((coveredUnits * 100.0) / totalUnits).toInt().coerceIn(0, 100)
      return if (isCoverageVerified) 100 else rawPercent.coerceAtMost(99)
    }

  val hasCompleteDictionaryCoverage: Boolean
    get() = isCoverageVerified
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

    val sentenceTexts = dto.sentences.associate { it.id.trim() to it.text }
    val allGlossaryEntries =
      dto.glossary.map { g ->
        GlossaryEntry(
          id = g.id.trim(),
          packageId = dto.id.trim(),
          sourceGlossaryId = g.id.trim(),
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
          sentenceIds = g.sentenceIds.map { it.trim() }.filter { it.isNotEmpty() },
        )
      }
    val validGlossaryIds = allGlossaryEntries.map { it.id }.toSet()

    // Only treat verified occurrences (valid sentence, valid entry, exact UTF-16 slice) as valid coverage anchors
    val verifiedOccurrences =
      buildList {
        dto.glossary.forEach { g ->
          val cleanEntryId = g.id.trim()
          g.occurrences.forEach { occ ->
            val cleanSentenceId = occ.sentenceId.trim()
            if (isVerifiedOccurrence(cleanSentenceId, cleanEntryId, occ.surface, occ.start, occ.end, sentenceTexts, validGlossaryIds)) {
              add(
                WordOccurrence(
                  packageId = dto.id.trim(),
                  sentenceId = cleanSentenceId,
                  glossaryEntryId = cleanEntryId,
                  surfaceForm = occ.surface,
                  startOffset = occ.start,
                  endOffset = occ.end,
                )
              )
            }
          }
        }
        dto.wordOccurrences.forEach { occ ->
          val cleanSentenceId = occ.sentenceId.trim()
          val cleanEntryId = occ.glossaryEntryId.trim()
          if (isVerifiedOccurrence(cleanSentenceId, cleanEntryId, occ.surfaceForm, occ.startOffset, occ.endOffset, sentenceTexts, validGlossaryIds)) {
            add(
              WordOccurrence(
                packageId = dto.id.trim(),
                sentenceId = cleanSentenceId,
                glossaryEntryId = cleanEntryId,
                surfaceForm = occ.surfaceForm,
                startOffset = occ.startOffset,
                endOffset = occ.endOffset,
              )
            )
          }
        }
      }

    val sentences =
      dto.sentences.mapIndexed { idx, s ->
        val cleanId = s.id.trim()
        Sentence(
          id = cleanId,
          packageId = dto.id.trim(),
          sourceSentenceId = cleanId,
          orderIndex = s.orderIndex ?: idx,
          speakerId = s.speakerId?.trim()?.takeIf { it.isNotEmpty() },
          englishText = s.text,
          persianTranslation = s.translationFa,
          phoneticHint = s.phoneticHint,
          grammarNoteFa = s.grammarNoteFa,
        )
      }

    val tokenStats =
      inspectVocabularyCoverageAndDeterminism(
        sentences = sentences,
        glossaryEntries = allGlossaryEntries,
        wordOccurrences = verifiedOccurrences,
      )
    buildMissingVocabWarnings(tokenStats, missingVocabWarnings)
    linguisticWarnings += tokenStats.determinismWarnings

    dto.glossary.forEachIndexed { index, entry ->
      if (entry.lemma.isBlank()) {
        linguisticWarnings +=
          IntegrityIssue(
            severity = IntegritySeverity.LINGUISTIC_QUALITY,
            fieldPath = "glossary[$index].lemma",
            messageFa = "واژه «${entry.term}» فیلد lemma ندارد.",
            messageEn = "Glossary entry '${entry.term}' is missing explicit lemma.",
          )
      } else if (!entry.term.trim().contains(' ')) {
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
      missingWords = tokenStats.uncoveredTokens,
      missingPhrases = tokenStats.missingPhrases,
      detectedPhraseCount = tokenStats.detectedPhrases,
      coveredPhraseCount = tokenStats.coveredPhrases,
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

    buildMissingVocabWarnings(tokenStats, missingVocabWarnings)
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
      missingWords = tokenStats.uncoveredTokens,
      missingPhrases = tokenStats.missingPhrases,
      detectedPhraseCount = tokenStats.detectedPhrases,
      coveredPhraseCount = tokenStats.coveredPhrases,
      fatalErrors = emptyList(),
      missingVocabularyWarnings = missingVocabWarnings,
      linguisticWarnings = linguisticWarnings,
    )
  }

  private fun buildMissingVocabWarnings(
    tokenStats: TokenCoverageStats,
    target: MutableList<IntegrityIssue>,
  ) {
    if (tokenStats.uncoveredTokens.isNotEmpty()) {
      val sample = tokenStats.uncoveredTokens.take(12).joinToString(", ")
      target +=
        IntegrityIssue(
          severity = IntegritySeverity.MISSING_VOCAB,
          fieldPath = "glossary",
          messageFa =
            "${tokenStats.uncoveredTokens.size} واژه متمایز در متن هنوز مدخل واژه‌نامه ندارند: $sample",
          messageEn = "${tokenStats.uncoveredTokens.size} distinct meaningful tokens lack glossary entries: $sample",
        )
    }
    if (tokenStats.missingPhrases.isNotEmpty()) {
      val sample = tokenStats.missingPhrases.take(10).joinToString(", ")
      target +=
        IntegrityIssue(
          severity = IntegritySeverity.MISSING_VOCAB,
          fieldPath = "glossary.phrases",
          messageFa =
            "${tokenStats.missingPhrases.size} عبارت چندواژه‌ای / فعل عبارتی در متن مدخل عبارتی در واژه‌نامه ندارد: $sample",
          messageEn = "${tokenStats.missingPhrases.size} multiword expressions in text lack a multiword glossary entry: $sample",
        )
    }
    target += tokenStats.phraseReferenceWarnings
  }

  private fun isVerifiedOccurrence(
    sentenceId: String,
    entryId: String,
    surface: String,
    start: Int,
    end: Int,
    sentenceTexts: Map<String, String>,
    validGlossaryIds: Set<String>,
  ): Boolean {
    if (entryId !in validGlossaryIds) return false
    val text = sentenceTexts[sentenceId] ?: return false
    if (surface.isBlank() || surface != surface.trim()) return false
    if (start < 0 || end <= start || end > text.length) return false
    if (text.substring(start, end) != surface) return false
    val prevChar = if (start > 0) text[start - 1] else null
    val nextChar = if (end < text.length) text[end] else null
    val startsInsideWord = prevChar != null && prevChar.isLetterOrDigit() && text[start].isLetterOrDigit()
    val endsInsideWord = nextChar != null && text[end - 1].isLetterOrDigit() && nextChar.isLetterOrDigit()
    return !startsInsideWord && !endsInsideWord
  }

  private data class TokenCoverageStats(
    val totalTokens: Int,
    val coveredTokens: Int,
    val uncoveredTokens: List<String>,
    val detectedPhrases: Int,
    val coveredPhrases: Int,
    val missingPhrases: List<String>,
    val phraseReferenceWarnings: List<IntegrityIssue>,
    val determinismWarnings: List<IntegrityIssue>,
  )

  private fun inspectVocabularyCoverageAndDeterminism(
    sentences: List<Sentence>,
    glossaryEntries: List<GlossaryEntry>,
    wordOccurrences: List<WordOccurrence>,
  ): TokenCoverageStats {
    val occurrencesBySentence = wordOccurrences.groupBy { it.sentenceId }
    val multiwordEntries = glossaryEntries.filter { it.isMultiword }
    val sentenceById = sentences.associateBy { it.id }
    val determinismWarnings = mutableListOf<IntegrityIssue>()
    val phraseReferenceWarnings = mutableListOf<IntegrityIssue>()

    // Check deterministic phrase matching (distinct normalized multiword forms shouldn't collide across entries)
    val seenPhraseForms = mutableMapOf<String, String>()
    multiwordEntries.forEach { entry ->
      val forms =
        (listOf(entry.normalizedTerm) + entry.forms)
          .map { it.trim().lowercase() }
          .filter { it.contains(' ') }
          .distinct()
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

      // Check whether declared sentenceIds on multiword entries actually contain/resolve that multiword entry
      if (entry.sentenceIds.isNotEmpty()) {
        entry.sentenceIds.forEach { refSentenceId ->
          val sentence = sentenceById[refSentenceId] ?: return@forEach
          val sentenceTokens = resolutionEngine.tokenizeSentence(sentence.englishText)
          val sentenceOccs = occurrencesBySentence[sentence.id].orEmpty()
          val resolvesInSentence =
            sentenceOccs.any { it.glossaryEntryId == entry.id } ||
              sentenceTokens.any { tok ->
                val resolved =
                  resolutionEngine.resolveInSentence(
                    sentence = sentence,
                    tappedOffset = tok.startOffset,
                    packageGlossary = listOf(entry),
                    sentenceOccurrences = sentenceOccs,
                    fallbackGlobalGlossary = emptyList(),
                  )
                resolved?.phraseMatches?.any { it.entry.id == entry.id } == true
              }
          if (!resolvesInSentence) {
            phraseReferenceWarnings +=
              IntegrityIssue(
                severity = IntegritySeverity.MISSING_VOCAB,
                fieldPath = "glossary.sentenceIds",
                messageFa = "عبارت «${entry.term}» به جمله '$refSentenceId' ارجاع داده اما در متن آن جمله تطابق نیافت.",
                messageEn = "Multiword entry '${entry.term}' references sentence '$refSentenceId' without a matching span in text.",
              )
          }
        }
      }
    }

    var totalTokens = 0
    var coveredTokens = 0
    val uncoveredDistinct = linkedSetOf<String>()

    var detectedPhrases = 0
    var coveredPhrases = 0
    val missingPhrasesDistinct = linkedSetOf<String>()

    sentences.forEach { sentence ->
      val tokens = resolutionEngine.tokenizeSentence(sentence.englishText)
      val sentenceOccurrences = occurrencesBySentence[sentence.id].orEmpty()
      val matchedCatalogSpans = detectCatalogPhrasesInSentence(sentence.englishText, tokens)

      matchedCatalogSpans.forEach { span ->
        detectedPhrases++
        val resolvedAtPhrase =
          resolutionEngine.resolveInSentence(
            sentence = sentence,
            tappedOffset = span.startOffset,
            packageGlossary = glossaryEntries,
            sentenceOccurrences = sentenceOccurrences,
            fallbackGlobalGlossary = emptyList(),
          )
        val hasMultiwordMatch =
          resolvedAtPhrase?.phraseMatches?.isNotEmpty() == true ||
            resolvedAtPhrase?.primaryMatch?.entry?.isMultiword == true
        if (hasMultiwordMatch) {
          coveredPhrases++
        } else {
          missingPhrasesDistinct += span.surface
        }
      }

      tokens.forEach { token ->
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
      detectedPhrases = detectedPhrases,
      coveredPhrases = coveredPhrases,
      missingPhrases = missingPhrasesDistinct.toList(),
      phraseReferenceWarnings = phraseReferenceWarnings,
      determinismWarnings = determinismWarnings,
    )
  }

  private data class DetectedPhraseSpan(
    val canonicalPhrase: String,
    val surface: String,
    val startOffset: Int,
    val endOffset: Int,
  )

  private fun detectCatalogPhrasesInSentence(
    sentenceText: String,
    tokens: List<SentenceToken>,
  ): List<DetectedPhraseSpan> {
    if (tokens.size < 2) return emptyList()
    val found = mutableListOf<DetectedPhraseSpan>()
    var i = 0
    while (i < tokens.size) {
      var matchedSpan: DetectedPhraseSpan? = null
      for (phraseTokens in CATALOG_PHRASE_TOKENS) {
        val len = phraseTokens.size
        if (i + len > tokens.size) continue
        var allMatch = true
        for (offset in 0 until len) {
          val tokenNormalized = tokens[i + offset].normalized
          val target = phraseTokens[offset]
          val exact = tokenNormalized == target
          val lemmaMatch =
            offset == 0 && MorphologyAnalyzer.resolveLemmas(tokenNormalized).any { it.lemma == target }
          if (!exact && !lemmaMatch) {
            allMatch = false
            break
          }
        }
        if (allMatch) {
          val start = tokens[i].startOffset
          val end = tokens[i + len - 1].endOffset
          matchedSpan =
            DetectedPhraseSpan(
              canonicalPhrase = phraseTokens.joinToString(" "),
              surface = sentenceText.substring(start, end),
              startOffset = start,
              endOffset = end,
            )
          i += len
          break
        }
      }
      if (matchedSpan != null) {
        found += matchedSpan
      } else {
        i++
      }
    }
    return found
  }

  companion object {
    private val CATALOG_PHRASE_TOKENS: List<List<String>> =
      listOf(
        "look forward to",
        "come up with",
        "get along with",
        "run out of",
        "take part in",
        "give up",
        "give in",
        "look after",
        "set off",
        "find out",
        "figure out",
        "pick up",
        "turn on",
        "turn off",
        "turn out",
        "put off",
        "wake up",
        "get up",
        "sit down",
        "stand up",
        "come back",
        "go back",
        "carry on",
        "carry out",
        "bring up",
        "break down",
        "hang out",
        "come across",
        "calm down",
        "hurry up",
        "slow down",
        "check in",
        "check out",
        "get on",
        "get off",
        "make sure",
        "in fact",
        "for example",
        "at least",
        "so far",
        "of course",
        "right now",
        "as well",
      )
        .map { it.split(' ') }
        .sortedByDescending { it.size }
  }
}
