package com.example.data.mapper

import com.example.core.common.CefrLevel
import com.example.core.common.ContentType
import com.example.data.local.dao.ContentPackageSummaryRow
import com.example.data.local.dao.DashboardStatsRow
import com.example.data.local.entity.ContentPackageEntity
import com.example.data.local.entity.GlossaryEntryEntity
import com.example.data.local.entity.GlossarySenseEntity
import com.example.data.local.entity.LearningProgressEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.local.entity.ReaderBookmarkEntity
import com.example.data.local.entity.SentenceEntity
import com.example.data.local.entity.SpeakerEntity
import com.example.data.local.entity.SrsCardEntity
import com.example.data.local.entity.SrsReviewLogEntity
import com.example.data.local.entity.UserVocabularyEntity
import com.example.data.local.entity.WordOccurrenceEntity
import com.example.domain.model.ComprehensionQuestion
import com.example.domain.model.ContentPackage
import com.example.domain.model.ContentPackageSummary
import com.example.domain.model.DashboardStats
import com.example.domain.model.GlossaryEntry
import com.example.domain.model.GlossaryExample
import com.example.domain.model.GlossarySense
import com.example.domain.model.LearningProgress
import com.example.domain.model.PackageMetadata
import com.example.domain.model.PronunciationInfo
import com.example.domain.model.ReaderBookmark
import com.example.domain.model.Sentence
import com.example.domain.model.Speaker
import com.example.domain.model.SrsCard
import com.example.domain.model.SrsCardState
import com.example.domain.model.SrsCardType
import com.example.domain.model.SrsReviewLog
import com.example.domain.model.UserVocabularyItem
import com.example.domain.model.WordOccurrence
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
private data class ExamplePayload(
  val text: String = "",
  val translationFa: String = "",
)

private val mapperJson = Json { ignoreUnknownKeys = true }
private val exampleListSerializer = ListSerializer(ExamplePayload.serializer())

fun ContentPackageEntity.toDomain(): ContentPackage =
  ContentPackage(
    packageId = packageId,
    schemaVersion = schemaVersion,
    contentVersion = contentVersion,
    contentType = ContentType.fromWireValue(contentType) ?: ContentType.STORY,
    level = CefrLevel.fromCode(level) ?: CefrLevel.A1,
    topic = topic,
    title = title,
    titleFa = titleFa,
    description = description,
    descriptionFa = descriptionFa,
    metadata =
      PackageMetadata(
        author = author,
        createdAt = createdAt,
        updatedAt = updatedAt,
        wordCount = wordCount,
        sentenceCount = sentenceCountMeta,
        glossaryCount = glossaryCountMeta,
        estimatedMinutes = estimatedMinutes,
        language = language,
        translationLanguage = translationLanguage,
        tags = tags,
      ),
    importedAtEpochMs = importedAtEpochMs,
    isBundledSample = isBundledSample,
  )

fun ContentPackage.toEntity(): ContentPackageEntity =
  ContentPackageEntity(
    packageId = packageId,
    schemaVersion = schemaVersion,
    contentVersion = contentVersion,
    contentType = contentType.wireValue,
    level = level.code,
    topic = topic,
    title = title,
    titleFa = titleFa,
    description = description,
    descriptionFa = descriptionFa,
    author = metadata.author,
    createdAt = metadata.createdAt,
    updatedAt = metadata.updatedAt,
    wordCount = metadata.wordCount,
    sentenceCountMeta = metadata.sentenceCount,
    glossaryCountMeta = metadata.glossaryCount,
    estimatedMinutes = metadata.estimatedMinutes,
    language = metadata.language,
    translationLanguage = metadata.translationLanguage,
    tags = metadata.tags,
    importedAtEpochMs = importedAtEpochMs,
    isBundledSample = isBundledSample,
  )

fun ContentPackageSummaryRow.toDomain(): ContentPackageSummary =
  ContentPackageSummary(
    contentPackage = contentPackage.toDomain(),
    sentenceCount = sentenceCount,
    glossaryCount = glossaryCount,
    questionCount = questionCount,
    speakerCount = speakerCount,
  )

fun SpeakerEntity.toDomain(): Speaker =
  Speaker(
    id = id,
    packageId = packageId,
    sourceSpeakerId = sourceSpeakerId,
    name = name,
    nameFa = nameFa,
    role = role,
  )

