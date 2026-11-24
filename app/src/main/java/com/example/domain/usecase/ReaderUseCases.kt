package com.example.domain.usecase

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

      val now = nowProvider()
      val wordCards = allCards.filter { it.cardType == SrsCardType.WORD }
      val sentenceCards = allCards.filter { it.cardType == SrsCardType.SENTENCE }

      val cardsByLemma = wordCards.groupBy { it.normalizedLemma.lowercase() }
      val cardsByFront = wordCards.groupBy { it.frontTerm.trim().lowercase() }
      val glossaryById = bundle.glossaryEntries.associateBy { it.id }

      val highlightsBySentence =
        bundle.sentences.associate { sentence ->
          val sentenceOccurrences = bundle.wordOccurrences.filter { it.sentenceId == sentence.id }
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

              val matchingSingleOcc =
                sentenceOccurrences.firstOrNull { occ ->
                  occ.startOffset < token.endOffset && occ.endOffset > token.startOffset
                }

              val candidateCard =
                findBestCardForToken(
                  tokenNormalized = token.normalized,
                  occurrenceEntryId = (matchingPhraseOcc ?: matchingSingleOcc)?.glossaryEntryId,
                  glossaryById = glossaryById,
                  cardsByLemma = cardsByLemma,
                  cardsByFront = cardsByFront,
                )

              val status = SrsScheduler.displayStatusFor(candidateCard, now)
              ReaderTokenHighlight(
                startOffset = token.startOffset,
                endOffset = token.endOffset,
                surface = token.surface,
                status = status,
                isPhrase = matchingPhraseOcc != null,
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

      ReaderPackageSnapshot(
        bundle = bundle,
        bookmarksBySentenceId = bookmarks.associateBy { it.sentenceId },
        progress = progress,
        tokenHighlightsBySentenceId = highlightsBySentence,
        sentenceSrsCardsBySentenceId = sentenceCardsMap,
        allWordCards = wordCards,
      )
    }
  }

  private fun findBestCardForToken(
    tokenNormalized: String,
    occurrenceEntryId: String?,
    glossaryById: Map<String, com.example.domain.model.GlossaryEntry>,
    cardsByLemma: Map<String, List<SrsCard>>,
    cardsByFront: Map<String, List<SrsCard>>,
  ): SrsCard? {
    val occurrenceEntry = occurrenceEntryId?.let { glossaryById[it] }
    if (occurrenceEntry != null) {
      val exactSenseCards =
        cardsByLemma[occurrenceEntry.lemma.lowercase()].orEmpty()
      if (exactSenseCards.isNotEmpty()) {
        return pickHighestPriorityCard(exactSenseCards)
      }
    }

    val directFront = cardsByFront[tokenNormalized].orEmpty()
    if (directFront.isNotEmpty()) return pickHighestPriorityCard(directFront)

    val lemmas = MorphologyAnalyzer.resolveLemmas(tokenNormalized)
    for (candidate in lemmas) {
      val matched = cardsByLemma[candidate.lemma].orEmpty()
      if (matched.isNotEmpty()) return pickHighestPriorityCard(matched)
    }
    return null
  }

  private fun pickHighestPriorityCard(cards: List<SrsCard>): SrsCard {
    // Prefer cards with earliest due timestamp so DUE/LEARNING state surfaces immediately
    return cards.minBy { it.dueAtEpochMs }
  }
}
