package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.LearningProgressEntity
import com.example.data.local.entity.ReaderBookmarkEntity
import com.example.data.local.entity.SrsCardEntity
import com.example.data.local.entity.SrsReviewLogEntity
import com.example.data.local.entity.UserVocabularyEntity
import kotlinx.coroutines.flow.Flow

data class DashboardStatsRow(
  val totalPackages: Int,
  val totalSentences: Int,
  val totalGlossaryEntries: Int,
  val totalQuestions: Int,
  val totalSrsCards: Int,
  val dueSrsCards: Int,
  val newSrsCards: Int,
  val totalReviewLogs: Int,
  val totalUserVocabulary: Int,
  val totalBookmarks: Int,
)

@Dao
interface LearningDao {
  @Query(
    """
    SELECT
      (SELECT COUNT(*) FROM content_packages) AS totalPackages,
      (SELECT COUNT(*) FROM sentences) AS totalSentences,
      (SELECT COUNT(*) FROM glossary_entries) AS totalGlossaryEntries,
      (SELECT COUNT(*) FROM questions) AS totalQuestions,
      (SELECT COUNT(*) FROM srs_cards) AS totalSrsCards,
      (SELECT COUNT(*) FROM srs_cards WHERE state != 'NEW' AND dueAtEpochMs <= :nowEpochMs) AS dueSrsCards,
      (SELECT COUNT(*) FROM srs_cards WHERE state = 'NEW') AS newSrsCards,
      (SELECT COUNT(*) FROM srs_review_logs) AS totalReviewLogs,
      (SELECT COUNT(*) FROM user_vocabulary) AS totalUserVocabulary,
      (SELECT COUNT(*) FROM reader_bookmarks) AS totalBookmarks
    """
  )
  fun observeDashboardStats(nowEpochMs: Long): Flow<DashboardStatsRow>

  @Query("SELECT * FROM srs_cards ORDER BY dueAtEpochMs ASC")
  fun observeAllSrsCards(): Flow<List<SrsCardEntity>>

  @Query("SELECT * FROM srs_cards WHERE state != 'NEW' AND dueAtEpochMs <= :nowEpochMs ORDER BY dueAtEpochMs ASC")
  fun observeDueSrsCards(nowEpochMs: Long): Flow<List<SrsCardEntity>>

  @Query("SELECT * FROM srs_cards WHERE state = 'NEW' ORDER BY createdAtEpochMs ASC")
  fun observeNewSrsCards(): Flow<List<SrsCardEntity>>

  @Query("SELECT * FROM srs_cards WHERE contentId = :contentId LIMIT 1")
  suspend fun getSrsCardByContentId(contentId: String): SrsCardEntity?

  @Query("SELECT * FROM srs_review_logs ORDER BY reviewedAtEpochMs DESC LIMIT :limit")
  fun observeRecentReviewLogs(limit: Int): Flow<List<SrsReviewLogEntity>>

  @Query("SELECT * FROM srs_review_logs ORDER BY reviewedAtEpochMs DESC")
  fun observeAllReviewLogs(): Flow<List<SrsReviewLogEntity>>

  @Query("SELECT * FROM srs_review_logs WHERE cardId = :cardId ORDER BY reviewedAtEpochMs DESC")
  suspend fun getReviewLogsForCard(cardId: String): List<SrsReviewLogEntity>

  @Query("SELECT * FROM user_vocabulary ORDER BY savedAtEpochMs DESC")
  fun observeUserVocabulary(): Flow<List<UserVocabularyEntity>>

  @Query("SELECT * FROM reader_bookmarks ORDER BY createdAtEpochMs DESC")
  fun observeBookmarks(): Flow<List<ReaderBookmarkEntity>>

  @Query("SELECT * FROM reader_bookmarks WHERE packageId = :packageId ORDER BY sentenceOrderIndex ASC")
  fun observeBookmarksForPackage(packageId: String): Flow<List<ReaderBookmarkEntity>>

  @Query("SELECT * FROM learning_progress ORDER BY updatedAtEpochMs DESC")
  fun observeLearningProgress(): Flow<List<LearningProgressEntity>>

  @Query("SELECT * FROM learning_progress WHERE packageId = :packageId LIMIT 1")
  fun observeProgressForPackage(packageId: String): Flow<LearningProgressEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSrsCard(card: SrsCardEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertReviewLog(log: SrsReviewLogEntity)

  @Transaction
  suspend fun recordSrsReview(updatedCard: SrsCardEntity, log: SrsReviewLogEntity) {
    upsertSrsCard(updatedCard)
    insertReviewLog(log)
  }

  @Query("DELETE FROM srs_cards WHERE cardId = :cardId")
  suspend fun deleteSrsCardById(cardId: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertUserVocabulary(item: UserVocabularyEntity)

  @Query("DELETE FROM user_vocabulary WHERE vocabId = :vocabId")
  suspend fun deleteUserVocabularyById(vocabId: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertBookmark(bookmark: ReaderBookmarkEntity)

  @Query("DELETE FROM reader_bookmarks WHERE packageId = :packageId AND sentenceId = :sentenceId")
  suspend fun deleteBookmark(packageId: String, sentenceId: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertLearningProgress(progress: LearningProgressEntity)

  @Query("DELETE FROM learning_progress")
  suspend fun deleteAllLearningProgress()

  @Query("DELETE FROM reader_bookmarks")
  suspend fun deleteAllBookmarks()

  @Query("DELETE FROM srs_cards")
  suspend fun deleteAllSrsCards()

  @Query("DELETE FROM user_vocabulary")
  suspend fun deleteAllUserVocabulary()

  @Transaction
  suspend fun resetReadingProgressAndBookmarks() {
    deleteAllLearningProgress()
    deleteAllBookmarks()
  }

  @Transaction
  suspend fun clearAllLearningData() {
    deleteAllSrsCards()
    deleteAllLearningProgress()
    deleteAllBookmarks()
    deleteAllUserVocabulary()
  }
}
