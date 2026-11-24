package com.example.data.repository

import com.example.core.common.DispatcherProvider
import com.example.data.local.dao.LearningDao
import com.example.data.mapper.toDomain
import com.example.data.mapper.toEntity
import com.example.domain.model.DashboardStats
import com.example.domain.model.LearningProgress
import com.example.domain.model.ReaderBookmark
import com.example.domain.model.SrsCard
import com.example.domain.model.SrsReviewLog
import com.example.domain.model.UserVocabularyItem
import com.example.domain.repository.LearningRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomLearningRepository(
  private val learningDao: LearningDao,
  private val dispatchers: DispatcherProvider,
) : LearningRepository {

  override fun observeDashboardStats(nowEpochMs: Long): Flow<DashboardStats> =
    learningDao.observeDashboardStats(nowEpochMs).map { it.toDomain() }

  override fun observeAllSrsCards(): Flow<List<SrsCard>> =
    learningDao.observeAllSrsCards().map { list -> list.map { it.toDomain() } }

  override fun observeDueSrsCards(nowEpochMs: Long): Flow<List<SrsCard>> =
    learningDao.observeDueSrsCards(nowEpochMs).map { list -> list.map { it.toDomain() } }

  override fun observeNewSrsCards(): Flow<List<SrsCard>> =
    learningDao.observeNewSrsCards().map { list -> list.map { it.toDomain() } }

  override fun observeRecentReviewLogs(limit: Int): Flow<List<SrsReviewLog>> =
    learningDao.observeRecentReviewLogs(limit).map { list -> list.map { it.toDomain() } }

  override fun observeAllReviewLogs(): Flow<List<SrsReviewLog>> =
    learningDao.observeAllReviewLogs().map { list -> list.map { it.toDomain() } }

  override fun observeUserVocabulary(): Flow<List<UserVocabularyItem>> =
    learningDao.observeUserVocabulary().map { list -> list.map { it.toDomain() } }

  override fun observeBookmarks(): Flow<List<ReaderBookmark>> =
    learningDao.observeBookmarks().map { list -> list.map { it.toDomain() } }

  override fun observeBookmarksForPackage(packageId: String): Flow<List<ReaderBookmark>> =
    learningDao.observeBookmarksForPackage(packageId).map { list -> list.map { it.toDomain() } }

  override fun observeLearningProgress(): Flow<List<LearningProgress>> =
    learningDao.observeLearningProgress().map { list -> list.map { it.toDomain() } }

  override fun observeProgressForPackage(packageId: String): Flow<LearningProgress?> =
    learningDao.observeProgressForPackage(packageId).map { it?.toDomain() }

  override suspend fun getSrsCardByContentId(contentId: String): SrsCard? =
    withContext(dispatchers.io) { learningDao.getSrsCardByContentId(contentId)?.toDomain() }

  override suspend fun upsertSrsCard(card: SrsCard) =
    withContext(dispatchers.io) { learningDao.upsertSrsCard(card.toEntity()) }

  override suspend fun recordSrsReview(updatedCard: SrsCard, log: SrsReviewLog) =
    withContext(dispatchers.io) { learningDao.recordSrsReview(updatedCard.toEntity(), log.toEntity()) }

  override suspend fun deleteSrsCardById(cardId: String) =
    withContext(dispatchers.io) { learningDao.deleteSrsCardById(cardId) }

  override suspend fun saveVocabularyItem(item: UserVocabularyItem) =
    withContext(dispatchers.io) { learningDao.upsertUserVocabulary(item.toEntity()) }

  override suspend fun deleteVocabularyItemById(vocabId: String) =
    withContext(dispatchers.io) { learningDao.deleteUserVocabularyById(vocabId) }

  override suspend fun saveBookmark(bookmark: ReaderBookmark) =
    withContext(dispatchers.io) { learningDao.upsertBookmark(bookmark.toEntity()) }

  override suspend fun deleteBookmark(packageId: String, sentenceId: String) =
    withContext(dispatchers.io) { learningDao.deleteBookmark(packageId, sentenceId) }

  override suspend fun upsertProgress(progress: LearningProgress) =
    withContext(dispatchers.io) { learningDao.upsertLearningProgress(progress.toEntity()) }

  override suspend fun resetReadingProgressAndBookmarks() =
    withContext(dispatchers.io) { learningDao.resetReadingProgressAndBookmarks() }

  override suspend fun clearAllLearningData() =
    withContext(dispatchers.io) { learningDao.clearAllLearningData() }
}
