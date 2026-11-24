package com.example.domain.srs

import com.example.domain.model.ReaderWordStatus
import com.example.domain.model.ReviewQuality
import com.example.domain.model.SrsCard
import com.example.domain.model.SrsCardState
import com.example.domain.model.SrsReviewLog
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Deterministic SM-2 spaced repetition scheduler with stable card identity helpers,
 * time-zone aware daily boundary calculation, and safe handling of backward clock changes.
 */
class SrsScheduler {

  data class ScheduleOutcome(
    val updatedCard: SrsCard,
    val reviewLog: SrsReviewLog,
  )

  fun scheduleNextReview(
    card: SrsCard,
    quality: ReviewQuality,
    nowEpochMs: Long,
  ): ScheduleOutcome {
    val safeNow = max(nowEpochMs, card.lastReviewedAtEpochMs ?: nowEpochMs)
    val q = quality.score
    val prevInterval = card.intervalDays
    val prevEase = card.easeFactor

    val nextEase =
      max(
        MIN_EASE_FACTOR,
        prevEase + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)),
      )

    val (nextRepetitions, nextIntervalDays, nextState) =
      when (quality) {
        ReviewQuality.AGAIN -> {
          Triple(0, 1, SrsCardState.RELEARNING)
        }
        ReviewQuality.HARD -> {
          val reps = card.repetitions + 1
          val interval =
            when {
              card.repetitions == 0 -> 1
              prevInterval <= 1 -> 2
              else -> max(prevInterval + 1, (prevInterval * 1.2).roundToInt())
            }
          val state = if (reps < 2) SrsCardState.LEARNING else SrsCardState.REVIEW
          Triple(reps, interval, state)
        }
        ReviewQuality.GOOD -> {
          val reps = card.repetitions + 1
          val interval =
            when (reps) {
              1 -> 1
              2 -> 6
              else -> max(prevInterval + 1, (prevInterval * nextEase).roundToInt())
            }
          val state = if (reps == 1) SrsCardState.LEARNING else SrsCardState.REVIEW
          Triple(reps, interval, state)
        }
        ReviewQuality.EASY -> {
          val reps = card.repetitions + 1
          val interval =
            when (reps) {
              1 -> 4
              2 -> 10
              else -> max(prevInterval + 2, (prevInterval * nextEase * 1.3).roundToInt())
            }
          Triple(reps, interval, SrsCardState.REVIEW)
        }
      }

    val nextDueEpochMs = safeNow + nextIntervalDays * MILLIS_PER_DAY

    val updatedCard =
      card.copy(
        state = nextState,
        repetitions = nextRepetitions,
        intervalDays = nextIntervalDays,
        easeFactor = nextEase,
        dueAtEpochMs = nextDueEpochMs,
        lastReviewedAtEpochMs = safeNow,
      )

    val log =
      SrsReviewLog(
        cardId = card.cardId,
        qualityScore = q,
        previousIntervalDays = prevInterval,
        newIntervalDays = nextIntervalDays,
        previousEaseFactor = prevEase,
        newEaseFactor = nextEase,
        reviewedAtEpochMs = safeNow,
      )

    return ScheduleOutcome(updatedCard = updatedCard, reviewLog = log)
  }

  fun previewIntervals(card: SrsCard, nowEpochMs: Long): Map<ReviewQuality, Int> =
    ReviewQuality.entries.associateWith { quality ->
      scheduleNextReview(card, quality, nowEpochMs).updatedCard.intervalDays
    }

  companion object {
    const val DEFAULT_EASE_FACTOR = 2.5
    const val MIN_EASE_FACTOR = 1.3
    const val MILLIS_PER_DAY = 86_400_000L

    /**
     * Computes time-dependent Reader display status without mutating persisted [SrsCard.state].
     */
    fun displayStatusFor(card: SrsCard?, nowEpochMs: Long): ReaderWordStatus {
      if (card == null) return ReaderWordStatus.NEW
      if (card.state != SrsCardState.NEW && card.dueAtEpochMs <= nowEpochMs) {
        return ReaderWordStatus.DUE
      }
      return when (card.state) {
        SrsCardState.NEW -> ReaderWordStatus.NEW
        SrsCardState.LEARNING,
        SrsCardState.RELEARNING -> ReaderWordStatus.LEARNING
        SrsCardState.REVIEW -> ReaderWordStatus.REVIEW
      }
    }

    fun endOfLocalDayEpochMs(
      nowEpochMs: Long,
      zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
      val localDate = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
      return localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
    }

    fun buildWordContentId(
      normalizedLemma: String,
      pos: String,
      senseId: String,
    ): String {
      val cleanLemma = normalizedLemma.trim().lowercase().ifBlank { "unknown" }
      val cleanPos = pos.trim().lowercase().ifBlank { "word" }
      val cleanSense = senseId.trim().lowercase().ifBlank { "sense-1" }
      return "word:$cleanLemma:$cleanPos:$cleanSense"
    }

    fun buildSentenceContentId(
      packageId: String?,
      sourceSentenceId: String?,
      englishText: String,
    ): String {
      val cleanText = englishText.trim().lowercase().replace(Regex("\\s+"), " ")
      return if (!packageId.isNullOrBlank() && !sourceSentenceId.isNullOrBlank()) {
        "sentence:${packageId.trim()}:${sourceSentenceId.trim()}"
      } else {
        "sentence:text:${cleanText.hashCode()}"
      }
    }
  }
}
