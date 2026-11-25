package com.example.feature.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.content.importer.PackageImportPreview
import com.example.content.validation.ContentIntegrityAnalyzer
import com.example.content.validation.ContentIntegrityReport
import com.example.core.common.CefrLevel
import com.example.core.common.ContentType
import com.example.core.tts.TextToSpeechEngine
import com.example.domain.model.ContentPackageBundle
import com.example.domain.model.ContentPackageSummary
import com.example.domain.model.GlossaryEntry
import com.example.domain.model.LearningProgress
import com.example.domain.model.PackageImportResult
import com.example.domain.model.UserVocabularyItem
import com.example.domain.repository.ContentRepository
import com.example.domain.repository.LearningRepository
import com.example.domain.repository.PreferencesRepository
import com.example.domain.usecase.CreateSrsCardResult
import com.example.domain.usecase.CreateSrsCardUseCase
import com.example.domain.usecase.DictionaryLookupUseCase
import com.example.domain.usecase.EnsureBundledSamplePackageUseCase
import com.example.domain.usecase.ImportContentPackageUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InspectedPackageDetails(
  val bundle: ContentPackageBundle,
  val integrityReport: ContentIntegrityReport,
)

data class LearnUiState(
  val packages: List<ContentPackageSummary> = emptyList(),
  val totalLibraryPackageCount: Int = 0,
  val progressByPackageId: Map<String, LearningProgress> = emptyMap(),
  val selectedLevel: CefrLevel? = null,
  val selectedType: ContentType? = null,
  val selectedTopic: String? = null,
  val availableTopics: List<String> = emptyList(),
  val packageSearchQuery: String = "",
  val dictionaryQuery: String = "",
  val dictionaryResults: List<GlossaryEntry> = emptyList(),
  val savedVocabularyMatches: List<UserVocabularyItem> = emptyList(),
  val allUserVocabulary: List<UserVocabularyItem> = emptyList(),
  val inspectedPackage: InspectedPackageDetails? = null,
  val pendingImportPreview: PackageImportPreview? = null,
  val pendingDeletePackage: ContentPackageSummary? = null,
  val importFeedbackMessage: String? = null,
  val isImportError: Boolean = false,
)

