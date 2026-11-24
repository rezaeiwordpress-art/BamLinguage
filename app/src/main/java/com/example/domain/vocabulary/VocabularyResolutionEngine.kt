package com.example.domain.vocabulary

import com.example.domain.model.GlossaryEntry
import com.example.domain.model.GlossarySense
import com.example.domain.model.Sentence
import com.example.domain.model.WordOccurrence

data class SentenceToken(
  val surface: String,
  val normalized: String,
  val startOffset: Int,
  val endOffset: Int,
)

data class ResolvedGlossaryMatch(
  val entry: GlossaryEntry,
  val recommendedSense: GlossarySense?,
  val matchReason: String,
  val score: Int,
)

data class ContextualResolutionResult(
  val tappedToken: SentenceToken,
  val matchedSurface: String,
  val matchedStartOffset: Int,
  val matchedEndOffset: Int,
  val sentence: Sentence,
  val primaryMatch: ResolvedGlossaryMatch?,
  val alternativeMatches: List<ResolvedGlossaryMatch>,
  val phraseMatches: List<ResolvedGlossaryMatch>,
  val predictedPos: String?,
) {
  val isFound: Boolean
    get() = primaryMatch != null

  val allSenses: List<GlossarySense>
    get() =
      buildList {
        primaryMatch?.recommendedSense?.let { add(it) }
        primaryMatch?.entry?.senses?.forEach { if (it !in this) add(it) }
        alternativeMatches.forEach { alt ->
          alt.entry.senses.forEach { if (it !in this) add(it) }
        }
      }
}

/**
 * Resolves tapped words and multiword phrases in sentence context using:
 * 1. Exact UTF-16 occurrence spans (prioritizing multiword expressions)
 * 2. Contiguous multiword glossary phrase scan in sentence
 * 3. Exact term match
 * 4. Inflected forms match
 * 5. Irregular & morphological lemma resolution ([MorphologyAnalyzer])
 * 6. Sentence-context grammatical POS disambiguation
 */
class VocabularyResolutionEngine {

  private val determiners = setOf("a", "an", "the", "my", "your", "his", "her", "our", "their", "this", "that", "these", "those", "some", "any", "no", "every", "each")
  private val subjectPronouns = setOf("i", "you", "he", "she", "it", "we", "they")
  private val auxiliariesAndModals = setOf("will", "would", "can", "could", "should", "must", "may", "might", "shall", "did", "do", "does", "have", "has", "had", "am", "is", "are", "was", "were")
  private val linkingOrActionVerbs = setOf("feel", "feels", "felt", "look", "looks", "looked", "seem", "seems", "work", "works", "worked", "speak", "speaks", "spoke", "do", "did", "does", "run", "runs", "ran", "perform", "performed", "study", "studied")
  private val prepositions = setOf("in", "on", "at", "to", "for", "of", "with", "by", "from", "about", "into", "through", "near", "along", "after", "before", "because")

  fun tokenizeSentence(text: String): List<SentenceToken> {
    val regex = Regex("[A-Za-z0-9]+(?:['’-][A-Za-z0-9]+)*")
    return regex.findAll(text).map { match ->
      SentenceToken(
        surface = match.value,
        normalized = MorphologyAnalyzer.normalizeToken(match.value),
        startOffset = match.range.first,
        endOffset = match.range.last + 1,
      )
    }.toList()
  }

