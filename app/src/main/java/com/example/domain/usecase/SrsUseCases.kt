package com.example.domain.usecase

import com.example.domain.model.GlossaryEntry
import com.example.domain.model.GlossarySense
import com.example.domain.model.ReviewQuality
import com.example.domain.model.Sentence
import com.example.domain.model.SrsCard
import com.example.domain.model.SrsCardState
import com.example.domain.model.SrsCardType
import com.example.domain.model.UserVocabularyItem
import com.example.domain.repository.LearningRepository
import com.example.domain.srs.SrsScheduler
import com.example.domain.vocabulary.MorphologyAnalyzer
import java.util.UUID

sealed interface CreateSrsCardResult {
  data class Created(val card: SrsCard) : CreateSrsCardResult

  data class AlreadyExists(val existingCard: SrsCard) : CreateSrsCardResult
}

class CreateSrsCardUseCase(
  private val learningRepository: LearningRepository,
) {
  suspend fun createWordCardFromGlossary(
    entry: GlossaryEntry,
    selectedSense: GlossarySense?,
    contextSentence: Sentence?,
    packageTitle: String?,
    nowEpochMs: Long = System.currentTimeMillis(),
  ): CreateSrsCardResult {
    val senseId = selectedSense?.sourceSenseId?.ifBlank { selectedSense.id } ?: "sense-1"
    val lemma = entry.lemma.ifBlank { entry.normalizedTerm }.trim().lowercase()
    val contentId = SrsScheduler.buildWordContentId(lemma, entry.partOfSpeech, senseId)

    val existing = learningRepository.getSrsCardByContentId(contentId)
    if (existing != null) {
      return CreateSrsCardResult.AlreadyExists(existing)
    }

    val meaningFa = selectedSense?.meaningFa?.ifBlank { entry.persianMeaning } ?: entry.persianMeaning
    val definitionEn = selectedSense?.definitionEn?.ifBlank { entry.englishDefinition } ?: entry.englishDefinition
    val exampleEn = selectedSense?.examples?.firstOrNull()?.text ?: entry.exampleSentence

    val card =
      SrsCard(
        cardId = "card_${UUID.randomUUID()}",
        cardType = SrsCardType.WORD,
        contentId = contentId,
        frontTerm = entry.term,
        backPersianMeaning = meaningFa,
        englishDefinition = definitionEn,
        normalizedLemma = lemma,
        pos = entry.partOfSpeech,
        senseId = senseId,
        phonetic = entry.pronunciation.primary() ?: entry.phonetic,
        exampleSentence = exampleEn,
        contextSentenceEn = contextSentence?.englishText ?: exampleEn,
        contextSentenceFa = contextSentence?.persianTranslation,
        sourcePackageId = entry.packageId,
        sourcePackageTitle = packageTitle,
        sourceGlossaryId = entry.id,
        sourceSentenceId = contextSentence?.id,
        state = SrsCardState.NEW,
        repetitions = 0,
        intervalDays = 0,
        easeFactor = SrsScheduler.DEFAULT_EASE_FACTOR,
        dueAtEpochMs = nowEpochMs,
        lastReviewedAtEpochMs = null,
        createdAtEpochMs = nowEpochMs,
      )
    learningRepository.upsertSrsCard(card)
    return CreateSrsCardResult.Created(card)
  }

  suspend fun createSentenceCard(
    sentence: Sentence,
    packageTitle: String?,
    nowEpochMs: Long = System.currentTimeMillis(),
  ): CreateSrsCardResult {
    val contentId =
      SrsScheduler.buildSentenceContentId(
        packageId = sentence.packageId,
        sourceSentenceId = sentence.sourceSentenceId,
        englishText = sentence.englishText,
      )
    val existing = learningRepository.getSrsCardByContentId(contentId)
    if (existing != null) {
      return CreateSrsCardResult.AlreadyExists(existing)
    }

    val card =
      SrsCard(
        cardId = "card_${UUID.randomUUID()}",
        cardType = SrsCardType.SENTENCE,
        contentId = contentId,
        frontTerm = sentence.englishText,
        backPersianMeaning = sentence.persianTranslation,
        englishDefinition = sentence.grammarNoteFa,
        normalizedLemma = sentence.englishText.trim().lowercase(),
        pos = "sentence",
        senseId = sentence.sourceSentenceId,
        phonetic = sentence.phoneticHint,
        exampleSentence = sentence.englishText,
        contextSentenceEn = sentence.englishText,
        contextSentenceFa = sentence.persianTranslation,
        sourcePackageId = sentence.packageId,
        sourcePackageTitle = packageTitle,
        sourceGlossaryId = null,
        sourceSentenceId = sentence.id,
        state = SrsCardState.NEW,
        repetitions = 0,
        intervalDays = 0,
        easeFactor = SrsScheduler.DEFAULT_EASE_FACTOR,
        dueAtEpochMs = nowEpochMs,
        lastReviewedAtEpochMs = null,
        createdAtEpochMs = nowEpochMs,
      )
    learningRepository.upsertSrsCard(card)
    return CreateSrsCardResult.Created(card)
  }

  suspend fun createManualWordAndCard(
    term: String,
    persianMeaning: String,
    englishDefinition: String?,
    pos: String,
    contextSentence: Sentence?,
    packageTitle: String?,
    addToSrs: Boolean,
    nowEpochMs: Long = System.currentTimeMillis(),
  ): CreateSrsCardResult? {
    val cleanTerm = term.trim()
    val cleanMeaning = persianMeaning.trim()
    if (cleanTerm.isEmpty() || cleanMeaning.isEmpty()) return null

    val normalized = MorphologyAnalyzer.normalizeToken(cleanTerm)
    val lemma = MorphologyAnalyzer.resolveLemmas(normalized).firstOrNull()?.lemma ?: normalized
    val cleanPos = pos.trim().lowercase().ifBlank { "word" }

    learningRepository.saveVocabularyItem(
      UserVocabularyItem(
        vocabId = "vocab_${normalized.hashCode()}",
        normalizedTerm = normalized,
        displayTerm = cleanTerm,
        persianMeaning = cleanMeaning,
        phonetic = null,
        notesFa = englishDefinition?.takeIf { it.isNotBlank() },
        sourcePackageId = contextSentence?.packageId,
        masteryLevel = 0,
        savedAtEpochMs = nowEpochMs,
      )
    )

    if (!addToSrs) return null

    val contentId = SrsScheduler.buildWordContentId(lemma, cleanPos, "manual-1")
    val existing = learningRepository.getSrsCardByContentId(contentId)
    if (existing != null) {
      return CreateSrsCardResult.AlreadyExists(existing)
    }

    val card =
      SrsCard(
        cardId = "card_${UUID.randomUUID()}",
        cardType = SrsCardType.WORD,
        contentId = contentId,
        frontTerm = cleanTerm,
        backPersianMeaning = cleanMeaning,
        englishDefinition = englishDefinition?.takeIf { it.isNotBlank() },
        normalizedLemma = lemma,
        pos = cleanPos,
        senseId = "manual-1",
        phonetic = null,
        exampleSentence = contextSentence?.englishText,
        contextSentenceEn = contextSentence?.englishText,
        contextSentenceFa = contextSentence?.persianTranslation,
        sourcePackageId = contextSentence?.packageId,
        sourcePackageTitle = packageTitle,
        sourceGlossaryId = null,
        sourceSentenceId = contextSentence?.id,
        state = SrsCardState.NEW,
        repetitions = 0,
        intervalDays = 0,
        easeFactor = SrsScheduler.DEFAULT_EASE_FACTOR,
        dueAtEpochMs = nowEpochMs,
        lastReviewedAtEpochMs = null,
        createdAtEpochMs = nowEpochMs,
      )
    learningRepository.upsertSrsCard(card)
    return CreateSrsCardResult.Created(card)
  }
}

class ReviewSrsCardUseCase(
  private val learningRepository: LearningRepository,
  private val scheduler: SrsScheduler,
) {
  suspend operator fun invoke(
    card: SrsCard,
    quality: ReviewQuality,
    nowEpochMs: Long = System.currentTimeMillis(),
  ): SrsScheduler.ScheduleOutcome {
    val outcome = scheduler.scheduleNextReview(card, quality, nowEpochMs)
    learningRepository.recordSrsReview(outcome.updatedCard, outcome.reviewLog)
    return outcome
  }
}

class DeleteSrsCardUseCase(
  private val learningRepository: LearningRepository,
) {
  suspend operator fun invoke(cardId: String) {
    learningRepository.deleteSrsCardById(cardId)
  }
}
