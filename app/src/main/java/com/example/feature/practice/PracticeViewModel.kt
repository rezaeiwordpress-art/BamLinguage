package com.example.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.TtsAccent
import com.example.core.datastore.UserPreferences
import com.example.core.tts.TextToSpeechEngine
import com.example.core.tts.TtsStatus
import com.example.core.tts.TtsVoiceOption
import com.example.domain.model.ReaderWordStatus
import com.example.domain.model.ReviewQuality
import com.example.domain.model.SrsCard
import com.example.domain.model.SrsCardType
import com.example.domain.model.SrsReviewLog
import com.example.domain.repository.LearningRepository
import com.example.domain.repository.PreferencesRepository
import com.example.domain.srs.SrsScheduler
import com.example.domain.usecase.DeleteSrsCardUseCase
import com.example.domain.usecase.ReviewSrsCardUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PracticeDeckFilter(val persianLabel: String) {
  ALL("همه"),
  DUE("سررسید"),
  WORD("واژه‌ها"),
  SENTENCE("جملات"),
}

data class ReviewSessionState(
  val queue: List<SrsCard> = emptyList(),
  val currentIndex: Int = 0,
  val isAnswerRevealed: Boolean = false,
  val reviewedCount: Int = 0,
  val ratingCounts: Map<ReviewQuality, Int> = emptyMap(),
  val isCompleted: Boolean = false,
) {
  val currentCard: SrsCard?
    get() = queue.getOrNull(currentIndex)
}

data class PracticeUiState(
  val allCards: List<SrsCard> = emptyList(),
  val dueQueue: List<SrsCard> = emptyList(),
  val newQueue: List<SrsCard> = emptyList(),
  val recentLogs: List<SrsReviewLog> = emptyList(),
  val deckFilter: PracticeDeckFilter = PracticeDeckFilter.ALL,
  val session: ReviewSessionState? = null,
  val intervalPreviews: Map<ReviewQuality, Int> = emptyMap(),
  val preferences: UserPreferences = UserPreferences(),
  val ttsStatus: TtsStatus = TtsStatus.Ready,
  val availableVoices: List<TtsVoiceOption> = emptyList(),
  val ttsTestInput: String = "She was running late and ran out of time at the station.",
  val feedbackMessage: String? = null,
) {
  val filteredDeck: List<SrsCard>
    get() {
      val now = System.currentTimeMillis()
      return when (deckFilter) {
        PracticeDeckFilter.ALL -> allCards
        PracticeDeckFilter.DUE ->
          allCards.filter { SrsScheduler.displayStatusFor(it, now) == ReaderWordStatus.DUE }
        PracticeDeckFilter.WORD -> allCards.filter { it.cardType == SrsCardType.WORD }
        PracticeDeckFilter.SENTENCE -> allCards.filter { it.cardType == SrsCardType.SENTENCE }
      }
    }
}

