package com.example.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.ai.AiResult
import com.example.core.ai.AiService
import com.example.core.common.AppTimeRefreshCoordinator
import com.example.core.common.TimeRefreshSource
import com.example.core.common.TtsAccent
import com.example.core.datastore.UserPreferences
import com.example.core.tts.TextToSpeechEngine
import com.example.core.tts.TtsSpeechItem
import com.example.core.tts.TtsStatus
import com.example.core.tts.TtsVoiceOption
import com.example.domain.model.GlossaryEntry
import com.example.domain.model.GlossarySense
import com.example.domain.model.LearningProgress
import com.example.domain.model.ReaderBookmark
import com.example.domain.model.Sentence
import com.example.domain.model.SrsCardState
import com.example.domain.repository.LearningRepository
import com.example.domain.repository.PreferencesRepository
import com.example.domain.usecase.CreateSrsCardResult
import com.example.domain.usecase.CreateSrsCardUseCase
import com.example.domain.usecase.ObserveReaderPackageUseCase
import com.example.domain.usecase.ReaderPackageSnapshot
import com.example.domain.usecase.ResolveWordInSentenceUseCase
import com.example.domain.vocabulary.ContextualResolutionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderUiState(
  val isLoading: Boolean = true,
  val snapshot: ReaderPackageSnapshot? = null,
  val preferences: UserPreferences = UserPreferences(),
  val ttsStatus: TtsStatus = TtsStatus.Ready,
  val availableVoices: List<TtsVoiceOption> = emptyList(),
  val speakerVoiceMap: Map<String, String> = emptyMap(),
  val activeResolution: ContextualResolutionResult? = null,
  val answeredQuestions: Map<String, Int> = emptyMap(),
  val feedbackBanner: String? = null,
  val optionalAiMessage: String? = null,
)