class LearnViewModel(
  private val contentRepository: ContentRepository,
  private val learningRepository: LearningRepository,
  private val preferencesRepository: PreferencesRepository,
  private val importContentPackageUseCase: ImportContentPackageUseCase,
  private val ensureBundledSamplePackageUseCase: EnsureBundledSamplePackageUseCase,
  private val dictionaryLookupUseCase: DictionaryLookupUseCase,
  private val createSrsCardUseCase: CreateSrsCardUseCase,
  private val contentIntegrityAnalyzer: ContentIntegrityAnalyzer,
  private val ttsEngine: TextToSpeechEngine,
) : ViewModel() {

  private val selectedLevel = MutableStateFlow<CefrLevel?>(null)
  private val selectedType = MutableStateFlow<ContentType?>(null)
  private val selectedTopic = MutableStateFlow<String?>(null)
  private val packageSearchQuery = MutableStateFlow("")
  private val dictionaryQuery = MutableStateFlow("")
  private val dictionaryResults = MutableStateFlow<List<GlossaryEntry>>(emptyList())
  private val savedVocabularyMatches = MutableStateFlow<List<UserVocabularyItem>>(emptyList())
  private val inspectedPackage = MutableStateFlow<InspectedPackageDetails?>(null)
  private val pendingImportPreview = MutableStateFlow<PackageImportPreview?>(null)
  private val pendingDeletePackage = MutableStateFlow<ContentPackageSummary?>(null)
  private val importFeedback = MutableStateFlow<Pair<String, Boolean>?>(null)
  private var dictionarySearchJob: Job? = null

  private val filterStateFlow =
    combine(selectedLevel, selectedType, selectedTopic, packageSearchQuery) {
      level,
      type,
      topic,
      pkgQuery ->
      LibraryFilters(level, type, topic, pkgQuery)
    }

  private val dictStateFlow =
    combine(dictionaryQuery, dictionaryResults, savedVocabularyMatches) {
      query,
      glossary,
      saved ->
      DictState(query, glossary, saved)
    }

  private val modalStateFlow =
    combine(inspectedPackage, pendingImportPreview, pendingDeletePackage, importFeedback) {
      inspected,
      preview,
      deleteTarget,
      feedback ->
      ModalState(inspected, preview, deleteTarget, feedback)
    }

  val uiState: StateFlow<LearnUiState> =
    combine(
      contentRepository.observePackageSummaries(),
      learningRepository.observeLearningProgress(),
      learningRepository.observeUserVocabulary(),
      combine(filterStateFlow, dictStateFlow, modalStateFlow) { filters, dict, modals ->
        Triple(filters, dict, modals)
      },
    ) { allPackages, progressList, userVocab, (filters, dict, modals) ->
      val distinctTopics =
        allPackages
          .map { it.contentPackage.topic.trim() }
          .filter { it.isNotEmpty() }
          .distinct()
          .sorted()

      val normalizedSearch = filters.packageSearchQuery.trim().lowercase()
      val filtered =
        allPackages.filter { summary ->
          val pkg = summary.contentPackage
          val levelOk = filters.level == null || pkg.level == filters.level
          val typeOk = filters.type == null || pkg.contentType == filters.type
          val topicOk =
            filters.topic == null || pkg.topic.equals(filters.topic, ignoreCase = true)
          val queryOk =
            normalizedSearch.isEmpty() ||
              pkg.title.lowercase().contains(normalizedSearch) ||
              pkg.titleFa.lowercase().contains(normalizedSearch) ||
              pkg.descriptionFa.lowercase().contains(normalizedSearch) ||
              pkg.topic.lowercase().contains(normalizedSearch)
          levelOk && typeOk && topicOk && queryOk
        }

      LearnUiState(
        packages = filtered,
        totalLibraryPackageCount = allPackages.size,
        progressByPackageId = progressList.associateBy { it.packageId },
        selectedLevel = filters.level,
        selectedType = filters.type,
        selectedTopic = filters.topic,
        availableTopics = distinctTopics,
        packageSearchQuery = filters.packageSearchQuery,
        dictionaryQuery = dict.query,
        dictionaryResults = dict.glossary,
        savedVocabularyMatches = dict.saved,
        allUserVocabulary = userVocab,
        inspectedPackage = modals.inspected,
        pendingImportPreview = modals.preview,
        pendingDeletePackage = modals.deleteTarget,
        importFeedbackMessage = modals.feedback?.first,
        isImportError = modals.feedback?.second ?: false,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = LearnUiState(),
    )

  fun selectLevel(level: CefrLevel?) {
    selectedLevel.value = level
  }

  fun selectType(type: ContentType?) {
    selectedType.value = type
  }

  fun selectTopic(topic: String?) {
    selectedTopic.value = if (selectedTopic.value == topic) null else topic
  }

  fun onPackageSearchQueryChanged(query: String) {
    packageSearchQuery.value = query
  }

  fun clearPackageFilters() {
    selectedLevel.value = null
    selectedType.value = null
    selectedTopic.value = null
    packageSearchQuery.value = ""
  }

  fun onDictionaryQueryChanged(query: String) {
    dictionaryQuery.value = query
    dictionarySearchJob?.cancel()
    if (query.trim().isEmpty()) {
      dictionaryResults.value = emptyList()
      savedVocabularyMatches.value = emptyList()
      return
    }
    dictionarySearchJob =
      viewModelScope.launch {
        val result = dictionaryLookupUseCase(query)
        dictionaryResults.value = result.glossaryMatches
        savedVocabularyMatches.value = result.savedVocabularyMatches
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

  fun addDictionaryEntryToSrs(entry: GlossaryEntry) {
    viewModelScope.launch {
      val result =
        createSrsCardUseCase.createWordCardFromGlossary(
          entry = entry,
          selectedSense = entry.senses.firstOrNull(),
          contextSentence = null,
          packageTitle = "واژه‌نامه آفلاین",
        )
      importFeedback.value =
        when (result) {
          is CreateSrsCardResult.Created ->
            ("واژه «${entry.term}» به کارت‌های مرور اضافه شد." to false)
          is CreateSrsCardResult.AlreadyExists ->
            ("واژه «${entry.term}» قبلاً در کارت‌های مرور ثبت شده است." to false)
        }
    }
  }

  fun promoteUserVocabularyToSrs(item: UserVocabularyItem) {
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
        )
      importFeedback.value =
        when (result) {
          is CreateSrsCardResult.Created ->
            ("واژه «${item.displayTerm}» به کارت‌های مرور اضافه شد." to false)
          is CreateSrsCardResult.AlreadyExists ->
            ("واژه «${item.displayTerm}» قبلاً در کارت‌های مرور وجود دارد." to false)
          null -> ("افزودن واژه انجام نشد." to true)
        }
    }
  }

  fun deleteUserVocabularyItem(vocabId: String) {
    viewModelScope.launch {
      learningRepository.deleteVocabularyItemById(vocabId)
      importFeedback.value = "واژه از واژه‌نامه شخصی حذف شد." to false
    }
  }

  fun inspectPackage(packageId: String) {
    viewModelScope.launch {
      val bundle = contentRepository.getPackageBundle(packageId) ?: return@launch
      val report = contentIntegrityAnalyzer.analyzeBundle(bundle)
      inspectedPackage.value = InspectedPackageDetails(bundle = bundle, integrityReport = report)
    }
  }

  fun dismissInspector() {
    inspectedPackage.value = null
  }

  fun requestImportPreview(rawJson: String) {
    viewModelScope.launch {
      pendingImportPreview.value = importContentPackageUseCase.preview(rawJson)
    }
  }

  fun dismissImportPreview() {
    pendingImportPreview.value = null
  }

  fun confirmPreviewImport() {
    val preview = pendingImportPreview.value as? PackageImportPreview.Ready ?: return
    if (!preview.canImport) return
    pendingImportPreview.value = null
    importJsonString(preview.rawJson)
  }

  fun importJsonString(rawJson: String) {
    viewModelScope.launch {
      when (val result = importContentPackageUseCase(rawJson)) {
        is PackageImportResult.Success -> {
          val actionLabel = if (result.wasUpgrade) "به‌روزرسانی (با حفظ سوابق SRS)" else "وارد"
          importFeedback.value =
            "بسته «${result.title}» (نسخه ${result.contentVersion}) با موفقیت $actionLabel شد (${result.sentenceCount} جمله، ${result.glossaryCount} واژه)." to
              false
        }
        is PackageImportResult.Failure -> {
          val details = result.errors.joinToString("\n• ") { "${it.fieldPath}: ${it.messageFa}" }
          importFeedback.value = "اعتبارسنجی بسته ناموفق بود:\n• $details" to true
        }
      }
    }
  }

  fun requestDeletePackage(summary: ContentPackageSummary) {
    pendingDeletePackage.value = summary
  }

  fun dismissDeleteConfirmation() {
    pendingDeletePackage.value = null
  }

  fun confirmDeletePackage() {
    val target = pendingDeletePackage.value ?: return
    pendingDeletePackage.value = null
    inspectedPackage.value = null
    viewModelScope.launch {
      contentRepository.deletePackage(target.contentPackage.packageId)
      importFeedback.value =
        "بسته «${target.contentPackage.title}» حذف شد. کارت‌ها و سوابق SRS شما حفظ گردیدند." to false
    }
  }

  fun reportFileReadFailure(messageFa: String) {
    importFeedback.value = messageFa to true
  }

  fun reloadBundledSample() {
    viewModelScope.launch {
      when (val result = ensureBundledSamplePackageUseCase(forceReload = true)) {
        is PackageImportResult.Success -> {
          importFeedback.value =
            "بسته‌های نمونه بام‌لینگویج با موفقیت بازنشانی شدند." to false
        }
        is PackageImportResult.Failure -> {
          val msg = result.errors.firstOrNull()?.messageFa ?: "خطا در بارگذاری بسته نمونه."
          importFeedback.value = msg to true
        }
        null -> Unit
      }
    }
  }

  fun dismissFeedback() {
    importFeedback.value = null
  }

  class Factory(
    private val contentRepository: ContentRepository,
    private val learningRepository: LearningRepository,
    private val preferencesRepository: PreferencesRepository,
    private val importContentPackageUseCase: ImportContentPackageUseCase,
    private val ensureBundledSamplePackageUseCase: EnsureBundledSamplePackageUseCase,
    private val dictionaryLookupUseCase: DictionaryLookupUseCase,
    private val createSrsCardUseCase: CreateSrsCardUseCase,
    private val contentIntegrityAnalyzer: ContentIntegrityAnalyzer,
    private val ttsEngine: TextToSpeechEngine,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      LearnViewModel(
        contentRepository = contentRepository,
        learningRepository = learningRepository,
        preferencesRepository = preferencesRepository,
        importContentPackageUseCase = importContentPackageUseCase,
        ensureBundledSamplePackageUseCase = ensureBundledSamplePackageUseCase,
        dictionaryLookupUseCase = dictionaryLookupUseCase,
        createSrsCardUseCase = createSrsCardUseCase,
        contentIntegrityAnalyzer = contentIntegrityAnalyzer,
        ttsEngine = ttsEngine,
      ) as T
  }
}

private data class LibraryFilters(
  val level: CefrLevel?,
  val type: ContentType?,
  val topic: String?,
  val packageSearchQuery: String,
)

private data class DictState(
  val query: String,
  val glossary: List<GlossaryEntry>,
  val saved: List<UserVocabularyItem>,
)

private data class ModalState(
  val inspected: InspectedPackageDetails?,
  val preview: PackageImportPreview?,
  val deleteTarget: ContentPackageSummary?,
  val feedback: Pair<String, Boolean>?,
)
