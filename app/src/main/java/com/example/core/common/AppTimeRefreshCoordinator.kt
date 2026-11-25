package com.example.core.common

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive

/**
 * Provides reactive wall-clock timestamps for dashboards, SRS due queues, and Reader badges.
 * Emits whenever the app returns to foreground, system time/timezone changes, or time passes
 * past a periodic / scheduled due-card / midnight boundary.
 */
interface TimeRefreshSource {
  fun currentTimeMillis(): Long

  val refreshTicks: Flow<Long>

  fun notifyForegrounded()

  fun notifyTimeChanged()

  fun observeRefreshTimestamps(
    upcomingDeadlinesFlow: Flow<List<Long>>? = null,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): Flow<Long>
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppTimeRefreshCoordinator(
  private val clock: () -> Long = { System.currentTimeMillis() },
  private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
  private val periodicIntervalMs: Long = 30_000L,
) : TimeRefreshSource {

  private val signalCounter = MutableStateFlow(0L)

  override fun currentTimeMillis(): Long = clock()

  override val refreshTicks: Flow<Long> =
    merge(
      signalCounter.map { clock() },
      flow {
        while (currentCoroutineContext().isActive) {
          delay(periodicIntervalMs.coerceAtLeast(1_000L))
          emit(clock())
        }
      }.flowOn(dispatchers.default),
    )

  override fun notifyForegrounded() {
    signalCounter.update { it + 1L }
  }

  override fun notifyTimeChanged() {
    signalCounter.update { it + 1L }
  }

  override fun observeRefreshTimestamps(
    upcomingDeadlinesFlow: Flow<List<Long>>?,
    zoneId: ZoneId,
  ): Flow<Long> {
    if (upcomingDeadlinesFlow == null) return refreshTicks

    val deadlineTrigger =
      combine(signalCounter, upcomingDeadlinesFlow) { _, deadlines -> deadlines }
        .transformLatest { deadlines ->
          while (currentCoroutineContext().isActive) {
            val now = clock()
            emit(now)
            val nextMidnight = nextMidnightEpochMs(now, zoneId)
            val earliestFuture =
              (deadlines + nextMidnight)
                .filter { it > now }
                .minOrNull()
            val waitMs =
              if (earliestFuture != null) {
                (earliestFuture - now + 25L).coerceIn(50L, periodicIntervalMs.coerceAtLeast(1_000L))
              } else {
                periodicIntervalMs.coerceAtLeast(1_000L)
              }
            delay(waitMs)
          }
        }
        .flowOn(dispatchers.default)

    return merge(signalCounter.map { clock() }, deadlineTrigger)
  }

  private fun nextMidnightEpochMs(nowEpochMs: Long, zoneId: ZoneId): Long =
    Instant.ofEpochMilli(nowEpochMs)
      .atZone(zoneId)
      .toLocalDate()
      .plusDays(1)
      .atStartOfDay(zoneId)
      .toInstant()
      .toEpochMilli()
}

class ManualTimeRefreshSource(
  initialTimeEpochMs: Long,
  private val clock: (() -> Long)? = null,
) : TimeRefreshSource {
  private val currentNow = MutableStateFlow(initialTimeEpochMs)
  private val manualPing = MutableStateFlow(0L)

  override fun currentTimeMillis(): Long = clock?.invoke() ?: currentNow.value

  override val refreshTicks: Flow<Long> =
    combine(currentNow, manualPing) { storedNow, _ ->
      clock?.invoke() ?: storedNow
    }

  fun advanceTo(newEpochMs: Long) {
    currentNow.value = newEpochMs
  }

  override fun notifyForegrounded() {
    manualPing.update { it + 1L }
  }

  override fun notifyTimeChanged() {
    manualPing.update { it + 1L }
  }

  override fun observeRefreshTimestamps(
    upcomingDeadlinesFlow: Flow<List<Long>>?,
    zoneId: ZoneId,
  ): Flow<Long> = refreshTicks
}