  fun resolveInSentence(
    sentence: Sentence,
    tappedOffset: Int,
    packageGlossary: List<GlossaryEntry>,
    sentenceOccurrences: List<WordOccurrence>,
    fallbackGlobalGlossary: List<GlossaryEntry> = emptyList(),
  ): ContextualResolutionResult? {
    val tokens = tokenizeSentence(sentence.englishText)
    if (tokens.isEmpty()) return null

    val tokenIndex =
      tokens.indexOfFirst { tappedOffset in it.startOffset until it.endOffset }
        .takeIf { it >= 0 }
        ?: tokens.indexOfFirst { tappedOffset <= it.endOffset }.takeIf { it >= 0 }
        ?: (tokens.size - 1)

    val tappedToken = tokens[tokenIndex]
    val predictedPos = predictContextualPos(tokens, tokenIndex, sentence.englishText)
    val entryById = packageGlossary.associateBy { it.id }

    val scoredMatches = mutableMapOf<String, ResolvedGlossaryMatch>()
    val phraseMatches = mutableListOf<ResolvedGlossaryMatch>()
    var matchedSpanStart = tappedToken.startOffset
    var matchedSpanEnd = tappedToken.endOffset
    var matchedSurface = tappedToken.surface

    // 1. Check explicit WordOccurrences overlapping the tapped token (multiword prioritized)
    val overlappingOccurrences =
      sentenceOccurrences.filter { occ ->
        occ.startOffset < tappedToken.endOffset && occ.endOffset > tappedToken.startOffset
      }.sortedByDescending { it.endOffset - it.startOffset }

    overlappingOccurrences.forEach { occ ->
      val entry = entryById[occ.glossaryEntryId] ?: return@forEach
      val isPhrase = occ.surfaceForm.contains(' ') || entry.isMultiword
      val posBonus = posCompatibilityScore(entry.partOfSpeech, predictedPos)
      val baseScore = if (isPhrase) 1_000 else 800
      val match =
        ResolvedGlossaryMatch(
          entry = entry,
          recommendedSense = selectBestSense(entry, predictedPos, sentence.englishText),
          matchReason = if (isPhrase) "عبارت چندواژه‌ای در متن" else "تطابق دقیق در جمله",
          score = baseScore + posBonus,
        )
      scoredMatches[entry.id] = match
      if (isPhrase) {
        phraseMatches += match
      }
    }

    // 2. Deterministic multiword phrase scan across contiguous tokens covering tokenIndex
    val allEntries = (packageGlossary + fallbackGlobalGlossary).distinctBy { it.id }
    val multiwordEntries = allEntries.filter { it.isMultiword }
    for (entry in multiwordEntries) {
      val candidatePhrases =
        (listOf(entry.term, entry.normalizedTerm) + entry.forms)
          .map { it.trim().lowercase() }
          .filter { it.contains(' ') }
          .distinct()
          .sortedByDescending { it.length }

      for (phrase in candidatePhrases) {
        val phraseTokens = phrase.split(Regex("\\s+"))
        val span = findPhraseSpanCoveringToken(tokens, tokenIndex, phraseTokens)
        if (span != null) {
          val score = 950 + phraseTokens.size * 20 + posCompatibilityScore(entry.partOfSpeech, predictedPos)
          val existing = scoredMatches[entry.id]
          if (existing == null || score > existing.score) {
            val match =
              ResolvedGlossaryMatch(
                entry = entry,
                recommendedSense = selectBestSense(entry, predictedPos, sentence.englishText),
                matchReason = "تطابق عبارت چندواژه‌ای (${phraseTokens.size} واژه)",
                score = score,
              )
            scoredMatches[entry.id] = match
            if (phraseMatches.none { it.entry.id == entry.id }) {
              phraseMatches += match
            }
          }
          if (span.second - span.first > matchedSpanEnd - matchedSpanStart) {
            matchedSpanStart = span.first
            matchedSpanEnd = span.second
            matchedSurface = sentence.englishText.substring(span.first, span.second)
          }
        }
      }
    }

    // 3. Exact term, inflected forms, and morphology/lemma resolution
    val lemmaCandidates = MorphologyAnalyzer.resolveLemmas(tappedToken.normalized)

    for (entry in allEntries) {
      if (entry.isMultiword) continue
      val packageBonus = if (entry.packageId == sentence.packageId) 100 else 0
      val posBonus = posCompatibilityScore(entry.partOfSpeech, predictedPos)

      val (baseScore, reason) =
        when {
          entry.normalizedTerm == tappedToken.normalized ||
            entry.term.equals(tappedToken.normalized, ignoreCase = true) ->
            700 to "تطابق دقیق واژه"
          entry.forms.any { it.equals(tappedToken.normalized, ignoreCase = true) } ->
            620 to "تطابق شکل صرف‌شده (Inflected form)"
          else -> {
            val matchedLemma =
              lemmaCandidates.firstOrNull { cand ->
                cand.lemma.equals(entry.lemma, ignoreCase = true) ||
                  cand.lemma.equals(entry.normalizedTerm, ignoreCase = true)
              }
            if (matchedLemma != null) {
              val lemmaHintBonus =
                if (matchedLemma.hintedPos != null &&
                  matchedLemma.hintedPos.equals(entry.partOfSpeech, ignoreCase = true)
                ) 40 else 0
              (520 + lemmaHintBonus) to "تشخیص ریشه واژه (${matchedLemma.lemma})"
            } else {
              0 to ""
            }
          }
        }

      if (baseScore > 0) {
        val totalScore = baseScore + packageBonus + posBonus
        val existing = scoredMatches[entry.id]
        if (existing == null || totalScore > existing.score) {
          scoredMatches[entry.id] =
            ResolvedGlossaryMatch(
              entry = entry,
              recommendedSense = selectBestSense(entry, predictedPos, sentence.englishText),
              matchReason = reason,
              score = totalScore,
            )
        }
      }
    }

    val ranked = scoredMatches.values.sortedByDescending { it.score }
    val primary = ranked.firstOrNull()
    val alternatives = if (ranked.size > 1) ranked.drop(1) else emptyList()

    if (overlappingOccurrences.isNotEmpty() && overlappingOccurrences.first().surfaceForm.contains(' ')) {
      val topOcc = overlappingOccurrences.first()
      matchedSpanStart = topOcc.startOffset
      matchedSpanEnd = topOcc.endOffset
      matchedSurface = topOcc.surfaceForm
    }

    return ContextualResolutionResult(
      tappedToken = tappedToken,
      matchedSurface = matchedSurface,
      matchedStartOffset = matchedSpanStart,
      matchedEndOffset = matchedSpanEnd,
      sentence = sentence,
      primaryMatch = primary,
      alternativeMatches = alternatives,
      phraseMatches = phraseMatches.sortedByDescending { it.score },
      predictedPos = predictedPos,
    )
  }

