package com.example.domain.srs

import com.example.domain.model.ContentPackageSummary
import com.example.domain.model.LearningProgress
import com.example.domain.model.ReaderWordStatus
import com.example.domain.model.SrsCard
import com.example.domain.model.SrsCardState
import com.example.domain.model.SrsCardType
import com.example.domain.model.SrsReviewLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DailyActivityPoint(
  val epochDay: Long,
  val shortLabel: String,
  val reviewCount: Int,
  val correctCount: Int,
)

data class LearningAnalyticsSummary(
  val reviewedTodayCount: Int = 0,
  val reviewAccuracyPercent: Int? = null,
  val streakDays: Int = 0,
  val totalWordCards: Int = 0,
  val totalSentenceCards: Int = 0,
  val learnedVocabularyCount: Int = 0,
  val learningWordsCount: Int = 0,
  val dueWordsCount: Int = 0,
  val newWordsCount: Int = 0,
  val dueTotalCount: Int = 0,
  val newTotalCount: Int = 0,
  val startedPackagesCount: Int = 0,
  val completedPackagesCount: Int = 0,
  val totalCompletedSentences: Int = 0,
  val totalSentencesAcrossLibrary: Int = 0,
  val overallReadingPercent: Int = 0,
  val continueReadingPackageId: String? = null,
  val dailyActivity: List<DailyActivityPoint> = emptyList(),
)

object LearningAnalyticsCalculator {

  private val dayLabelFormatter = DateTimeFormatter.ofPattern("MM/dd", Locale.US)

  fun compute(
    cards: List<SrsCard>,
    reviewLogs: List<SrsReviewLog>,
    progressList: List<LearningProgress>,
    packages: List<ContentPackageSummary>,
    nowEpochMs: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): LearningAnalyticsSummary {
    val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
    val todayEpochDay = today.toEpochDay()

    val logsByDay =
      reviewLogs.groupBy { log ->
        Instant.ofEpochMilli(log.reviewedAtEpochMs).atZone(zoneId).toLocalDate().toEpochDay()
      }
    val reviewedToday = logsByDay[todayEpochDay]?.size ?: 0

    val accuracyPercent =
      if (reviewLogs.isEmpty()) null
      else {
        val passing = reviewLogs.count { it.qualityScore >= 3 }
        ((passing * 100.0) / reviewLogs.size).toInt().coerceIn(0, 100)
      }

    // Streak: consecutive local days ending today (or yesterday if not yet reviewed today) with >= 1 review or reading progress activity
    val activeDays =
      buildSet {
        addAll(logsByDay.keys)
        progressList.forEach { prog ->
          if (prog.completedSentenceCount > 0 || prog.correctQuestionsCount > 0) {
            add(Instant.ofEpochMilli(prog.updatedAtEpochMs).atZone(zoneId).toLocalDate().toEpochDay())
          }
        }
      }
    val streakDays = computeConsecutiveStreak(activeDays, today)

    val wordCards = cards.filter { it.cardType == SrsCardType.WORD }
    val sentenceCards = cards.filter { it.cardType == SrsCardType.SENTENCE }
    val learnedWords = wordCards.count { it.state == SrsCardState.REVIEW }
    val learningWords =
      wordCards.count { it.state == SrsCardState.LEARNING || it.state == SrsCardState.RELEARNING }
    val dueWords =
      wordCards.count { SrsScheduler.displayStatusFor(it, nowEpochMs) == ReaderWordStatus.DUE }
    val newWords = wordCards.count { it.state == SrsCardState.NEW }
    val dueTotal = cards.count { SrsScheduler.displayStatusFor(it, nowEpochMs) == ReaderWordStatus.DUE }
    val newTotal = cards.count { it.state == SrsCardState.NEW }

    val progressByPackageId = progressList.associateBy { it.packageId }
    val totalSentencesLibrary = packages.sumOf { it.sentenceCount }
    val completedSentences =
      packages.sumOf { summary ->
        val prog = progressByPackageId[summary.contentPackage.packageId]
        (prog?.completedSentenceCount ?: 0).coerceAtMost(summary.sentenceCount)
      }
    val overallReadingPercent =
      if (totalSentencesLibrary <= 0) 0
      else ((completedSentences * 100.0) / totalSentencesLibrary).toInt().coerceIn(0, 100)

    val startedCount = progressList.count { it.completedSentenceCount > 0 || it.correctQuestionsCount > 0 }
    val completedPackages =
      progressList.count { it.totalSentenceCount > 0 && it.completedSentenceCount >= it.totalSentenceCount }

    val availablePackageIds = packages.map { it.contentPackage.packageId }.toSet()
    val continuePackageId =
      progressList
        .filter { it.packageId in availablePackageIds && it.completedSentenceCount < it.totalSentenceCount }
        .maxByOrNull { it.updatedAtEpochMs }
        ?.packageId
        ?: progressList.firstOrNull { it.packageId in availablePackageIds }?.packageId
        ?: packages.firstOrNull()?.contentPackage?.packageId

    val dailyActivity =
      (6 downTo 0).map { offset ->
        val day = today.minusDays(offset.toLong())
        val epochDay = day.toEpochDay()
        val dayLogs = logsByDay[epochDay].orEmpty()
        DailyActivityPoint(
          epochDay = epochDay,
          shortLabel = day.format(dayLabelFormatter),
          reviewCount = dayLogs.size,
          correctCount = dayLogs.count { it.qualityScore >= 3 },
        )
      }

    return LearningAnalyticsSummary(
      reviewedTodayCount = reviewedToday,
      reviewAccuracyPercent = accuracyPercent,
      streakDays = streakDays,
      totalWordCards = wordCards.size,
      totalSentenceCards = sentenceCards.size,
      learnedVocabularyCount = learnedWords,
      learningWordsCount = learningWords,
      dueWordsCount = dueWords,
      newWordsCount = newWords,
      dueTotalCount = dueTotal,
      newTotalCount = newTotal,
      startedPackagesCount = startedCount,
      completedPackagesCount = completedPackages,
      totalCompletedSentences = completedSentences,
      totalSentencesAcrossLibrary = totalSentencesLibrary,
      overallReadingPercent = overallReadingPercent,
      continueReadingPackageId = continuePackageId,
      dailyActivity = dailyActivity,
    )
  }

  private fun computeConsecutiveStreak(
    activeEpochDays: Set<Long>,
    today: LocalDate,
  ): Int {
    if (activeEpochDays.isEmpty()) return 0
    val todayDay = today.toEpochDay()
    val startDay =
      when {
        todayDay in activeEpochDays -> todayDay
        (todayDay - 1) in activeEpochDays -> todayDay - 1
        else -> return 0
      }
    var count = 0
    var cursor = startDay
    while (cursor in activeEpochDays) {
      count++
      cursor--
    }
    return count
  }
}
