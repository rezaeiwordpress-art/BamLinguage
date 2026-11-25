package com.example.domain.usecase

import com.example.core.common.StableIdGenerator
import com.example.domain.model.ContentPackageBundle
import com.example.domain.model.LearningProgress
import com.example.domain.model.ReaderBookmark
import com.example.domain.model.ReaderWordStatus
import com.example.domain.model.Sentence
import com.example.domain.model.SrsCard
import com.example.domain.model.SrsCardType
import com.example.domain.repository.ContentRepository
import com.example.domain.repository.LearningRepository
import com.example.domain.srs.SrsScheduler
import com.example.domain.vocabulary.ContextualResolutionResult
import com.example.domain.vocabulary.MorphologyAnalyzer
import com.example.domain.vocabulary.VocabularyResolutionEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class ReaderTokenHighlight(
  val startOffset: Int,
  val endOffset: Int,
  val surface: String,
  val status: ReaderWordStatus,
  val isPhrase: Boolean,
  val linkedCardId: String?,
)

data class ReaderPackageSnapshot(
  val bundle: ContentPackageBundle,
  val bookmarksBySentenceId: Map<String, ReaderBookmark>,
  val progress: LearningProgress?,
  val tokenHighlightsBySentenceId: Map<String, List<ReaderTokenHighlight>>,
  val sentenceSrsCardsBySentenceId: Map<String, SrsCard>,
  val allWordCards: List<SrsCard>,
)

class ResolveWordInSentenceUseCase(
  private val contentRepository: ContentRepository,
  private val engine: VocabularyResolutionEngine = VocabularyResolutionEngine(),
) {
  suspend operator fun invoke(
    sentence: Sentence,
    tappedOffset: Int,
    bundle: ContentPackageBundle,
  ): ContextualResolutionResult? {
    val sentenceOccurrences = bundle.wordOccurrences.filter { it.sentenceId == sentence.id }
    val globalGlossary = contentRepository.getAllGlossaryEntries()
    return engine.resolveInSentence(
      sentence = sentence,
      tappedOffset = tappedOffset,
      packageGlossary = bundle.glossaryEntries,
      sentenceOccurrences = sentenceOccurrences,
      fallbackGlobalGlossary = globalGlossary,
    )
  }
}