fun Speaker.toEntity(): SpeakerEntity =
  SpeakerEntity(
    id = id,
    packageId = packageId,
    sourceSpeakerId = sourceSpeakerId,
    name = name,
    nameFa = nameFa,
    role = role,
  )

fun SentenceEntity.toDomain(): Sentence =
  Sentence(
    id = id,
    packageId = packageId,
    sourceSentenceId = sourceSentenceId,
    orderIndex = orderIndex,
    speakerId = speakerId,
    englishText = englishText,
    persianTranslation = persianTranslation,
    phoneticHint = phoneticHint,
    grammarNoteFa = grammarNoteFa,
  )

fun Sentence.toEntity(): SentenceEntity =
  SentenceEntity(
    id = id,
    packageId = packageId,
    sourceSentenceId = sourceSentenceId,
    orderIndex = orderIndex,
    speakerId = speakerId,
    englishText = englishText,
    persianTranslation = persianTranslation,
    phoneticHint = phoneticHint,
    grammarNoteFa = grammarNoteFa,
  )

fun GlossarySenseEntity.toDomain(): GlossarySense {
  val parsedExamples =
    runCatching {
      mapperJson.decodeFromString(exampleListSerializer, examplesJson).map {
        GlossaryExample(text = it.text, translationFa = it.translationFa)
      }
    }.getOrDefault(emptyList())

  return GlossarySense(
    id = id,
    glossaryEntryId = glossaryEntryId,
    sourceSenseId = sourceSenseId,
    orderIndex = orderIndex,
    meaningFa = meaningFa,
    definitionEn = definitionEn,
    examples = parsedExamples,
  )
}

fun GlossarySense.toEntity(packageId: String): GlossarySenseEntity {
  val encoded =
    mapperJson.encodeToString(
      exampleListSerializer,
      examples.map { ExamplePayload(it.text, it.translationFa) },
    )
  return GlossarySenseEntity(
    id = id,
    glossaryEntryId = glossaryEntryId,
    packageId = packageId,
    sourceSenseId = sourceSenseId,
    orderIndex = orderIndex,
    meaningFa = meaningFa,
    definitionEn = definitionEn,
    examplesJson = encoded,
  )
}

fun GlossaryEntryEntity.toDomain(senses: List<GlossarySense>): GlossaryEntry {
  val resolvedSenses =
    if (senses.isNotEmpty()) {
      senses.sortedBy { it.orderIndex }
    } else {
      listOf(
        GlossarySense(
          id = "${id}::sense-1",
          glossaryEntryId = id,
          sourceSenseId = "sense-1",
          orderIndex = 0,
          meaningFa = persianMeaning,
          definitionEn = englishDefinition,
          examples =
            exampleSentence?.let {
              listOf(GlossaryExample(text = it, translationFa = ""))
            } ?: emptyList(),
        )
      )
    }

  return GlossaryEntry(
    id = id,
    packageId = packageId,
    sourceGlossaryId = sourceGlossaryId,
    term = term,
    normalizedTerm = normalizedTerm,
    lemma = lemma.ifBlank { normalizedTerm },
    forms = forms,
    partOfSpeech = partOfSpeech,
    cefr = CefrLevel.fromCode(cefr) ?: CefrLevel.A1,
    senses = resolvedSenses,
    phonetic = phonetic ?: pronunciationUs ?: pronunciationUk,
    pronunciation = PronunciationInfo(uk = pronunciationUk ?: phonetic, us = pronunciationUs ?: phonetic),
    persianMeaning = persianMeaning,
    englishDefinition = englishDefinition,
    exampleSentence = exampleSentence,
    synonyms = synonyms,
    antonyms = antonyms,
    sentenceIds = sentenceIds,
  )
}

fun GlossaryEntry.toEntity(): GlossaryEntryEntity =
  GlossaryEntryEntity(
    id = id,
    packageId = packageId,
    sourceGlossaryId = sourceGlossaryId,
    term = term,
    normalizedTerm = normalizedTerm,
    lemma = lemma,
    forms = forms,
    partOfSpeech = partOfSpeech,
    cefr = cefr.code,
    phonetic = phonetic ?: pronunciation.primary(),
    pronunciationUk = pronunciation.uk,
    pronunciationUs = pronunciation.us,
    persianMeaning = persianMeaning,
    englishDefinition = englishDefinition,
    exampleSentence = exampleSentence,
    synonyms = synonyms,
    antonyms = antonyms,
    sentenceIds = sentenceIds,
  )

