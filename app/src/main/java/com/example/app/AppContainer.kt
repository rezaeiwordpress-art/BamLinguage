package com.example.app

import android.content.Context
import com.example.content.importer.JsonContentPackageImporter
import com.example.content.validation.ContentIntegrityAnalyzer
import com.example.content.validation.ContentPackageValidator
import com.example.core.ai.AiService
import com.example.core.ai.DisabledOfflineAiProvider
import com.example.core.common.AppTimeRefreshCoordinator
import com.example.core.common.DefaultDispatcherProvider
import com.example.core.common.DispatcherProvider
import com.example.core.common.TimeRefreshSource
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.tts.AndroidTextToSpeechEngine
import com.example.core.tts.TextToSpeechEngine
import com.example.data.local.database.BamLinguageDatabase
import com.example.data.repository.DataStorePreferencesRepository
import com.example.data.repository.RoomContentRepository
import com.example.data.repository.RoomLearningRepository
import com.example.domain.repository.ContentRepository
import com.example.domain.repository.LearningRepository
import com.example.domain.repository.PreferencesRepository
import com.example.domain.srs.SrsScheduler
import com.example.domain.usecase.CreateSrsCardUseCase
import com.example.domain.usecase.DeleteSrsCardUseCase
import com.example.domain.usecase.DictionaryLookupUseCase
import com.example.domain.usecase.EnsureBundledSamplePackageUseCase
import com.example.domain.usecase.ImportContentPackageUseCase
import com.example.domain.usecase.ObserveReaderPackageUseCase
import com.example.domain.usecase.ResolveWordInSentenceUseCase
import com.example.domain.usecase.ReviewSrsCardUseCase
import com.example.domain.vocabulary.VocabularyResolutionEngine

interface AppContainer {
  val dispatchers: DispatcherProvider
  val database: BamLinguageDatabase
  val contentRepository: ContentRepository
  val learningRepository: LearningRepository
  val preferencesRepository: PreferencesRepository
  val ttsEngine: TextToSpeechEngine
  val aiService: AiService
  val srsScheduler: SrsScheduler
  val vocabularyResolutionEngine: VocabularyResolutionEngine
  val contentPackageValidator: ContentPackageValidator
  val contentIntegrityAnalyzer: ContentIntegrityAnalyzer
  val jsonContentPackageImporter: JsonContentPackageImporter
  val importContentPackageUseCase: ImportContentPackageUseCase
  val ensureBundledSamplePackageUseCase: EnsureBundledSamplePackageUseCase
  val dictionaryLookupUseCase: DictionaryLookupUseCase
  val resolveWordInSentenceUseCase: ResolveWordInSentenceUseCase
  val observeReaderPackageUseCase: ObserveReaderPackageUseCase
  val createSrsCardUseCase: CreateSrsCardUseCase
  val reviewSrsCardUseCase: ReviewSrsCardUseCase
  val deleteSrsCardUseCase: DeleteSrsCardUseCase
  val timeRefreshSource: TimeRefreshSource
}

class DefaultAppContainer(context: Context) : AppContainer {
  private val appContext = context.applicationContext

  override val dispatchers: DispatcherProvider = DefaultDispatcherProvider()

  override val timeRefreshSource: TimeRefreshSource by lazy {
    AppTimeRefreshCoordinator(dispatchers = dispatchers)
  }

  override val database: BamLinguageDatabase by lazy {
    BamLinguageDatabase.create(appContext)
  }

  private val preferencesDataStore: UserPreferencesDataStore by lazy {
    UserPreferencesDataStore(appContext)
  }

  override val contentRepository: ContentRepository by lazy {
    RoomContentRepository(database.contentDao(), dispatchers)
  }

  override val learningRepository: LearningRepository by lazy {
    RoomLearningRepository(database.learningDao(), dispatchers)
  }

  override val preferencesRepository: PreferencesRepository by lazy {
    DataStorePreferencesRepository(preferencesDataStore)
  }

  override val ttsEngine: TextToSpeechEngine by lazy {
    AndroidTextToSpeechEngine(appContext)
  }

  override val aiService: AiService by lazy {
    AiService(DisabledOfflineAiProvider())
  }

  override val srsScheduler: SrsScheduler = SrsScheduler()

  override val vocabularyResolutionEngine: VocabularyResolutionEngine = VocabularyResolutionEngine()

  override val contentPackageValidator: ContentPackageValidator = ContentPackageValidator()

  override val contentIntegrityAnalyzer: ContentIntegrityAnalyzer by lazy {
    ContentIntegrityAnalyzer(
      validator = contentPackageValidator,
      resolutionEngine = vocabularyResolutionEngine,
    )
  }

  override val jsonContentPackageImporter: JsonContentPackageImporter by lazy {
    JsonContentPackageImporter(
      contentRepository = contentRepository,
      validator = contentPackageValidator,
      integrityAnalyzer = contentIntegrityAnalyzer,
    )
  }

  override val importContentPackageUseCase: ImportContentPackageUseCase by lazy {
    ImportContentPackageUseCase(jsonContentPackageImporter)
  }

  override val ensureBundledSamplePackageUseCase: EnsureBundledSamplePackageUseCase by lazy {
    EnsureBundledSamplePackageUseCase(
      appContext = appContext,
      contentRepository = contentRepository,
      preferencesRepository = preferencesRepository,
      importer = jsonContentPackageImporter,
    )
  }

  override val dictionaryLookupUseCase: DictionaryLookupUseCase by lazy {
    DictionaryLookupUseCase(
      contentRepository = contentRepository,
      learningRepository = learningRepository,
    )
  }

  override val resolveWordInSentenceUseCase: ResolveWordInSentenceUseCase by lazy {
    ResolveWordInSentenceUseCase(
      contentRepository = contentRepository,
      engine = vocabularyResolutionEngine,
    )
  }

  override val observeReaderPackageUseCase: ObserveReaderPackageUseCase by lazy {
    ObserveReaderPackageUseCase(
      contentRepository = contentRepository,
      learningRepository = learningRepository,
      engine = vocabularyResolutionEngine,
    )
  }

  override val createSrsCardUseCase: CreateSrsCardUseCase by lazy {
    CreateSrsCardUseCase(learningRepository = learningRepository)
  }

  override val reviewSrsCardUseCase: ReviewSrsCardUseCase by lazy {
    ReviewSrsCardUseCase(
      learningRepository = learningRepository,
      scheduler = srsScheduler,
    )
  }

  override val deleteSrsCardUseCase: DeleteSrsCardUseCase by lazy {
    DeleteSrsCardUseCase(learningRepository = learningRepository)
  }
}
