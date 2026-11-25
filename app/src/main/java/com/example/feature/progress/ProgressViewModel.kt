package com.example.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppTimeRefreshCoordinator
import com.example.core.common.TimeRefreshSource
import com.example.domain.model.ContentPackageSummary
import com.example.domain.model.DashboardStats
import com.example.domain.model.LearningProgress
import com.example.domain.model.ReaderBookmark
import com.example.domain.model.SrsCardState
import com.example.domain.model.SrsReviewLog
import com.example.domain.repository.ContentRepository
import com.example.domain.repository.LearningRepository
import com.example.domain.srs.LearningAnalyticsCalculator
import com.example.domain.srs.LearningAnalyticsSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ProgressUiState(
  val stats: DashboardStats = DashboardStats(),
  val analytics: LearningAnalyticsSummary = LearningAnalyticsSummary(),
  val packagesById: Map<String, ContentPackageSummary> = emptyMap(),
  val progressList: List<LearningProgress> = emptyList(),
  val bookmarks: List<ReaderBookmark> = emptyList(),
  val recentReviews: List<SrsReviewLog> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
  contentRepository: ContentRepository,
  learningRepository: LearningRepository,
  timeRefreshSource: TimeRefreshSource = AppTimeRefreshCoordinator(),
) : ViewModel() {

  private val allCardsFlow = learningRepository.observeAllSrsCards()

  private val upcomingDeadlinesFlow =
    allCardsFlow
      .map { cards ->
        cards.filter { it.state != SrsCardState.NEW }.map { it.dueAtEpochMs }.sorted()
      }
      .distinctUntilChanged()

  private val timeFlow = timeRefreshSource.observeRefreshTimestamps(upcomingDeadlinesFlow)

  private val timedStatsFlow =
    timeFlow.flatMapLatest { now ->
      learningRepository.observeDashboardStats(now).map { stats -> now to stats }
    }

  private val baseContentFlow =
    combine(
      timedStatsFlow,
      contentRepository.observePackageSummaries(),
      allCardsFlow,
      learningRepository.observeAllReviewLogs(),
    ) { (now, stats), packages, cards, allLogs ->
      ProgressBaseData(now, stats, packages, cards, allLogs)
    }

  val uiState: StateFlow<ProgressUiState> =
    combine(
      baseContentFlow,
      learningRepository.observeLearningProgress(),
      learningRepository.observeBookmarks(),
    ) { base, progress, bookmarks ->
      val analytics =
        LearningAnalyticsCalculator.compute(
          cards = base.cards,
          reviewLogs = base.allLogs,
          progressList = progress,
          packages = base.packages,
          nowEpochMs = base.nowEpochMs,
        )
      ProgressUiState(
        stats = base.stats,
        analytics = analytics,
        packagesById = base.packages.associateBy { it.contentPackage.packageId },
        progressList = progress.sortedByDescending { it.updatedAtEpochMs },
        bookmarks = bookmarks,
        recentReviews = base.allLogs.sortedByDescending { it.reviewedAtEpochMs }.take(10),
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = ProgressUiState(),
    )

  class Factory(
    private val contentRepository: ContentRepository,
    private val learningRepository: LearningRepository,
    private val timeRefreshSource: TimeRefreshSource = AppTimeRefreshCoordinator(),
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      ProgressViewModel(
        contentRepository = contentRepository,
        learningRepository = learningRepository,
        timeRefreshSource = timeRefreshSource,
      ) as T
  }
}

private data class ProgressBaseData(
  val nowEpochMs: Long,
  val stats: DashboardStats,
  val packages: List<ContentPackageSummary>,
  val cards: List<com.example.domain.model.SrsCard>,
  val allLogs: List<SrsReviewLog>,
)