fun WordOccurrenceEntity.toDomain(): WordOccurrence =
  WordOccurrence(
    id = id,
    packageId = packageId,
    sentenceId = sentenceId,
    glossaryEntryId = glossaryEntryId,
    surfaceForm = surfaceForm,
    startOffset = startOffset,
    endOffset = endOffset,
  )

fun WordOccurrence.toEntity(): WordOccurrenceEntity =
  WordOccurrenceEntity(
    id = id,
    packageId = packageId,
    sentenceId = sentenceId,
    glossaryEntryId = glossaryEntryId,
    surfaceForm = surfaceForm,
    startOffset = startOffset,
    endOffset = endOffset,
  )

fun QuestionEntity.toDomain(): ComprehensionQuestion =
  ComprehensionQuestion(
    id = id,
    packageId = packageId,
    sourceQuestionId = sourceQuestionId,
    orderIndex = orderIndex,
    promptEn = promptEn,
    promptFa = promptFa,
    options = options,
    correctOptionIndex = correctOptionIndex,
    explanationFa = explanationFa,
  )

fun ComprehensionQuestion.toEntity(): QuestionEntity =
  QuestionEntity(
    id = id,
    packageId = packageId,
    sourceQuestionId = sourceQuestionId,
    orderIndex = orderIndex,
    promptEn = promptEn,
    promptFa = promptFa,
    options = options,
    correctOptionIndex = correctOptionIndex,
    explanationFa = explanationFa,
  )

fun SrsCardEntity.toDomain(): SrsCard =
  SrsCard(
    cardId = cardId,
    cardType = runCatching { SrsCardType.valueOf(cardType) }.getOrDefault(SrsCardType.WORD),
    contentId = contentId.ifBlank { cardId },
    frontTerm = frontTerm,
    backPersianMeaning = backPersianMeaning,
    englishDefinition = englishDefinition,
    normalizedLemma = normalizedLemma.ifBlank { frontTerm.trim().lowercase() },
    pos = pos,
    senseId = senseId.ifBlank { "sense-1" },
    phonetic = phonetic,
    exampleSentence = exampleSentence,
    contextSentenceEn = contextSentenceEn,
    contextSentenceFa = contextSentenceFa,
    sourcePackageId = sourcePackageId,
    sourcePackageTitle = sourcePackageTitle,
    sourceGlossaryId = sourceGlossaryId,
    sourceSentenceId = sourceSentenceId,
    state = runCatching { SrsCardState.valueOf(state) }.getOrDefault(SrsCardState.NEW),
    repetitions = repetitions,
    intervalDays = intervalDays,
    easeFactor = easeFactor,
    dueAtEpochMs = dueAtEpochMs,
    lastReviewedAtEpochMs = lastReviewedAtEpochMs,
    createdAtEpochMs = createdAtEpochMs,
  )

fun SrsCard.toEntity(): SrsCardEntity =
  SrsCardEntity(
    cardId = cardId,
    cardType = cardType.name,
    contentId = contentId,
    frontTerm = frontTerm,
    backPersianMeaning = backPersianMeaning,
    englishDefinition = englishDefinition,
    normalizedLemma = normalizedLemma,
    pos = pos,
    senseId = senseId,
    phonetic = phonetic,
    exampleSentence = exampleSentence,
    contextSentenceEn = contextSentenceEn,
    contextSentenceFa = contextSentenceFa,
    sourcePackageId = sourcePackageId,
    sourcePackageTitle = sourcePackageTitle,
    sourceGlossaryId = sourceGlossaryId,
    sourceSentenceId = sourceSentenceId,
    state = state.name,
    repetitions = repetitions,
    intervalDays = intervalDays,
    easeFactor = easeFactor,
    dueAtEpochMs = dueAtEpochMs,
    lastReviewedAtEpochMs = lastReviewedAtEpochMs,
    createdAtEpochMs = createdAtEpochMs,
  )

