package com.example.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.ai.AiAvailability
import com.example.core.ai.AiProviderType
import com.example.core.ai.AiService
import com.example.core.common.ThemeMode
import com.example.core.common.TtsAccent
import com.example.core.datastore.UserPreferences
import com.example.core.tts.TextToSpeechEngine
import com.example.core.tts.TtsStatus
import com.example.core.tts.TtsVoiceOption
import com.example.domain.model.PackageImportResult
import com.example.domain.repository.LearningRepository
import com.example.domain.repository.PreferencesRepository
import com.example.domain.usecase.EnsureBundledSamplePackageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
  val preferences: UserPreferences = UserPreferences(),
  val availableVoices: List<TtsVoiceOption> = emptyList(),
  val ttsStatus: TtsStatus = TtsStatus.Ready,
  val aiAvailability: AiAvailability = AiAvailability.DisabledByUser,
  val actionFeedbackMessage: String? = null,
)

class ProfileViewModel(
  private val preferencesRepository: PreferencesRepository,
  private val learningRepository: LearningRepository,
  private val ttsEngine: TextToSpeechEngine,
  private val ensureBundledSamplePackageUseCase: EnsureBundledSamplePackageUseCase,
  private val aiService: AiService,
) : ViewModel() {

  private val feedbackMessage = MutableStateFlow<String?>(null)

  val uiState: StateFlow<ProfileUiState> =
    combine(
      preferencesRepository.userPreferences,
      ttsEngine.availableVoices,
      ttsEngine.status,
      feedbackMessage,
    ) { prefs, voices, status, msg ->
      ProfileUiState(
        preferences = prefs,
        availableVoices = voices.sortedWith(compareBy<TtsVoiceOption> { it.isNetworkRequired }.thenBy { it.localeTag }),
        ttsStatus = status,
        aiAvailability = aiService.checkAvailability(prefs.aiConfiguration),
        actionFeedbackMessage = msg,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = ProfileUiState(),
    )

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
  }

  fun setTtsSpeed(speed: Float) {
    viewModelScope.launch { preferencesRepository.setTtsSpeed(speed) }
  }

  fun setTtsAccent(accent: TtsAccent) {
    viewModelScope.launch { preferencesRepository.setTtsAccent(accent) }
  }

  fun setPreferredVoiceName(voiceName: String?) {
    viewModelScope.launch { preferencesRepository.setPreferredVoiceName(voiceName) }
  }

  fun refreshVoices() {
    ttsEngine.refreshVoices()
  }

  fun setDailyGoalCards(goal: Int) {
    viewModelScope.launch { preferencesRepository.setDailyGoalCards(goal) }
  }

  fun setNewCardsPerDay(count: Int) {
    viewModelScope.launch { preferencesRepository.setNewCardsPerDay(count) }
  }

  fun setShowTranslationsInReader(show: Boolean) {
    viewModelScope.launch { preferencesRepository.setShowTranslationsInReader(show) }
  }

  fun setShowIpaInReader(show: Boolean) {
    viewModelScope.launch { preferencesRepository.setShowIpaInReader(show) }
  }

  fun setShowGrammarNotesInReader(show: Boolean) {
    viewModelScope.launch { preferencesRepository.setShowGrammarNotesInReader(show) }
  }

  fun setAutoPlaySrsAudio(autoPlay: Boolean) {
    viewModelScope.launch { preferencesRepository.setAutoPlaySrsAudio(autoPlay) }
  }

  fun setAiConfiguration(
    enabled: Boolean,
    providerType: AiProviderType,
    endpointUrl: String,
  ) {
    viewModelScope.launch {
      preferencesRepository.setAiConfiguration(
        enabled = enabled,
        providerType = providerType,
        endpointUrl = endpointUrl,
      )
    }
  }

  fun reloadSamplePackages() {
    viewModelScope.launch {
      feedbackMessage.value =
        when (val result = ensureBundledSamplePackageUseCase(forceReload = true)) {
          is PackageImportResult.Success ->
            "بسته‌های نمونه بام‌لینگویج با موفقیت بازنشانی شدند."
          is PackageImportResult.Failure ->
            result.errors.firstOrNull()?.messageFa ?: "خطا در بازنشانی بسته‌های نمونه."
          null -> "بسته‌های نمونه هم‌اکنون به‌روز هستند."
        }
    }
  }

  fun resetReadingProgressAndBookmarks() {
    viewModelScope.launch {
      learningRepository.resetReadingProgressAndBookmarks()
      feedbackMessage.value = "پیشرفت مطالعه و نشانک‌ها پاک‌سازی شدند (کارت‌های SRS حفظ گردیدند)."
    }
  }

  fun clearAllLearningData() {
    viewModelScope.launch {
      learningRepository.clearAllLearningData()
      feedbackMessage.value = "تمام کارت‌های مرور، تاریخچه، واژه‌نامه شخصی، پیشرفت و نشانک‌ها پاک شدند."
    }
  }

  fun dismissFeedback() {
    feedbackMessage.value = null
  }

  class Factory(
    private val preferencesRepository: PreferencesRepository,
    private val learningRepository: LearningRepository,
    private val ttsEngine: TextToSpeechEngine,
    private val ensureBundledSamplePackageUseCase: EnsureBundledSamplePackageUseCase,
    private val aiService: AiService,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      ProfileViewModel(
        preferencesRepository = preferencesRepository,
        learningRepository = learningRepository,
        ttsEngine = ttsEngine,
        ensureBundledSamplePackageUseCase = ensureBundledSamplePackageUseCase,
        aiService = aiService,
      ) as T
  }
}
