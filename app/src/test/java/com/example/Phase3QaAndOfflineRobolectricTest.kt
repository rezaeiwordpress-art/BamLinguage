package com.example

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.content.importer.JsonContentPackageImporter
import com.example.content.importer.PackageImportPreview
import com.example.content.schema.ContentPackageJsonDto
import com.example.content.validation.ContentIntegrityAnalyzer
import com.example.content.validation.ContentPackageValidator
import com.example.core.common.DefaultDispatcherProvider
import com.example.core.common.ThemeMode
import com.example.core.common.TtsAccent
import com.example.core.datastore.UserPreferencesDataStore
import com.example.data.local.database.BamLinguageDatabase
import com.example.data.repository.RoomContentRepository
import com.example.data.repository.RoomLearningRepository
import com.example.domain.model.ImportValidationErrorCode
import com.example.domain.model.LearningProgress
import com.example.domain.model.PackageImportResult
import com.example.domain.model.ReaderBookmark
import com.example.domain.model.ReviewQuality
import com.example.domain.srs.SrsScheduler
import com.example.domain.usecase.CreateSrsCardResult
import com.example.domain.usecase.CreateSrsCardUseCase
import com.example.domain.usecase.EnsureBundledSamplePackageUseCase
import com.example.domain.usecase.ReviewSrsCardUseCase
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3QaAndOfflineRobolectricTest {

  private val strictJson =
    Json {
      ignoreUnknownKeys = false
      isLenient = false
      coerceInputValues = false
    }

  @Test
  fun `both bundled sample assets achieve 100 percent dictionary coverage with zero fatal or missing vocab warnings`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val analyzer = ContentIntegrityAnalyzer()
      for (assetPath in EnsureBundledSamplePackageUseCase.SAMPLE_ASSET_PATHS) {
        val rawJson = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        val dto = strictJson.decodeFromString(ContentPackageJsonDto.serializer(), rawJson)
        val report = analyzer.analyzeDto(dto)
        assertTrue("Expected 0 fatal errors for $assetPath: ${report.fatalErrors}", report.fatalErrors.isEmpty())
        assertTrue(
          "Expected 100% token coverage for $assetPath, uncovered=${report.uncoveredTokens}",
          report.uncoveredTokens.isEmpty(),
        )
        assertEquals("Expected 100% coverage for $assetPath", 100, report.coveragePercent)
        assertTrue("Expected complete coverage flag for $assetPath", report.hasCompleteDictionaryCoverage)
      }
    }

  @Test
  fun `importer rejects oversized and malformed json safely and preserves SRS during package v2 upgrade`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = BamLinguageDatabase.createInMemory(context)
      try {
        val dispatchers = DefaultDispatcherProvider()
        val contentRepo = RoomContentRepository(db.contentDao(), dispatchers)
        val learningRepo = RoomLearningRepository(db.learningDao(), dispatchers)
        val importer = JsonContentPackageImporter(contentRepo)
        val createCard = CreateSrsCardUseCase(learningRepo)
        val reviewCard = ReviewSrsCardUseCase(learningRepo, SrsScheduler())

        // Oversized (>5MB) rejection
        val hugeJson = "a".repeat(JsonContentPackageImporter.MAX_JSON_CHARS + 10)
        val hugePreview = importer.previewFromJson(hugeJson)
        assertTrue(hugePreview is PackageImportPreview.ParseFailed)

        // Malformed JSON rejection
        val badPreview = importer.previewFromJson("{not-valid-json")
        assertTrue(badPreview is PackageImportPreview.ParseFailed)
        assertEquals(
          ImportValidationErrorCode.INVALID_JSON_SYNTAX,
          (badPreview as PackageImportPreview.ParseFailed).errors.first().code,
        )

        // Import bundled v1 story
        val v1Json = context.assets.open("sample_packages/a_late_train_story.json").bufferedReader().use { it.readText() }
        val previewV1 = importer.previewFromJson(v1Json) as PackageImportPreview.Ready
        assertTrue(previewV1.canImport)
        assertEquals(100, previewV1.integrityReport.coveragePercent)
        assertTrue(importer.importFromJson(v1Json) is PackageImportResult.Success)

        val bundleV1 = contentRepo.getPackageBundle("story-b1-0001")!!
        val missEntry = bundleV1.glossaryEntries.first { it.lemma == "miss" }
        val cardResult =
          createCard.createWordCardFromGlossary(
            entry = missEntry,
            selectedSense = missEntry.senses.first(),
            contextSentence = bundleV1.sentences.first(),
            packageTitle = bundleV1.contentPackage.title,
            nowEpochMs = 1_000_000L,
          ) as CreateSrsCardResult.Created
        reviewCard(cardResult.card, ReviewQuality.GOOD, 1_001_000L)

        // Upgrade same packageId to version 2 -> preview shows isUpgrade = true, SRS & logs remain preserved
        val v2Json = v1Json.replace("\"version\": 1", "\"version\": 2").replace("\"A Late Train\"", "\"A Late Train Revised\"")
        val previewV2 = importer.previewFromJson(v2Json) as PackageImportPreview.Ready
        assertTrue(previewV2.isUpgrade)
        assertTrue(previewV2.canImport)

        val upgradeResult = importer.importFromJson(v2Json) as PackageImportResult.Success
        assertTrue(upgradeResult.wasUpgrade)
        assertEquals(2, upgradeResult.contentVersion)
        assertEquals("A Late Train Revised", contentRepo.getPackageById("story-b1-0001")?.title)

        // Verify SRS card and review log survived upgrade untouched
        val cardsAfterUpgrade = learningRepo.observeAllSrsCards().first()
        val logsAfterUpgrade = learningRepo.observeAllReviewLogs().first()
        assertEquals(1, cardsAfterUpgrade.size)
        assertEquals(1, logsAfterUpgrade.size)
        assertEquals(1, cardsAfterUpgrade.first().intervalDays)
      } finally {
        db.close()
      }
    }

  @Test
  fun `imported content, SRS reviews, progress, bookmarks, and preferences survive simulated process restart completely offline`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val dbFile = File(context.cacheDir, "restart_test_${System.nanoTime()}.db")
      val prefsFile = File(context.cacheDir, "restart_prefs_${System.nanoTime()}.preferences_pb")
      if (dbFile.exists()) dbFile.delete()
      if (prefsFile.exists()) prefsFile.delete()

      try {
        val dispatchers = DefaultDispatcherProvider()

        // --- Session 1: Populate offline DB & DataStore ---
        val db1 =
          Room.databaseBuilder(context, BamLinguageDatabase::class.java, dbFile.absolutePath)
            .addMigrations(
              BamLinguageDatabase.MIGRATION_1_2,
              BamLinguageDatabase.MIGRATION_2_3,
              BamLinguageDatabase.MIGRATION_3_4,
            )
            .allowMainThreadQueries()
            .build()
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ds1 = PreferenceDataStoreFactory.create(scope = scope1, produceFile = { prefsFile })
        val prefs1 = UserPreferencesDataStore(ds1)

        val contentRepo1 = RoomContentRepository(db1.contentDao(), dispatchers)
        val learningRepo1 = RoomLearningRepository(db1.learningDao(), dispatchers)
        val importer1 = JsonContentPackageImporter(contentRepo1, ContentPackageValidator())
        val createCard1 = CreateSrsCardUseCase(learningRepo1)
        val reviewCard1 = ReviewSrsCardUseCase(learningRepo1, SrsScheduler())

        val storyJson = context.assets.open("sample_packages/a_late_train_story.json").bufferedReader().use { it.readText() }
        assertTrue(importer1.importFromJson(storyJson) is PackageImportResult.Success)
        val bundle = contentRepo1.getPackageBundle("story-b1-0001")!!
        val missEntry = bundle.glossaryEntries.first { it.lemma == "miss" }
        val card =
          (createCard1.createWordCardFromGlossary(missEntry, missEntry.senses.first(), bundle.sentences.first(), bundle.contentPackage.title, 10_000L) as CreateSrsCardResult.Created).card
        reviewCard1(card, ReviewQuality.GOOD, 11_000L)
        learningRepo1.upsertProgress(
          LearningProgress(
            progressId = "prog_story-b1-0001",
            packageId = "story-b1-0001",
            packageTitleSnapshot = "A Late Train",
            completedSentenceCount = 3,
            totalSentenceCount = bundle.sentences.size,
            correctQuestionsCount = 1,
            totalQuestionsCount = bundle.questions.size,
            lastReadSentenceIndex = 2,
            updatedAtEpochMs = 12_000L,
          )
        )
        learningRepo1.saveBookmark(
          ReaderBookmark(
            bookmarkId = "bm_story_1",
            packageId = "story-b1-0001",
            packageTitleSnapshot = "A Late Train",
            sentenceId = bundle.sentences.first().id,
            sentenceOrderIndex = 0,
            noteFa = "سارا دوباره جا ماند",
            createdAtEpochMs = 12_500L,
          )
        )
        createCard1.createManualWordAndCard(
          term = "customword",
          persianMeaning = "واژه دلخواه",
          englishDefinition = "A saved test word",
          pos = "noun",
          contextSentence = null,
          packageTitle = "Personal",
          addToSrs = false,
        )

        prefs1.setThemeMode(ThemeMode.DARK)
        prefs1.setTtsAccent(TtsAccent.UK)
        prefs1.setNewCardsPerDay(15)
        prefs1.setAutoPlaySrsAudio(true)
        prefs1.setShowIpaInReader(false)
        prefs1.setShowGrammarNotesInReader(false)

        // Ensure flushed and shut down Session 1
        assertEquals(ThemeMode.DARK, prefs1.preferencesFlow.first().themeMode)
        db1.close()
        scope1.cancel()

        // --- Session 2: Fresh DB + DataStore instances (simulated cold process restart) ---
        val db2 =
          Room.databaseBuilder(context, BamLinguageDatabase::class.java, dbFile.absolutePath)
            .addMigrations(
              BamLinguageDatabase.MIGRATION_1_2,
              BamLinguageDatabase.MIGRATION_2_3,
              BamLinguageDatabase.MIGRATION_3_4,
            )
            .allowMainThreadQueries()
            .build()
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ds2 = PreferenceDataStoreFactory.create(scope = scope2, produceFile = { prefsFile })
        val prefs2 = UserPreferencesDataStore(ds2)

        val contentRepo2 = RoomContentRepository(db2.contentDao(), dispatchers)
        val learningRepo2 = RoomLearningRepository(db2.learningDao(), dispatchers)

        assertNotNull(contentRepo2.getPackageBundle("story-b1-0001"))
        assertEquals(1, learningRepo2.observeAllSrsCards().first().size)
        assertEquals(1, learningRepo2.observeAllReviewLogs().first().size)
        assertEquals(3, learningRepo2.observeLearningProgress().first().first().completedSentenceCount)
        assertEquals(1, learningRepo2.observeBookmarks().first().size)
        assertEquals(1, learningRepo2.observeUserVocabulary().first().size)

        val reloadedPrefs = prefs2.preferencesFlow.first()
        assertEquals(ThemeMode.DARK, reloadedPrefs.themeMode)
        assertEquals(TtsAccent.UK, reloadedPrefs.ttsAccent)
        assertEquals(15, reloadedPrefs.newCardsPerDay)
        assertTrue(reloadedPrefs.autoPlaySrsAudio)
        assertFalse(reloadedPrefs.showIpaInReader)
        assertFalse(reloadedPrefs.showGrammarNotesInReader)
        assertFalse(reloadedPrefs.aiEnabled)

        // Verify Data Management resets behave selectively
        learningRepo2.resetReadingProgressAndBookmarks()
        assertEquals(0, learningRepo2.observeLearningProgress().first().size)
        assertEquals(0, learningRepo2.observeBookmarks().first().size)
        assertEquals(1, learningRepo2.observeAllSrsCards().first().size)

        learningRepo2.clearAllLearningData()
        assertEquals(0, learningRepo2.observeAllSrsCards().first().size)
        assertEquals(0, learningRepo2.observeUserVocabulary().first().size)
        assertNotNull(contentRepo2.getPackageBundle("story-b1-0001"))

        db2.close()
        scope2.cancel()
      } finally {
        dbFile.delete()
        prefsFile.delete()
      }
    }
}