class PracticeViewModel(
  private val ttsEngine: TextToSpeechEngine,
  private val learningRepository: LearningRepository,
  private val preferencesRepository: PreferencesRepository,
  private val srsScheduler: SrsScheduler,
  private val reviewSrsCardUseCase: ReviewSrsCardUseCase,
  private val deleteSrsCardUseCase: DeleteSrsCardUseCase,
) : ViewModel() {

  private val deckFilter = MutableStateFlow(PracticeDeckFilter.ALL)
  private val activeSession = MutableStateFlow<ReviewSessionState?>(null)
  private val ttsInput =
    MutableStateFlow("She was running late and ran out of time at the station.")
  private val feedbackMessage = MutableStateFlow<String?>(null)

  val uiState: StateFlow<PracticeUiState> =
    combine(
      learningRepository.observeAllSrsCards(),
      learningRepository.observeRecentReviewLogs(30),
      preferencesRepository.userPreferences,
      ttsEngine.status,
      combine(ttsEngine.availableVoices, deckFilter, activeSession, ttsInput, feedbackMessage) {
        voices,
        filter,
        session,
        input,
        feedback ->
        PracticeTransient(voices, filter, session, input, feedback)
      },
    ) { allCards, logs, prefs, tts, transient ->
      val now = System.currentTimeMillis()
      val dueQueue =
        allCards.filter { SrsScheduler.displayStatusFor(it, now) == ReaderWordStatus.DUE }
      val newQueue =
        allCards.filter { SrsScheduler.displayStatusFor(it, now) == ReaderWordStatus.NEW }
      val currentCard = transient.session?.currentCard
      val previews =
        if (currentCard != null) {
          srsScheduler.previewIntervals(currentCard, now)
        } else {
          emptyMap()
        }

      PracticeUiState(
        allCards = allCards,
        dueQueue = dueQueue,
        newQueue = newQueue,
        recentLogs = logs,
        deckFilter = transient.filter,
        session = transient.session,
        intervalPreviews = previews,
        preferences = prefs,
        ttsStatus = tts,
        availableVoices = transient.voices,
        ttsTestInput = transient.ttsInput,
        feedbackMessage = transient.feedback,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = PracticeUiState(),
    )

  fun setDeckFilter(filter: PracticeDeckFilter) {
    deckFilter.value = filter
  }

  fun startReviewSession(includeNewCards: Boolean = true) {
    val state = uiState.value
    val dailyLimit = state.preferences.dailyGoalCards
    val newCardLimit = minOf(state.preferences.newCardsPerDay, maxOf(0, dailyLimit - state.dueQueue.size))
    val combined =
      buildList {
        addAll(state.dueQueue.take(dailyLimit))
        if (includeNewCards && newCardLimit > 0) {
          addAll(state.newQueue.take(newCardLimit))
        }
      }.distinctBy { it.cardId }

    if (combined.isEmpty()) {
      feedbackMessage.value = "کارت جدید یا سررسیدی برای مرور وجود ندارد. از خواننده واژه‌ها را اضافه کنید."
      return
    }

    activeSession.value =
      ReviewSessionState(
        queue = combined,
        currentIndex = 0,
        isAnswerRevealed = false,
        reviewedCount = 0,
        ratingCounts = emptyMap(),
        isCompleted = false,
      )
  }

  fun revealAnswer() {
    val session = activeSession.value ?: return
    activeSession.value = session.copy(isAnswerRevealed = true)
  }

  fun submitReview(quality: ReviewQuality) {
    val session = activeSession.value ?: return
    val card = session.currentCard ?: return

    viewModelScope.launch {
      reviewSrsCardUseCase(card = card, quality = quality)

      val updatedRatings =
        session.ratingCounts + (quality to ((session.ratingCounts[quality] ?: 0) + 1))
      val nextIndex = session.currentIndex + 1
      val isDone = nextIndex >= session.queue.size

      activeSession.value =
        session.copy(
          currentIndex = nextIndex,
          isAnswerRevealed = false,
          reviewedCount = session.reviewedCount + 1,
          ratingCounts = updatedRatings,
          isCompleted = isDone,
        )
    }
  }

  fun closeSession() {
    activeSession.value = null
  }

  fun deleteCard(cardId: String) {
    viewModelScope.launch {
      deleteSrsCardUseCase(cardId)
      feedbackMessage.value = "کارت مرور حذف شد (محتوای داستان/گفت‌وگو حفظ گردید)."
    }
  }

  fun speakText(text: String) {
    val prefs = uiState.value.preferences
    ttsEngine.speakSentence(
      text = text,
      speechRate = prefs.ttsSpeed,
      accent = prefs.ttsAccent,
      preferredVoiceName = prefs.preferredVoiceName,
    )
  }

  fun onTtsInputChanged(newText: String) {
    ttsInput.value = newText
  }

  fun speakSample() {
    speakText(uiState.value.ttsTestInput)
  }

  fun stopSpeaking() {
    ttsEngine.stop()
  }

  fun setTtsAccent(accent: TtsAccent) {
    viewModelScope.launch { preferencesRepository.setTtsAccent(accent) }
  }

  fun dismissFeedback() {
    feedbackMessage.value = null
  }

  class Factory(
    private val ttsEngine: TextToSpeechEngine,
    private val learningRepository: LearningRepository,
    private val preferencesRepository: PreferencesRepository,
    private val srsScheduler: SrsScheduler,
    private val reviewSrsCardUseCase: ReviewSrsCardUseCase,
    private val deleteSrsCardUseCase: DeleteSrsCardUseCase,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      PracticeViewModel(
        ttsEngine = ttsEngine,
        learningRepository = learningRepository,
        preferencesRepository = preferencesRepository,
        srsScheduler = srsScheduler,
        reviewSrsCardUseCase = reviewSrsCardUseCase,
        deleteSrsCardUseCase = deleteSrsCardUseCase,
      ) as T
  }
}

private data class PracticeTransient(
  val voices: List<TtsVoiceOption>,
  val filter: PracticeDeckFilter,
  val session: ReviewSessionState?,
  val ttsInput: String,
  val feedback: String?,
)
