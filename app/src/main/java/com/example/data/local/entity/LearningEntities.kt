package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Learning tables are intentionally decoupled from [ContentPackageEntity] cascade deletion.
 * Deleting a content package NEVER deletes user SRS cards, review logs, progress,
 * vocabulary, or bookmarks.
 */
@Entity(
  tableName = "srs_cards",
  indices = [
    Index(value = ["dueAtEpochMs"]),
    Index(value = ["state"]),
    Index(value = ["sourcePackageId"]),
    Index(value = ["frontTerm"]),
    Index(value = ["contentId"]),
    Index(value = ["cardType"]),
  ],
)
data class SrsCardEntity(
  @PrimaryKey val cardId: String,
  val cardType: String = "WORD",
  val contentId: String = "",
  val frontTerm: String,
  val backPersianMeaning: String,
  val englishDefinition: String? = null,
  val normalizedLemma: String = "",
  val pos: String = "",
  val senseId: String = "sense-1",
  val phonetic: String?,
  val exampleSentence: String?,
  val contextSentenceEn: String? = null,
  val contextSentenceFa: String? = null,
  val sourcePackageId: String?,
  val sourcePackageTitle: String?,
  val sourceGlossaryId: String?,
  val sourceSentenceId: String?,
  val state: String,
  val repetitions: Int,
  val intervalDays: Int,
  val easeFactor: Double,
  val dueAtEpochMs: Long,
  val lastReviewedAtEpochMs: Long?,
  val createdAtEpochMs: Long,
)

@Entity(
  tableName = "srs_review_logs",
  foreignKeys = [
    ForeignKey(
      entity = SrsCardEntity::class,
      parentColumns = ["cardId"],
      childColumns = ["cardId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [
    Index(value = ["cardId"]),
    Index(value = ["reviewedAtEpochMs"]),
  ],
)
data class SrsReviewLogEntity(
  @PrimaryKey(autoGenerate = true) val logId: Long = 0L,
  val cardId: String,
  val qualityScore: Int,
  val previousIntervalDays: Int,
  val newIntervalDays: Int,
  val previousEaseFactor: Double,
  val newEaseFactor: Double,
  val reviewedAtEpochMs: Long,
)

@Entity(
  tableName = "learning_progress",
  indices = [
    Index(value = ["packageId"], unique = true),
    Index(value = ["updatedAtEpochMs"]),
  ],
)
data class LearningProgressEntity(
  @PrimaryKey val progressId: String,
  val packageId: String,
  val packageTitleSnapshot: String,
  val completedSentenceCount: Int,
  val totalSentenceCount: Int,
  val correctQuestionsCount: Int,
  val totalQuestionsCount: Int,
  val lastReadSentenceIndex: Int,
  val updatedAtEpochMs: Long,
)

@Entity(
  tableName = "user_vocabulary",
  indices = [
    Index(value = ["normalizedTerm"], unique = true),
    Index(value = ["sourcePackageId"]),
  ],
)
data class UserVocabularyEntity(
  @PrimaryKey val vocabId: String,
  val normalizedTerm: String,
  val displayTerm: String,
  val persianMeaning: String,
  val phonetic: String?,
  val notesFa: String?,
  val sourcePackageId: String?,
  val masteryLevel: Int,
  val savedAtEpochMs: Long,
)

@Entity(
  tableName = "reader_bookmarks",
  indices = [
    Index(value = ["packageId", "sentenceId"], unique = true),
    Index(value = ["createdAtEpochMs"]),
  ],
)
data class ReaderBookmarkEntity(
  @PrimaryKey val bookmarkId: String,
  val packageId: String,
  val packageTitleSnapshot: String,
  val sentenceId: String,
  val sentenceOrderIndex: Int,
  val noteFa: String?,
  val createdAtEpochMs: Long,
)
