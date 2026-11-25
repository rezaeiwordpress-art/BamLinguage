package com.example.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppTimeRefreshCoordinator
import com.example.core.common.TimeRefreshSource
import com.example.core.tts.TextToSpeechEngine
import com.example.domain.model.ContentPackageSummary
import com.example.domain.model.DashboardStats
import com.example.domain.model.LearningProgress
import com.example.domain.model.SrsCardState
import com.example.domain.model.UserVocabularyItem
import com.example.domain.repository.ContentRepository
import com.example.domain.repository.LearningRepository
import com.example.domain.repository.PreferencesRepository
import com.example.domain.srs.LearningAnalyticsCalculator
import com.example.domain.srs.LearningAnalyticsSummary
import com.example.domain.usecase.CreateSrsCardResult
import com.example.domain.usecase.CreateSrsCardUseCase
import com.example.domain.usecase.EnsureBundledSamplePackageUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomePackageProgressItem(
  val summary: ContentPackageSummary,
  val progress: LearningProgress?,
) {
  val completedSentences: Int
    get() = (progress?.completedSentenceCount ?: 0).coerceAtMost(summary.sentenceCount)

  val completionPercent: Int
    get() =
      if (summary.sentenceCount <= 0) 0
      else ((completedSentences * 100.0) / summary.sentenceCount).toInt().coerceIn(0, 100)
}

data class HomeUiState(
  val isLoading: Boolean = true,
  val stats: DashboardStats = DashboardStats(),
  val analytics: LearningAnalyticsSummary = LearningAnalyticsSummary(),
  val dailyGoalCards: Int = 20,
  val featuredPackage: ContentPackageSummary? = null,
  val continueReadingProgress: LearningProgress? = null,
  val recentPackages: List<HomePackageProgressItem> = emptyList(),
  val userVocabulary: List<UserVocabularyItem> = emptyList(),
  val feedbackMessage: String? = null,
) {
  val isNewUserEmptyState: Boolean
    get() =
      !isLoading &&
        analytics.totalWordCards == 0 &&
        analytics.totalSentenceCards == 0 &&
        analytics.reviewedTodayCount == 0 &&
        analytics.startedPackagesCount == 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
  contentRepository: ContentRepository,
  private val learningRepository: LearningRepository,
  private val preferencesRepository: PreferencesRepository,
  private val ensureBundledSamplePackageUseCase: EnsureBundledSamplePackageUseCase,
  private val createSrsCardUseCase: CreateSrsCardUseCase,
  private val ttsEngine: TextToSpeechEngine,
  private val timeRefreshSource: TimeRefreshSource = AppTimeRefreshCoordinator(),
) : ViewModel() {

  private val feedbackMessage = MutableStateFlow<String?>(null)

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

  private val learningDataFlow =
    combine(
      allCardsFlow,
      learningRepository.observeAllReviewLogs(),
      learningRepository.observeLearningProgress(),
      learningRepository.observeUserVocabulary(),
    ) { cards, logs, progress, userVocab ->
      HomeLearningSnapshot(cards, logs, progress, userVocab)
    }

  val uiState: StateFlow<HomeUiState> =
    combine(
      timedStatsFlow,
      contentRepository.observePackageSummaries(),
      learningDataFlow,
      preferencesRepository.userPreferences,
      feedbackMessage,
    ) { (now, stats), packages, learning, prefs, feedback ->
      val analytics =
        LearningAnalyticsCalculator.compute(
          cards = learning.cards,
          reviewLogs = learning.logs,
          progressList = learning.progress,
          packages = packages,
          nowEpochMs = now,
        )
      val progressById = learning.progress.associateBy { it.packageId }
      val continuePkg =
        packages.firstOrNull { it.contentPackage.packageId == analytics.continueReadingPackageId }
          ?: packages.firstOrNull()

      val recent =
        packages
          .map { pkg -> HomePackageProgressItem(pkg, progressById[pkg.contentPackage.packageId]) }
          .sortedByDescending { item ->
            item.progress?.updatedAtEpochMs ?: item.summary.contentPackage.importedAtEpochMs
          }

      HomeUiState(
        isLoading = false,
        stats = stats,
        analytics = analytics,
        dailyGoalCards = prefs.dailyGoalCards,
        featuredPackage = continuePkg,
        continueReadingProgress = continuePkg?.let { progressById[it.contentPackage.packageId] },
        recentPackages = recent,
        userVocabulary = learning.userVocab,
        feedbackMessage = feedback,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = HomeUiState(),
    )

  fun refreshBundledSample() {
    viewModelScope.launch {
      ensureBundledSamplePackageUseCase(forceReload = true)
    }
  }

  fun speakWord(word: String) {
    viewModelScope.launch {
      val prefs = preferencesRepository.userPreferences.first()
      ttsEngine.speakWord(
        word = word,
        speechRate = prefs.ttsSpeed,
        accent = prefs.ttsAccent,
        preferredVoiceName = prefs.preferredVoiceName,
      )
    }
  }

  fun promoteVocabularyToSrs(item: UserVocabularyItem) {
    viewModelScope.launch {
      val result =
        createSrsCardUseCase.createManualWordAndCard(
          term = item.displayTerm,
          persianMeaning = item.persianMeaning,
          englishDefinition = item.notesFa,
          pos = "word",
          contextSentence = null,
          packageTitle = "واژه‌نامه شخصی",
          addToSrs = true,
          nowEpochMs = timeRefreshSource.currentTimeMillis(),
        )
      feedbackMessage.value =
        when (result) {
          is CreateSrsCardResult.Created -> "واژه «${item.displayTerm}» به کارت‌های مرور اضافه شد."
          is CreateSrsCardResult.AlreadyExists -> "واژه «${item.displayTerm}» هم‌اکنون در کارت‌های مرور وجود دارد."
          null -> "افزودن واژه انجام نشد."
        }
    }
  }

  fun deleteVocabularyItem(vocabId: String) {
    viewModelScope.launch {
      learningRepository.deleteVocabularyItemById(vocabId)
      feedbackMessage.value = "واژه از واژه‌نامه شخصی حذف شد."
    }
  }

  fun dismissFeedback() {
    feedbackMessage.value = null
  }

  class Factory(
    private val contentRepository: ContentRepository,
    private val learningRepository: LearningRepository,
    private val preferencesRepository: PreferencesRepository,
    private val ensureBundledSamplePackageUseCase: EnsureBundledSamplePackageUseCase,
    private val createSrsCardUseCase: CreateSrsCardUseCase,
    private val ttsEngine: TextToSpeechEngine,
    private val timeRefreshSource: TimeRefreshSource = AppTimeRefreshCoordinator(),
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      HomeViewModel(
        contentRepository = contentRepository,
        learningRepository = learningRepository,
        preferencesRepository = preferencesRepository,
        ensureBundledSamplePackageUseCase = ensureBundledSamplePackageUseCase,
        createSrsCardUseCase = createSrsCardUseCase,
        ttsEngine = ttsEngine,
        timeRefreshSource = timeRefreshSource,
      ) as T
  }
}

private data class HomeLearningSnapshot(
  val cards: List<com.example.domain.model.SrsCard>,
  val logs: List<com.example.domain.model.SrsReviewLog>,
  val progress: List<LearningProgress>,
  val userVocab: List<UserVocabularyItem>,
)