  private fun findPhraseSpanCoveringToken(
    tokens: List<SentenceToken>,
    tokenIndex: Int,
    phraseTokens: List<String>,
  ): Pair<Int, Int>? {
    if (phraseTokens.size < 2 || phraseTokens.size > tokens.size) return null
    val minStart = maxOf(0, tokenIndex - phraseTokens.size + 1)
    val maxStart = minOf(tokenIndex, tokens.size - phraseTokens.size)
    for (startIdx in minStart..maxStart) {
      var allMatch = true
      for (offset in phraseTokens.indices) {
        val sentenceToken = tokens[startIdx + offset].normalized
        val targetToken = phraseTokens[offset]
        val matchesExact = sentenceToken == targetToken
        val matchesLemma =
          MorphologyAnalyzer.resolveLemmas(sentenceToken).any { it.lemma == targetToken }
        if (!matchesExact && !matchesLemma) {
          allMatch = false
          break
        }
      }
      if (allMatch) {
        return tokens[startIdx].startOffset to tokens[startIdx + phraseTokens.size - 1].endOffset
      }
    }
    return null
  }

  fun predictContextualPos(
    tokens: List<SentenceToken>,
    index: Int,
    rawSentence: String,
  ): String? {
    val prev = tokens.getOrNull(index - 1)?.normalized
    val next = tokens.getOrNull(index + 1)?.normalized
    val current = tokens[index].normalized

    if (prev == "to" || prev in auxiliariesAndModals) {
      return "verb"
    }
    if (prev in determiners) {
      return if (next != null && next !in prepositions && next !in auxiliariesAndModals) {
        "adjective"
      } else {
        "noun"
      }
    }
    if (current == "better" || current == "best" || current == "worse" || current == "worst") {
      if (prev in linkingOrActionVerbs) return "adverb"
      if (next != null && next !in prepositions && next !in auxiliariesAndModals) return "adjective"
    }
    if (prev in subjectPronouns) {
      return "verb"
    }
    if (index == 1 &&
      tokens[0].surface.firstOrNull()?.isUpperCase() == true &&
      prev !in determiners &&
      current !in auxiliariesAndModals &&
      (next in determiners || current.endsWith("ed") || MorphologyAnalyzer.resolveLemmas(current).any { it.hintedPos == "verb" })
    ) {
      return "verb"
    }
    // Capitalized non-initial token in sentence after subject slot or proper noun at index 0 followed by past verb
    val tokenSurface = tokens[index].surface
    if (index == 0 && tokenSurface.firstOrNull()?.isUpperCase() == true && next != null && next.endsWith("ed")) {
      return "noun"
    }
    if (rawSentence.isNotBlank() && prev in prepositions && next != null && next !in prepositions) {
      return "noun"
    }
    return null
  }

  private fun posCompatibilityScore(entryPos: String, predictedPos: String?): Int {
    if (predictedPos == null || entryPos.isBlank()) return 0
    val normalizedEntryPos = entryPos.trim().lowercase()
    return when {
      normalizedEntryPos == predictedPos -> 120
      normalizedEntryPos.contains(predictedPos) -> 90
      predictedPos == "noun" && normalizedEntryPos == "pronoun" -> 40
      else -> 0
    }
  }

  private fun selectBestSense(
    entry: GlossaryEntry,
    predictedPos: String?,
    sentenceText: String,
  ): GlossarySense? {
    if (entry.senses.isEmpty()) return null
    if (entry.senses.size == 1) return entry.senses.first()

    val lowerSentence = sentenceText.lowercase()
    return entry.senses.maxByOrNull { sense ->
      var score = 0
      val defLower = sense.definitionEn.lowercase()
      if (predictedPos != null && defLower.contains(predictedPos)) score += 20
      // Overlap between sentence content words and sense definition/examples
      val sentenceWords = lowerSentence.split(Regex("\\W+")).filter { it.length >= 4 }.toSet()
      val senseText =
        (defLower + " " + sense.examples.joinToString(" ") { it.text.lowercase() })
          .split(Regex("\\W+"))
          .filter { it.length >= 4 }
          .toSet()
      score += sentenceWords.intersect(senseText).size * 15
      score
    } ?: entry.senses.first()
  }
}
