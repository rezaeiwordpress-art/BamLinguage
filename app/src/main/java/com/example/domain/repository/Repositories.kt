package com.example.domain.repository

import com.example.core.ai.AiProviderType
import com.example.core.common.ThemeMode
import com.example.core.common.TtsAccent
import com.example.core.datastore.UserPreferences
import com.example.domain.model.ContentPackage
import com.example.domain.model.ContentPackageBundle
import com.example.domain.model.ContentPackageSummary
import com.example.domain.model.DashboardStats
import com.example.domain.model.GlossaryEntry
import com.example.domain.model.LearningProgress
import com.example.domain.model.QuizAttempt
import com.example.domain.model.ReaderBookmark
import com.example.domain.model.SrsCard
import com.example.domain.model.SrsReviewLog
import com.example.domain.model.UserVocabularyItem
import kotlinx.coroutines.flow.Flow

interface ContentRepository {
  fun observePackageSummaries(): Flow<List<ContentPackageSummary>>

  fun observePackageBundle(packageId: String): Flow<ContentPackageBundle?>

  suspend fun getPackageById(packageId: String): ContentPackage?

  suspend fun getPackageBundle(packageId: String): ContentPackageBundle?

  suspend fun getAllGlossaryEntries(): List<GlossaryEntry>

  suspend fun replacePackageTransactionally(bundle: ContentPackageBundle)

  suspend fun deletePackage(packageId: String)

  suspend fun searchGlossary(query: String): List<GlossaryEntry>

  suspend fun getPackageCount(): Int
}

sealed interface SrsCardInsertOutcome {
  data class Inserted(val card: SrsCard) : SrsCardInsertOutcome

  data class AlreadyExists(val existingCard: SrsCard) : SrsCardInsertOutcome
}

interface LearningRepository {
  fun observeDashboardStats(nowEpochMs: Long): Flow<DashboardStats>

  fun observeAllSrsCards(): Flow<List<SrsCard>>

  fun observeDueSrsCards(nowEpochMs: Long): Flow<List<SrsCard>>

  fun observeNewSrsCards(): Flow<List<SrsCard>>

  fun observeRecentReviewLogs(limit: Int = 50): Flow<List<SrsReviewLog>>

  fun observeAllReviewLogs(): Flow<List<SrsReviewLog>>

  fun observeUserVocabulary(): Flow<List<UserVocabularyItem>>

  fun observeBookmarks(): Flow<List<ReaderBookmark>>

  fun observeBookmarksForPackage(packageId: String): Flow<List<ReaderBookmark>>

  fun observeLearningProgress(): Flow<List<LearningProgress>>

  fun observeProgressForPackage(packageId: String): Flow<LearningProgress?>

  fun observeQuizAttemptsForPackage(packageId: String): Flow<List<QuizAttempt>>

  fun observeAllQuizAttempts(): Flow<List<QuizAttempt>>

  suspend fun getSrsCardByContentId(contentId: String): SrsCard?

  suspend fun insertSrsCardIfAbsent(card: SrsCard): SrsCardInsertOutcome

  suspend fun upsertSrsCard(card: SrsCard)

  suspend fun recordSrsReview(updatedCard: SrsCard, log: SrsReviewLog)

  suspend fun deleteSrsCardById(cardId: String)

  suspend fun saveVocabularyItem(item: UserVocabularyItem)

  suspend fun deleteVocabularyItemById(vocabId: String)

  suspend fun saveBookmark(bookmark: ReaderBookmark)

  suspend fun deleteBookmark(packageId: String, sentenceId: String)

  suspend fun upsertProgress(progress: LearningProgress)

  suspend fun saveQuizAttempt(
    packageId: String,
    questionId: String,
    selectedOptionIndex: Int,
    isCorrect: Boolean,
    nowEpochMs: Long = System.currentTimeMillis(),
  )

  suspend fun resetReadingProgressAndBookmarks()

  suspend fun clearAllLearningData()
}

interface PreferencesRepository {
  val userPreferences: Flow<UserPreferences>

  suspend fun setThemeMode(mode: ThemeMode)

  suspend fun setTtsSpeed(speed: Float)

  suspend fun setTtsAccent(accent: TtsAccent)

  suspend fun setPreferredVoiceName(voiceName: String?)

  suspend fun setDailyGoalCards(goal: Int)

  suspend fun setNewCardsPerDay(count: Int)

  suspend fun setShowTranslationsInReader(show: Boolean)

  suspend fun setShowIpaInReader(show: Boolean)

  suspend fun setShowGrammarNotesInReader(show: Boolean)

  suspend fun setAutoPlaySrsAudio(autoPlay: Boolean)

  suspend fun setAiConfiguration(enabled: Boolean, providerType: AiProviderType, endpointUrl: String)

  suspend fun markSampleSeedAttempted(attempted: Boolean = true)

  suspend fun setSelectedPackageId(packageId: String?)
}
