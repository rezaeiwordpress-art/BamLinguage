package com.example.domain.model

enum class SrsCardType {
  WORD,
  SENTENCE,
}

enum class SrsCardState {
  NEW,
  LEARNING,
  REVIEW,
  RELEARNING,
}

/**
 * Time-dependent display classification for Reader vocabulary highlighting.
 * [DUE] is computed dynamically from [SrsCard.dueAtEpochMs] and NEVER overwrites [SrsCard.state].
 */
enum class ReaderWordStatus(val persianLabel: String) {
  NEW("جدید"),
  LEARNING("در حال یادگیری"),
  REVIEW("مرور شده"),
  DUE("سررسید مرور"),
}

enum class ReviewQuality(val score: Int, val persianLabel: String) {
  AGAIN(1, "دوباره"),
  HARD(3, "سخت"),
  GOOD(4, "خوب"),
  EASY(5, "آسان"),
}

data class SrsCard(
  val cardId: String,
  val cardType: SrsCardType = SrsCardType.WORD,
  val contentId: String = cardId,
  val frontTerm: String,
  val backPersianMeaning: String,
  val englishDefinition: String? = null,
  val normalizedLemma: String = frontTerm.trim().lowercase(),
  val pos: String = "",
  val senseId: String = "sense-1",
  val phonetic: String? = null,
  val exampleSentence: String? = null,
  val contextSentenceEn: String? = null,
  val contextSentenceFa: String? = null,
  val sourcePackageId: String? = null,
  val sourcePackageTitle: String? = null,
  val sourceGlossaryId: String? = null,
  val sourceSentenceId: String? = null,
  val state: SrsCardState = SrsCardState.NEW,
  val repetitions: Int = 0,
  val intervalDays: Int = 0,
  val easeFactor: Double = 2.5,
  val dueAtEpochMs: Long,
  val lastReviewedAtEpochMs: Long? = null,
  val createdAtEpochMs: Long,
)

data class SrsReviewLog(
  val logId: Long = 0L,
  val cardId: String,
  val qualityScore: Int,
  val previousIntervalDays: Int,
  val newIntervalDays: Int,
  val previousEaseFactor: Double,
  val newEaseFactor: Double,
  val reviewedAtEpochMs: Long,
)

data class LearningProgress(
  val progressId: String,
  val packageId: String,
  val packageTitleSnapshot: String,
  val completedSentenceCount: Int,
  val totalSentenceCount: Int,
  val correctQuestionsCount: Int,
  val totalQuestionsCount: Int,
  val lastReadSentenceIndex: Int,
  val updatedAtEpochMs: Long,
)

data class UserVocabularyItem(
  val vocabId: String,
  val normalizedTerm: String,
  val displayTerm: String,
  val persianMeaning: String,
  val phonetic: String?,
  val notesFa: String?,
  val sourcePackageId: String?,
  val masteryLevel: Int,
  val savedAtEpochMs: Long,
)

data class ReaderBookmark(
  val bookmarkId: String,
  val packageId: String,
  val packageTitleSnapshot: String,
  val sentenceId: String,
  val sentenceOrderIndex: Int,
  val noteFa: String?,
  val createdAtEpochMs: Long,
)

data class DashboardStats(
  val totalPackages: Int = 0,
  val totalSentences: Int = 0,
  val totalGlossaryEntries: Int = 0,
  val totalQuestions: Int = 0,
  val totalSrsCards: Int = 0,
  val dueSrsCards: Int = 0,
  val newSrsCards: Int = 0,
  val totalReviewLogs: Int = 0,
  val totalUserVocabulary: Int = 0,
  val totalBookmarks: Int = 0,
)