class ObserveReaderPackageUseCase(
  private val contentRepository: ContentRepository,
  private val learningRepository: LearningRepository,
  private val engine: VocabularyResolutionEngine = VocabularyResolutionEngine(),
) {
  operator fun invoke(
    packageId: String,
    nowProvider: () -> Long = { System.currentTimeMillis() },
  ): Flow<ReaderPackageSnapshot?> {
    return combine(
      contentRepository.observePackageBundle(packageId),
      learningRepository.observeAllSrsCards(),
      learningRepository.observeBookmarksForPackage(packageId),
      learningRepository.observeProgressForPackage(packageId),
    ) { bundle, allCards, bookmarks, progress ->
      if (bundle == null) return@combine null
      buildSnapshot(
        bundle = bundle,
        allCards = allCards,
        bookmarks = bookmarks,
        progress = progress,
        nowEpochMs = nowProvider(),
      )
    }
  }

  operator fun invoke(
    packageId: String,
    timeRefreshFlow: Flow<Long>,
  ): Flow<ReaderPackageSnapshot?> {
    return combine(
      contentRepository.observePackageBundle(packageId),
      learningRepository.observeAllSrsCards(),
      learningRepository.observeBookmarksForPackage(packageId),
      learningRepository.observeProgressForPackage(packageId),
      timeRefreshFlow,
    ) { bundle, allCards, bookmarks, progress, nowEpochMs ->
      if (bundle == null) return@combine null
      buildSnapshot(
        bundle = bundle,
        allCards = allCards,
        bookmarks = bookmarks,
        progress = progress,
        nowEpochMs = nowEpochMs,
      )
    }
  }

  private fun buildSnapshot(
    bundle: ContentPackageBundle,
    allCards: List<SrsCard>,
    bookmarks: List<ReaderBookmark>,
    progress: LearningProgress?,
    nowEpochMs: Long,
  ): ReaderPackageSnapshot {
    val wordCards = allCards.filter { it.cardType == SrsCardType.WORD }
    val sentenceCards = allCards.filter { it.cardType == SrsCardType.SENTENCE }
    val cardsByExactKey = wordCards.groupBy { it.toWordCardKey() }

    val occurrencesBySentenceId = bundle.wordOccurrences.groupBy { it.sentenceId }
    val glossaryIndex = engine.buildIndex(bundle.glossaryEntries)

    val highlightsBySentence =
      bundle.sentences.associate { sentence ->
        val sentenceOccurrences = occurrencesBySentenceId[sentence.id].orEmpty()
        val tokens = engine.tokenizeSentence(sentence.englishText)

        val tokenHighlights =
          tokens.map { token ->
            val matchingPhraseOcc =
              sentenceOccurrences
                .filter { occ ->
                  occ.startOffset < token.endOffset &&
                    occ.endOffset > token.startOffset &&
                    occ.surfaceForm.contains(' ')
                }
                .maxByOrNull { it.endOffset - it.startOffset }

            val resolution =
              engine.resolveInSentenceIndexed(
                sentence = sentence,
                tappedOffset = token.startOffset,
                index = glossaryIndex,
                sentenceOccurrences = sentenceOccurrences,
                tokens = tokens,
              )

            val candidateCard =
              findBestCardForToken(
                tokenNormalized = token.normalized,
                resolution = resolution,
                cardsByExactKey = cardsByExactKey,
              )

            val status = SrsScheduler.displayStatusFor(candidateCard, nowEpochMs)
            ReaderTokenHighlight(
              startOffset = token.startOffset,
              endOffset = token.endOffset,
              surface = token.surface,
              status = status,
              isPhrase = matchingPhraseOcc != null || resolution?.primaryMatch?.entry?.isMultiword == true,
              linkedCardId = candidateCard?.cardId,
            )
          }
        sentence.id to tokenHighlights
      }

    val sentenceCardsMap =
      bundle.sentences.mapNotNull { sentence ->
        val contentId =
          SrsScheduler.buildSentenceContentId(
            packageId = sentence.packageId,
            sourceSentenceId = sentence.sourceSentenceId,
            englishText = sentence.englishText,
          )
        val match =
          sentenceCards.firstOrNull {
            it.contentId == contentId || it.sourceSentenceId == sentence.id
          }
        if (match != null) sentence.id to match else null
      }.toMap()

    return ReaderPackageSnapshot(
      bundle = bundle,
      bookmarksBySentenceId = bookmarks.associateBy { it.sentenceId },
      progress = progress,
      tokenHighlightsBySentenceId = highlightsBySentence,
      sentenceSrsCardsBySentenceId = sentenceCardsMap,
      allWordCards = wordCards,
    )
  }

  private fun findBestCardForToken(
    tokenNormalized: String,
    resolution: ContextualResolutionResult?,
    cardsByExactKey: Map<WordCardKey, List<SrsCard>>,
  ): SrsCard? {
    val primary = resolution?.primaryMatch
    if (primary != null) {
      val entry = primary.entry
      val lemma =
        StableIdGenerator.normalizeKeyPart(
          entry.lemma.ifBlank { entry.normalizedTerm },
          fallback = "unknown",
        )
      val pos = StableIdGenerator.normalizeKeyPart(entry.partOfSpeech, fallback = "word")
      val sense = primary.recommendedSense ?: entry.senses.firstOrNull()
      val senseId =
        StableIdGenerator.normalizeKeyPart(
          sense?.let { it.sourceSenseId.ifBlank { it.id } }.orEmpty(),
          fallback = "sense-1",
        )

      val exactMatches = cardsByExactKey[WordCardKey(lemma = lemma, pos = pos, senseId = senseId)].orEmpty()
      if (exactMatches.isNotEmpty()) {
        return pickHighestPriorityCard(exactMatches)
      }
      return null
    }

    val predictedPos = resolution?.predictedPos?.let { StableIdGenerator.normalizeKeyPart(it, fallback = "") }?.takeIf { it.isNotEmpty() }
    val lemmas = MorphologyAnalyzer.resolveLemmas(tokenNormalized)
    for (candidate in lemmas) {
      val pos =
        predictedPos
          ?: candidate.hintedPos?.let { StableIdGenerator.normalizeKeyPart(it, fallback = "") }?.takeIf { it.isNotEmpty() }
          ?: "word"
      val manualMatches =
        cardsByExactKey[WordCardKey(lemma = StableIdGenerator.normalizeKeyPart(candidate.lemma, "unknown"), pos = pos, senseId = "manual-1")].orEmpty()
      if (manualMatches.isNotEmpty()) {
        return pickHighestPriorityCard(manualMatches)
      }
    }
    return null
  }

  private fun pickHighestPriorityCard(cards: List<SrsCard>): SrsCard {
    // Prefer cards with earliest due timestamp so DUE/LEARNING state surfaces immediately
    return cards.minBy { it.dueAtEpochMs }
  }

  private fun SrsCard.toWordCardKey(): WordCardKey =
    WordCardKey(
      lemma = StableIdGenerator.normalizeKeyPart(normalizedLemma.ifBlank { frontTerm }, fallback = "unknown"),
      pos = StableIdGenerator.normalizeKeyPart(pos, fallback = "word"),
      senseId = StableIdGenerator.normalizeKeyPart(senseId, fallback = "sense-1"),
    )

  private data class WordCardKey(
    val lemma: String,
    val pos: String,
    val senseId: String,
  )
}