fun SrsReviewLogEntity.toDomain(): SrsReviewLog =
  SrsReviewLog(
    logId = logId,
    cardId = cardId,
    qualityScore = qualityScore,
    previousIntervalDays = previousIntervalDays,
    newIntervalDays = newIntervalDays,
    previousEaseFactor = previousEaseFactor,
    newEaseFactor = newEaseFactor,
    reviewedAtEpochMs = reviewedAtEpochMs,
  )

fun SrsReviewLog.toEntity(): SrsReviewLogEntity =
  SrsReviewLogEntity(
    logId = logId,
    cardId = cardId,
    qualityScore = qualityScore,
    previousIntervalDays = previousIntervalDays,
    newIntervalDays = newIntervalDays,
    previousEaseFactor = previousEaseFactor,
    newEaseFactor = newEaseFactor,
    reviewedAtEpochMs = reviewedAtEpochMs,
  )

fun LearningProgressEntity.toDomain(): LearningProgress =
  LearningProgress(
    progressId = progressId,
    packageId = packageId,
    packageTitleSnapshot = packageTitleSnapshot,
    completedSentenceCount = completedSentenceCount,
    totalSentenceCount = totalSentenceCount,
    correctQuestionsCount = correctQuestionsCount,
    totalQuestionsCount = totalQuestionsCount,
    lastReadSentenceIndex = lastReadSentenceIndex,
    updatedAtEpochMs = updatedAtEpochMs,
  )

fun LearningProgress.toEntity(): LearningProgressEntity =
  LearningProgressEntity(
    progressId = progressId,
    packageId = packageId,
    packageTitleSnapshot = packageTitleSnapshot,
    completedSentenceCount = completedSentenceCount,
    totalSentenceCount = totalSentenceCount,
    correctQuestionsCount = correctQuestionsCount,
    totalQuestionsCount = totalQuestionsCount,
    lastReadSentenceIndex = lastReadSentenceIndex,
    updatedAtEpochMs = updatedAtEpochMs,
  )

fun UserVocabularyEntity.toDomain(): UserVocabularyItem =
  UserVocabularyItem(
    vocabId = vocabId,
    normalizedTerm = normalizedTerm,
    displayTerm = displayTerm,
    persianMeaning = persianMeaning,
    phonetic = phonetic,
    notesFa = notesFa,
    sourcePackageId = sourcePackageId,
    masteryLevel = masteryLevel,
    savedAtEpochMs = savedAtEpochMs,
  )

fun UserVocabularyItem.toEntity(): UserVocabularyEntity =
  UserVocabularyEntity(
    vocabId = vocabId,
    normalizedTerm = normalizedTerm,
    displayTerm = displayTerm,
    persianMeaning = persianMeaning,
    phonetic = phonetic,
    notesFa = notesFa,
    sourcePackageId = sourcePackageId,
    masteryLevel = masteryLevel,
    savedAtEpochMs = savedAtEpochMs,
  )

fun ReaderBookmarkEntity.toDomain(): ReaderBookmark =
  ReaderBookmark(
    bookmarkId = bookmarkId,
    packageId = packageId,
    packageTitleSnapshot = packageTitleSnapshot,
    sentenceId = sentenceId,
    sentenceOrderIndex = sentenceOrderIndex,
    noteFa = noteFa,
    createdAtEpochMs = createdAtEpochMs,
  )

fun ReaderBookmark.toEntity(): ReaderBookmarkEntity =
  ReaderBookmarkEntity(
    bookmarkId = bookmarkId,
    packageId = packageId,
    packageTitleSnapshot = packageTitleSnapshot,
    sentenceId = sentenceId,
    sentenceOrderIndex = sentenceOrderIndex,
    noteFa = noteFa,
    createdAtEpochMs = createdAtEpochMs,
  )

fun DashboardStatsRow.toDomain(): DashboardStats =
  DashboardStats(
    totalPackages = totalPackages,
    totalSentences = totalSentences,
    totalGlossaryEntries = totalGlossaryEntries,
    totalQuestions = totalQuestions,
    totalSrsCards = totalSrsCards,
    dueSrsCards = dueSrsCards,
    newSrsCards = newSrsCards,
    totalReviewLogs = totalReviewLogs,
    totalUserVocabulary = totalUserVocabulary,
    totalBookmarks = totalBookmarks,
  )