class ReaderViewModel(
  private val packageId: String,
  observeReaderPackageUseCase: ObserveReaderPackageUseCase,
  private val resolveWordInSentenceUseCase: ResolveWordInSentenceUseCase,
  private val createSrsCardUseCase: CreateSrsCardUseCase,
  private val learningRepository: LearningRepository,
  private val preferencesRepository: PreferencesRepository,
  private val ttsEngine: TextToSpeechEngine,
  private val aiService: AiService,
  private val timeRefreshSource: TimeRefreshSource = AppTimeRefreshCoordinator(),
) : ViewModel() {

  private val activeResolution = MutableStateFlow<ContextualResolutionResult?>(null)
  private val pendingQuestionSelections = MutableStateFlow<Map<String, Int>>(emptyMap())
  private val speakerVoiceMap = MutableStateFlow<Map<String, String>>(emptyMap())
  private val feedbackBanner = MutableStateFlow<String?>(null)
  private val optionalAiMessage = MutableStateFlow<String?>(null)

  private val upcomingDeadlinesFlow =
    learningRepository.observeAllSrsCards()
      .map { cards ->
        cards.filter { it.state != SrsCardState.NEW }.map { it.dueAtEpochMs }.sorted()
      }
      .distinctUntilChanged()

  private val timeFlow = timeRefreshSource.observeRefreshTimestamps(upcomingDeadlinesFlow)

  private val persistedAnswersFlow =
    learningRepository.observeQuizAttemptsForPackage(packageId).map { attempts ->
      attempts.associate { it.questionId to it.selectedOptionIndex }
    }

  private val answeredQuestionsFlow =
    combine(persistedAnswersFlow, pendingQuestionSelections) { persisted, pending ->
      persisted + pending
    }

  val uiState: StateFlow<ReaderUiState> =
    combine(
      observeReaderPackageUseCase(packageId, timeFlow),
      preferencesRepository.userPreferences,
      ttsEngine.status,
      ttsEngine.availableVoices,
      combine(
        activeResolution,
        answeredQuestionsFlow,
        speakerVoiceMap,
        feedbackBanner,
        optionalAiMessage,
      ) { res, ans, spkMap, msg, aiMsg ->
        ReaderTransientState(res, ans, spkMap, msg, aiMsg)
      },
    ) { snapshot, prefs, tts, voices, transient ->
      val resolvedSpeakerMap =
        if (transient.speakerMap.isNotEmpty() || snapshot == null || voices.isEmpty()) {
          transient.speakerMap
        } else {
          val offlineFirstVoices = voices.sortedBy { it.isNetworkRequired }
          snapshot.bundle.speakers.mapIndexed { idx, speaker ->
            speaker.id to offlineFirstVoices[idx % offlineFirstVoices.size].name
          }.toMap()
        }

      ReaderUiState(
        isLoading = false,
        snapshot = snapshot,
        preferences = prefs,
        ttsStatus = tts,
        availableVoices = voices,
        speakerVoiceMap = resolvedSpeakerMap,
        activeResolution = transient.resolution,
        answeredQuestions = transient.answered,
        feedbackBanner = transient.feedback,
        optionalAiMessage = transient.aiMessage,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = ReaderUiState(),
    )

  fun onWordTapped(sentence: Sentence, charOffset: Int) {
    val bundle = uiState.value.snapshot?.bundle ?: return
    viewModelScope.launch {
      val resolved =
        resolveWordInSentenceUseCase(
          sentence = sentence,
          tappedOffset = charOffset,
          bundle = bundle,
        )
      activeResolution.value = resolved
      optionalAiMessage.value = null
    }
  }

  fun dismissVocabularySheet() {
    activeResolution.value = null
    optionalAiMessage.value = null
  }

  fun requestOptionalAiExplanation(term: String, sentenceText: String) {
    val config = uiState.value.preferences.aiConfiguration
    viewModelScope.launch {
      val result =
        aiService.requestUserInitiatedExplanation(
          prompt = "Explain '$term' in context: $sentenceText",
          configuration = config,
        )
      optionalAiMessage.value =
        when (result) {
          is AiResult.Success -> result.explanation
          is AiResult.Unavailable -> result.reasonFa
        }
    }
  }

  fun speakWord(word: String) {
    val state = uiState.value
    ttsEngine.speakWord(
      word = word,
      speechRate = state.preferences.ttsSpeed,
      accent = state.preferences.ttsAccent,
      preferredVoiceName = state.preferences.preferredVoiceName,
    )
  }

  fun speakSentence(sentence: Sentence) {
    val state = uiState.value
    val speakerVoice =
      sentence.speakerId?.let { state.speakerVoiceMap[it] } ?: state.preferences.preferredVoiceName
    ttsEngine.speakSentence(
      text = sentence.englishText,
      itemId = sentence.id,
      speechRate = state.preferences.ttsSpeed,
      accent = state.preferences.ttsAccent,
      preferredVoiceName = speakerVoice,
    )
  }

  fun speakAllParagraphs() {
    val state = uiState.value
    val bundle = state.snapshot?.bundle ?: return
    val items =
      bundle.sentences.map {
        TtsSpeechItem(itemId = it.id, text = it.englishText, speakerId = it.speakerId)
      }
    ttsEngine.speakParagraph(
      items = items,
      speechRate = state.preferences.ttsSpeed,
      accent = state.preferences.ttsAccent,
      speakerVoiceMap = state.speakerVoiceMap,
      defaultVoiceName = state.preferences.preferredVoiceName,
    )
  }

  fun pauseTts() = ttsEngine.pause()

  fun resumeTts() = ttsEngine.resume()

  fun stopTts() = ttsEngine.stop()

  fun setTtsAccent(accent: TtsAccent) {
    viewModelScope.launch { preferencesRepository.setTtsAccent(accent) }
  }

  fun setSpeakerVoice(speakerId: String, voiceName: String) {
    speakerVoiceMap.value = speakerVoiceMap.value + (speakerId to voiceName)
  }

  fun toggleTranslationsVisibility() {
    val current = uiState.value.preferences.showTranslationsInReader
    viewModelScope.launch { preferencesRepository.setShowTranslationsInReader(!current) }
  }

  fun addWordSenseToSrs(entry: GlossaryEntry, sense: GlossarySense?) {
    val state = uiState.value
    val contextSentence = state.activeResolution?.sentence
    val packageTitle = state.snapshot?.bundle?.contentPackage?.title
    val now = timeRefreshSource.currentTimeMillis()
    viewModelScope.launch {
      when (
        createSrsCardUseCase.createWordCardFromGlossary(
          entry = entry,
          selectedSense = sense,
          contextSentence = contextSentence,
          packageTitle = packageTitle,
          nowEpochMs = now,
        )
      ) {
        is CreateSrsCardResult.Created ->
          feedbackBanner.value = "واژه «${entry.term}» به کارت‌های مرور (SRS) اضافه شد."
        is CreateSrsCardResult.AlreadyExists ->
          feedbackBanner.value = "این معنی از «${entry.term}» قبلاً در کارت‌های مرور شما وجود دارد (جلوگیری از تکرار)."
      }
    }
  }

  fun saveManualWord(
    term: String,
    meaningFa: String,
    definitionEn: String,
    pos: String,
    addToSrs: Boolean,
  ) {
    val state = uiState.value
    val contextSentence = state.activeResolution?.sentence
    val packageTitle = state.snapshot?.bundle?.contentPackage?.title
    val now = timeRefreshSource.currentTimeMillis()
    viewModelScope.launch {
      val result =
        createSrsCardUseCase.createManualWordAndCard(
          term = term,
          persianMeaning = meaningFa,
          englishDefinition = definitionEn,
          pos = pos,
          contextSentence = contextSentence,
          packageTitle = packageTitle,
          addToSrs = addToSrs,
          nowEpochMs = now,
        )
      activeResolution.value = null
      feedbackBanner.value =
        when (result) {
          is CreateSrsCardResult.Created -> "واژه «$term» در واژه‌نامه شخصی و SRS ثبت شد."
          is CreateSrsCardResult.AlreadyExists -> "واژه «$term» در واژه‌نامه ذخیره شد (کارت SRS قبلاً وجود داشت)."
          null -> "واژه «$term» در واژه‌نامه شخصی ذخیره شد."
        }
    }
  }

  fun addSentenceToSrs(sentence: Sentence) {
    val packageTitle = uiState.value.snapshot?.bundle?.contentPackage?.title
    val now = timeRefreshSource.currentTimeMillis()
    viewModelScope.launch {
      when (
        createSrsCardUseCase.createSentenceCard(
          sentence = sentence,
          packageTitle = packageTitle,
          nowEpochMs = now,
        )
      ) {
        is CreateSrsCardResult.Created ->
          feedbackBanner.value = "جمله شماره ${sentence.orderIndex + 1} به کارت‌های مرور اضافه شد."
        is CreateSrsCardResult.AlreadyExists ->
          feedbackBanner.value = "این جمله قبلاً در کارت‌های مرور شما ثبت شده است."
      }
    }
  }

  fun toggleBookmark(sentence: Sentence) {
    val snap = uiState.value.snapshot ?: return
    val isBookmarked = snap.bookmarksBySentenceId.containsKey(sentence.id)
    val now = timeRefreshSource.currentTimeMillis()
    viewModelScope.launch {
      if (isBookmarked) {
        learningRepository.deleteBookmark(snap.bundle.contentPackage.packageId, sentence.id)
        feedbackBanner.value = "نشانک جمله حذف شد."
      } else {
        learningRepository.saveBookmark(
          ReaderBookmark(
            bookmarkId = "bm_${snap.bundle.contentPackage.packageId}_${sentence.sourceSentenceId}",
            packageId = snap.bundle.contentPackage.packageId,
            packageTitleSnapshot = snap.bundle.contentPackage.title,
            sentenceId = sentence.id,
            sentenceOrderIndex = sentence.orderIndex,
            noteFa = sentence.persianTranslation,
            createdAtEpochMs = now,
          )
        )
        feedbackBanner.value = "جمله شماره ${sentence.orderIndex + 1} نشانک‌گذاری شد."
      }
    }
  }

  fun markSentenceRead(sentence: Sentence) {
    val snap = uiState.value.snapshot ?: return
    val pkg = snap.bundle.contentPackage
    val validSentenceIds = snap.bundle.sentences.map { it.id }.toSet()
    val totalSentences = snap.bundle.sentences.size
    val totalQuestions = snap.bundle.questions.size
    val now = timeRefreshSource.currentTimeMillis()

    viewModelScope.launch {
      val latestProgress = learningRepository.observeProgressForPackage(pkg.packageId).first()
      val existingCompletedIds =
        (latestProgress?.completedSentenceIds ?: snap.progress?.completedSentenceIds.orEmpty())
          .intersect(validSentenceIds)
      val updatedCompletedIds = existingCompletedIds + sentence.id
      val nextCompleted = updatedCompletedIds.size.coerceAtMost(totalSentences)

      val latestAttempts = learningRepository.observeQuizAttemptsForPackage(pkg.packageId).first()
      val mergedAnswers =
        latestAttempts.associate { it.questionId to it.selectedOptionIndex } + pendingQuestionSelections.value
      val correctCount =
        snap.bundle.questions.count { q -> mergedAnswers[q.id] == q.correctOptionIndex }

      learningRepository.upsertProgress(
        LearningProgress(
          progressId = "prog_${pkg.packageId}",
          packageId = pkg.packageId,
          packageTitleSnapshot = pkg.title,
          completedSentenceCount = nextCompleted,
          totalSentenceCount = totalSentences,
          correctQuestionsCount = maxOf(latestProgress?.correctQuestionsCount ?: 0, correctCount),
          totalQuestionsCount = totalQuestions,
          lastReadSentenceIndex = sentence.orderIndex,
          updatedAtEpochMs = now,
          completedSentenceIds = updatedCompletedIds,
        )
      )
      feedbackBanner.value = "پیشرفت مطالعه: $nextCompleted از $totalSentences جمله ثبت شد."
    }
  }

  fun answerQuestion(questionId: String, selectedOptionIndex: Int) {
    val snap = uiState.value.snapshot ?: return
    val question = snap.bundle.questions.firstOrNull { it.id == questionId } ?: return
    val pkg = snap.bundle.contentPackage
    val isCorrect = selectedOptionIndex == question.correctOptionIndex
    val now = timeRefreshSource.currentTimeMillis()

    pendingQuestionSelections.update { it + (questionId to selectedOptionIndex) }

    viewModelScope.launch {
      learningRepository.saveQuizAttempt(
        packageId = pkg.packageId,
        questionId = questionId,
        selectedOptionIndex = selectedOptionIndex,
        isCorrect = isCorrect,
        nowEpochMs = now,
      )

      val latestAttempts = learningRepository.observeQuizAttemptsForPackage(pkg.packageId).first()
      val persistedMap = latestAttempts.associate { it.questionId to it.selectedOptionIndex }
      val mergedAnswers = persistedMap + pendingQuestionSelections.value
      val correctCount =
        snap.bundle.questions.count { q -> mergedAnswers[q.id] == q.correctOptionIndex }

      val latestProgress = learningRepository.observeProgressForPackage(pkg.packageId).first()
      learningRepository.upsertProgress(
        LearningProgress(
          progressId = "prog_${pkg.packageId}",
          packageId = pkg.packageId,
          packageTitleSnapshot = pkg.title,
          completedSentenceCount = latestProgress?.completedSentenceCount ?: snap.progress?.completedSentenceCount ?: 0,
          totalSentenceCount = snap.bundle.sentences.size,
          correctQuestionsCount = correctCount,
          totalQuestionsCount = snap.bundle.questions.size,
          lastReadSentenceIndex = latestProgress?.lastReadSentenceIndex ?: snap.progress?.lastReadSentenceIndex ?: 0,
          updatedAtEpochMs = now,
          completedSentenceIds = latestProgress?.completedSentenceIds ?: snap.progress?.completedSentenceIds.orEmpty(),
        )
      )

      pendingQuestionSelections.update { current ->
        if (current[questionId] == selectedOptionIndex) current - questionId else current
      }
    }
  }

  fun dismissFeedback() {
    feedbackBanner.value = null
  }

  override fun onCleared() {
    super.onCleared()
    ttsEngine.stop()
  }

  class Factory(
    private val packageId: String,
    private val observeReaderPackageUseCase: ObserveReaderPackageUseCase,
    private val resolveWordInSentenceUseCase: ResolveWordInSentenceUseCase,
    private val createSrsCardUseCase: CreateSrsCardUseCase,
    private val learningRepository: LearningRepository,
    private val preferencesRepository: PreferencesRepository,
    private val ttsEngine: TextToSpeechEngine,
    private val aiService: AiService,
    private val timeRefreshSource: TimeRefreshSource = AppTimeRefreshCoordinator(),
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      ReaderViewModel(
        packageId = packageId,
        observeReaderPackageUseCase = observeReaderPackageUseCase,
        resolveWordInSentenceUseCase = resolveWordInSentenceUseCase,
        createSrsCardUseCase = createSrsCardUseCase,
        learningRepository = learningRepository,
        preferencesRepository = preferencesRepository,
        ttsEngine = ttsEngine,
        aiService = aiService,
        timeRefreshSource = timeRefreshSource,
      ) as T
  }
}

private data class ReaderTransientState(
  val resolution: ContextualResolutionResult?,
  val answered: Map<String, Int>,
  val speakerMap: Map<String, String>,
  val feedback: String?,
  val aiMessage: String?,
)
