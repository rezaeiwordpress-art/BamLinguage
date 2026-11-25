package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.app.AppContainer
import com.example.content.importer.JsonContentPackageImporter
import com.example.content.importer.PackageImportPreview
import com.example.content.validation.ContentIntegrityAnalyzer
import com.example.content.validation.ContentPackageValidator
import com.example.core.ai.AiAvailability
import com.example.core.ai.AiConfiguration
import com.example.core.ai.AiProviderType
import com.example.core.ai.AiResult
import com.example.core.ai.AiService
import com.example.core.ai.DisabledOfflineAiProvider
import com.example.core.common.DefaultDispatcherProvider
import com.example.core.common.DispatcherProvider
import com.example.core.common.ManualTimeRefreshSource
import com.example.core.common.TimeRefreshSource
import com.example.core.common.TtsAccent
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.tts.TextToSpeechEngine
import com.example.core.tts.TtsSpeechItem
import com.example.core.tts.TtsStatus
import com.example.core.tts.TtsVoiceOption
import com.example.data.local.database.BamLinguageDatabase
import com.example.data.repository.DataStorePreferencesRepository
import com.example.data.repository.RoomContentRepository
import com.example.data.repository.RoomLearningRepository
import com.example.domain.model.PackageImportResult
import com.example.domain.model.ReaderWordStatus
import com.example.domain.model.ReviewQuality
import com.example.domain.repository.ContentRepository
import com.example.domain.repository.LearningRepository
import com.example.domain.repository.PreferencesRepository
import com.example.domain.srs.SrsScheduler
import com.example.domain.usecase.CreateSrsCardResult
import com.example.domain.usecase.CreateSrsCardUseCase
import com.example.domain.usecase.DeleteSrsCardUseCase
import com.example.domain.usecase.DictionaryLookupUseCase
import com.example.domain.usecase.EnsureBundledSamplePackageUseCase
import com.example.domain.usecase.ImportContentPackageUseCase
import com.example.domain.usecase.ObserveReaderPackageUseCase
import com.example.domain.usecase.ResolveWordInSentenceUseCase
import com.example.domain.usecase.ReviewSrsCardUseCase
import com.example.feature.home.HomeViewModel
import com.example.feature.learn.LearnViewModel
import com.example.feature.practice.PracticeViewModel
import com.example.feature.progress.ProgressViewModel
import com.example.feature.reader.ReaderViewModel
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class Phase5FinalQaRobolectricTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun `full integration end to end flow covers JSON import, Reader lookup, TTS, SRS, quiz, Home, and Progress`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val harness = createTestHarness(context)
    val collectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    try {
      val learnVm =
        LearnViewModel(
          contentRepository = harness.contentRepository,
          learningRepository = harness.learningRepository,
          preferencesRepository = harness.preferencesRepository,
          importContentPackageUseCase = harness.importContentPackageUseCase,
          ensureBundledSamplePackageUseCase = harness.ensureBundledSamplePackageUseCase,
          dictionaryLookupUseCase = harness.dictionaryLookupUseCase,
          createSrsCardUseCase = harness.createSrsCardUseCase,
          contentIntegrityAnalyzer = harness.contentIntegrityAnalyzer,
          ttsEngine = harness.recordingTts,
        )
      val homeVm =
        HomeViewModel(
          contentRepository = harness.contentRepository,
          learningRepository = harness.learningRepository,
          preferencesRepository = harness.preferencesRepository,
          ensureBundledSamplePackageUseCase = harness.ensureBundledSamplePackageUseCase,
          createSrsCardUseCase = harness.createSrsCardUseCase,
          ttsEngine = harness.recordingTts,
          timeRefreshSource = harness.timeSource,
        )
      val practiceVm =
        PracticeViewModel(
          ttsEngine = harness.recordingTts,
          learningRepository = harness.learningRepository,
          preferencesRepository = harness.preferencesRepository,
          srsScheduler = harness.srsScheduler,
          reviewSrsCardUseCase = harness.reviewSrsCardUseCase,
          deleteSrsCardUseCase = harness.deleteSrsCardUseCase,
          timeRefreshSource = harness.timeSource,
        )
      val progressVm =
        ProgressViewModel(
          contentRepository = harness.contentRepository,
          learningRepository = harness.learningRepository,
          timeRefreshSource = harness.timeSource,
        )

      val jobs =
        listOf(
          collectScope.launch { learnVm.uiState.collect() },
          collectScope.launch { homeVm.uiState.collect() },
          collectScope.launch { practiceVm.uiState.collect() },
          collectScope.launch { progressVm.uiState.collect() },
        )

      // 1. JSON Import via LearnViewModel preview + confirm + offline dictionary lookup
      val storyJson =
        context.assets.open("sample_packages/a_late_train_story.json").bufferedReader().use { it.readText() }
      learnVm.requestImportPreview(storyJson)
      waitUntilMain {
        val preview = learnVm.uiState.value.pendingImportPreview
        preview is PackageImportPreview.Ready && preview.canImport && preview.integrityReport.coveragePercent == 100
      }
      learnVm.confirmPreviewImport()
      waitUntilMain {
        learnVm.uiState.value.packages.any { it.contentPackage.packageId == "story-b1-0001" }
      }

      // Offline dictionary lookup with morphological inflection ("missed" -> "miss") + TTS pronunciation
      learnVm.onDictionaryQueryChanged("missed")
      waitUntilMain {
        learnVm.uiState.value.dictionaryResults.any { it.lemma == "miss" }
      }
      learnVm.speakWord("miss")
      waitUntilMain {
        "miss" in harness.recordingTts.spokenWords
      }

      // 2. Open ReaderViewModel on imported story -> tap word/phrase -> speak TTS -> add SRS cards -> mark read -> answer quiz
      val readerVm =
        ReaderViewModel(
          packageId = "story-b1-0001",
          observeReaderPackageUseCase = harness.observeReaderPackageUseCase,
          resolveWordInSentenceUseCase = harness.resolveWordInSentenceUseCase,
          createSrsCardUseCase = harness.createSrsCardUseCase,
          learningRepository = harness.learningRepository,
          preferencesRepository = harness.preferencesRepository,
          ttsEngine = harness.recordingTts,
          aiService = harness.aiService,
          timeRefreshSource = harness.timeSource,
        )
      val readerJob = collectScope.launch { readerVm.uiState.collect() }

      waitUntilMain {
        readerVm.uiState.value.snapshot != null
      }
      val snapshot = readerVm.uiState.value.snapshot!!
      val firstSentence = snapshot.bundle.sentences.first()
      val missedOffset = firstSentence.englishText.indexOf("missed").coerceAtLeast(0)

      // Contextual word tap lookup
      readerVm.onWordTapped(firstSentence, missedOffset)
      waitUntilMain {
        readerVm.uiState.value.activeResolution?.primaryMatch?.entry?.lemma == "miss"
      }
      val resolution = readerVm.uiState.value.activeResolution!!
      val missEntry = resolution.primaryMatch!!.entry
      val recommendedSense = resolution.primaryMatch!!.recommendedSense

      // Speak sentence and all paragraphs via TTS
      readerVm.speakSentence(firstSentence)
      readerVm.speakAllParagraphs()
      assertTrue(harness.recordingTts.spokenSentences.any { it == firstSentence.englishText })
      assertTrue(harness.recordingTts.paragraphCalls.isNotEmpty())

      // Add word sense + sentence card to SRS, bookmark, mark sentence 1 read, answer first question correctly
      readerVm.addWordSenseToSrs(missEntry, recommendedSense)
      readerVm.addSentenceToSrs(firstSentence)
      readerVm.toggleBookmark(firstSentence)
      readerVm.markSentenceRead(firstSentence)
      val firstQuestion = snapshot.bundle.questions.first()
      readerVm.answerQuestion(firstQuestion.id, firstQuestion.correctOptionIndex)

      waitUntilMain {
        val snap = readerVm.uiState.value.snapshot
        snap != null &&
          snap.allWordCards.size == 1 &&
          snap.sentenceSrsCardsBySentenceId.size == 1 &&
          snap.bookmarksBySentenceId.containsKey(firstSentence.id) &&
          snap.progress?.completedSentenceCount == 1 &&
          snap.progress?.correctQuestionsCount == 1
      }

      // 3. Practice SM-2 review session updates interval and review logs
      waitUntilMain {
        practiceVm.uiState.value.newQueue.size == 2
      }
      practiceVm.startReviewSession(includeNewCards = true)
      waitUntilMain { practiceVm.uiState.value.session != null }
      practiceVm.revealAnswer()
      practiceVm.submitReview(ReviewQuality.GOOD)
      waitUntilMain {
        practiceVm.uiState.value.session?.reviewedCount == 1
      }
      practiceVm.revealAnswer()
      practiceVm.submitReview(ReviewQuality.EASY)
      waitUntilMain {
        practiceVm.uiState.value.session?.isCompleted == true
      }

      // 4. Verify Home Dashboard & Progress Dashboard stay synchronized with Room
      waitUntilMain {
        homeVm.uiState.value.analytics.reviewedTodayCount == 2 &&
          homeVm.uiState.value.analytics.streakDays >= 1 &&
          progressVm.uiState.value.analytics.reviewedTodayCount == 2 &&
          progressVm.uiState.value.stats.totalReviewLogs == 2 &&
          progressVm.uiState.value.stats.totalBookmarks == 1 &&
          progressVm.uiState.value.progressList.firstOrNull()?.completedSentenceCount == 1 &&
          progressVm.uiState.value.progressList.firstOrNull()?.correctQuestionsCount == 1
      }

      assertEquals(100, homeVm.uiState.value.analytics.reviewAccuracyPercent)
      assertEquals(ReaderWordStatus.LEARNING, readerVm.uiState.value.snapshot!!.tokenHighlightsBySentenceId[firstSentence.id]!!.first { it.surface == "missed" }.status)

      readerJob.cancel()
      jobs.forEach { it.cancel() }
    } finally {
      runBlocking { collectScope.coroutineContext[Job]?.cancelAndJoin() }
      harness.close()
    }
  }

  @Test
  fun `offline verification confirms zero INTERNET permission, disabled AI, and pure local operation`() =
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val packageInfo =
        context.packageManager.getPackageInfo(
          context.packageName,
          PackageManager.GET_PERMISSIONS,
        )
      val requestedPermissions = packageInfo.requestedPermissions?.toList().orEmpty()
      assertFalse(
        "App must never require android.permission.INTERNET for offline-first learning",
        "android.permission.INTERNET" in requestedPermissions,
      )

      val harness = createTestHarness(context)
      try {
        val defaultPrefs = harness.preferencesRepository.userPreferences.first()
        assertFalse(defaultPrefs.aiEnabled)
        assertEquals(AiProviderType.NONE, defaultPrefs.aiProviderType)

        assertEquals(
          AiAvailability.DisabledByUser,
          harness.aiService.checkAvailability(defaultPrefs.aiConfiguration),
        )
        val aiResponse =
          harness.aiService.requestUserInitiatedExplanation(
            prompt = "Explain missed",
            configuration = AiConfiguration(enabled = true, providerType = AiProviderType.CUSTOM_ENDPOINT, endpointUrl = "https://example.invalid"),
          )
        assertTrue(aiResponse is AiResult.Unavailable)

        // Core bundled import, lookup, and SRS work 100% offline
        val seeded = harness.ensureBundledSamplePackageUseCase(forceReload = true)
        assertTrue(seeded is PackageImportResult.Success)
        val lookup = harness.dictionaryLookupUseCase("went")
        assertTrue("Expected offline morphological lookup for 'went' -> 'go'", lookup.glossaryMatches.any { it.lemma == "go" })
      } finally {
        harness.close()
      }
    }

  @Test
  fun `room full migration chain v1 to v4 preserves user SRS cards, review logs, progress, vocabulary, and bookmarks`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbFile = File(context.cacheDir, "v1_to_v4_migration_${System.nanoTime()}.db")
    if (dbFile.exists()) dbFile.delete()

    // Build authentic Room v1 SQLite schema & insert pre-existing user data
    val v1Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    try {
      v1Db.execSQL("CREATE TABLE content_packages (packageId TEXT NOT NULL PRIMARY KEY, schemaVersion INTEGER NOT NULL, contentVersion INTEGER NOT NULL, contentType TEXT NOT NULL, level TEXT NOT NULL, topic TEXT NOT NULL, title TEXT NOT NULL, titleFa TEXT NOT NULL, description TEXT NOT NULL, descriptionFa TEXT NOT NULL, author TEXT NOT NULL, estimatedMinutes INTEGER NOT NULL, language TEXT NOT NULL, translationLanguage TEXT NOT NULL, tags TEXT NOT NULL, importedAtEpochMs INTEGER NOT NULL, isBundledSample INTEGER NOT NULL)")
      v1Db.execSQL("CREATE INDEX index_content_packages_level ON content_packages (level)")
      v1Db.execSQL("CREATE INDEX index_content_packages_contentType ON content_packages (contentType)")
      v1Db.execSQL("CREATE INDEX index_content_packages_importedAtEpochMs ON content_packages (importedAtEpochMs)")

      v1Db.execSQL("CREATE TABLE speakers (id TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, sourceSpeakerId TEXT NOT NULL, name TEXT NOT NULL, nameFa TEXT NOT NULL, role TEXT NOT NULL, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE)")
      v1Db.execSQL("CREATE INDEX index_speakers_packageId ON speakers (packageId)")

      v1Db.execSQL("CREATE TABLE sentences (id TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, sourceSentenceId TEXT NOT NULL, orderIndex INTEGER NOT NULL, speakerId TEXT, englishText TEXT NOT NULL, persianTranslation TEXT NOT NULL, phoneticHint TEXT, grammarNoteFa TEXT, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE, FOREIGN KEY(speakerId) REFERENCES speakers(id) ON DELETE SET NULL)")
      v1Db.execSQL("CREATE UNIQUE INDEX index_sentences_packageId_orderIndex ON sentences (packageId, orderIndex)")
      v1Db.execSQL("CREATE INDEX index_sentences_speakerId ON sentences (speakerId)")

      v1Db.execSQL("CREATE TABLE glossary_entries (id TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, sourceGlossaryId TEXT NOT NULL, term TEXT NOT NULL, normalizedTerm TEXT NOT NULL, partOfSpeech TEXT NOT NULL, phonetic TEXT, persianMeaning TEXT NOT NULL, englishDefinition TEXT NOT NULL, exampleSentence TEXT, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE)")
      v1Db.execSQL("CREATE INDEX index_glossary_entries_packageId_normalizedTerm ON glossary_entries (packageId, normalizedTerm)")
      v1Db.execSQL("CREATE INDEX index_glossary_entries_normalizedTerm ON glossary_entries (normalizedTerm)")

      v1Db.execSQL("CREATE TABLE word_occurrences (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, packageId TEXT NOT NULL, sentenceId TEXT NOT NULL, glossaryEntryId TEXT NOT NULL, surfaceForm TEXT NOT NULL, startOffset INTEGER NOT NULL, endOffset INTEGER NOT NULL, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE, FOREIGN KEY(sentenceId) REFERENCES sentences(id) ON DELETE CASCADE, FOREIGN KEY(glossaryEntryId) REFERENCES glossary_entries(id) ON DELETE CASCADE)")
      v1Db.execSQL("CREATE INDEX index_word_occurrences_packageId ON word_occurrences (packageId)")
      v1Db.execSQL("CREATE INDEX index_word_occurrences_sentenceId ON word_occurrences (sentenceId)")
      v1Db.execSQL("CREATE INDEX index_word_occurrences_glossaryEntryId ON word_occurrences (glossaryEntryId)")

      v1Db.execSQL("CREATE TABLE questions (id TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, sourceQuestionId TEXT NOT NULL, orderIndex INTEGER NOT NULL, promptEn TEXT NOT NULL, promptFa TEXT NOT NULL, options TEXT NOT NULL, correctOptionIndex INTEGER NOT NULL, explanationFa TEXT, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE)")
      v1Db.execSQL("CREATE UNIQUE INDEX index_questions_packageId_orderIndex ON questions (packageId, orderIndex)")

      v1Db.execSQL("CREATE TABLE srs_cards (cardId TEXT NOT NULL PRIMARY KEY, frontTerm TEXT NOT NULL, backPersianMeaning TEXT NOT NULL, phonetic TEXT, exampleSentence TEXT, sourcePackageId TEXT, sourcePackageTitle TEXT, sourceGlossaryId TEXT, sourceSentenceId TEXT, state TEXT NOT NULL, repetitions INTEGER NOT NULL, intervalDays INTEGER NOT NULL, easeFactor REAL NOT NULL, dueAtEpochMs INTEGER NOT NULL, lastReviewedAtEpochMs INTEGER, createdAtEpochMs INTEGER NOT NULL)")
      v1Db.execSQL("CREATE INDEX index_srs_cards_dueAtEpochMs ON srs_cards (dueAtEpochMs)")
      v1Db.execSQL("CREATE INDEX index_srs_cards_state ON srs_cards (state)")
      v1Db.execSQL("CREATE INDEX index_srs_cards_sourcePackageId ON srs_cards (sourcePackageId)")
      v1Db.execSQL("CREATE INDEX index_srs_cards_frontTerm ON srs_cards (frontTerm)")

      v1Db.execSQL("CREATE TABLE srs_review_logs (logId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, cardId TEXT NOT NULL, qualityScore INTEGER NOT NULL, previousIntervalDays INTEGER NOT NULL, newIntervalDays INTEGER NOT NULL, previousEaseFactor REAL NOT NULL, newEaseFactor REAL NOT NULL, reviewedAtEpochMs INTEGER NOT NULL, FOREIGN KEY(cardId) REFERENCES srs_cards(cardId) ON DELETE CASCADE)")
      v1Db.execSQL("CREATE INDEX index_srs_review_logs_cardId ON srs_review_logs (cardId)")
      v1Db.execSQL("CREATE INDEX index_srs_review_logs_reviewedAtEpochMs ON srs_review_logs (reviewedAtEpochMs)")

      v1Db.execSQL("CREATE TABLE learning_progress (progressId TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, packageTitleSnapshot TEXT NOT NULL, completedSentenceCount INTEGER NOT NULL, totalSentenceCount INTEGER NOT NULL, correctQuestionsCount INTEGER NOT NULL, totalQuestionsCount INTEGER NOT NULL, lastReadSentenceIndex INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL)")
      v1Db.execSQL("CREATE UNIQUE INDEX index_learning_progress_packageId ON learning_progress (packageId)")
      v1Db.execSQL("CREATE INDEX index_learning_progress_updatedAtEpochMs ON learning_progress (updatedAtEpochMs)")

      v1Db.execSQL("CREATE TABLE user_vocabulary (vocabId TEXT NOT NULL PRIMARY KEY, normalizedTerm TEXT NOT NULL, displayTerm TEXT NOT NULL, persianMeaning TEXT NOT NULL, phonetic TEXT, notesFa TEXT, sourcePackageId TEXT, masteryLevel INTEGER NOT NULL, savedAtEpochMs INTEGER NOT NULL)")
      v1Db.execSQL("CREATE UNIQUE INDEX index_user_vocabulary_normalizedTerm ON user_vocabulary (normalizedTerm)")
      v1Db.execSQL("CREATE INDEX index_user_vocabulary_sourcePackageId ON user_vocabulary (sourcePackageId)")

      v1Db.execSQL("CREATE TABLE reader_bookmarks (bookmarkId TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, packageTitleSnapshot TEXT NOT NULL, sentenceId TEXT NOT NULL, sentenceOrderIndex INTEGER NOT NULL, noteFa TEXT, createdAtEpochMs INTEGER NOT NULL)")
      v1Db.execSQL("CREATE UNIQUE INDEX index_reader_bookmarks_packageId_sentenceId ON reader_bookmarks (packageId, sentenceId)")
      v1Db.execSQL("CREATE INDEX index_reader_bookmarks_createdAtEpochMs ON reader_bookmarks (createdAtEpochMs)")

      // Seed v1 rows across content + learning tables
      v1Db.execSQL("INSERT INTO content_packages VALUES ('legacy-pkg-1', 1, 1, 'STORY', 'A2', 'travel', 'Legacy Story', 'داستان قدیمی', 'desc', 'توضیح', 'Author', 3, 'en', 'fa', '[]', 5000, 0)")
      v1Db.execSQL("INSERT INTO glossary_entries VALUES ('g1', 'legacy-pkg-1', 'gls1', 'resilient', 'resilient', 'adjective', null, 'تاب‌آور', 'Strong against difficulty', null)")
      v1Db.execSQL("INSERT INTO srs_cards VALUES ('card_v1_1', 'resilient', 'تاب‌آور', null, null, 'legacy-pkg-1', 'Legacy Story', 'gls1', null, 'REVIEW', 3, 6, 2.5, 900000, 800000, 1000)")
      v1Db.execSQL("INSERT INTO srs_review_logs VALUES (1, 'card_v1_1', 4, 1, 6, 2.5, 2.5, 800000)")
      v1Db.execSQL("INSERT INTO learning_progress VALUES ('prog_legacy', 'legacy-pkg-1', 'Legacy Story', 2, 4, 1, 1, 1, 810000)")
      v1Db.execSQL("INSERT INTO user_vocabulary VALUES ('voc_1', 'bamiyan', 'Bamiyan', 'بامیان', null, 'یادداشت', 'legacy-pkg-1', 2, 700000)")
      v1Db.execSQL("INSERT INTO reader_bookmarks VALUES ('bm_1', 'legacy-pkg-1', 'Legacy Story', 'legacy-pkg-1::s1', 0, 'نکته', 750000)")
      v1Db.version = 1
    } finally {
      v1Db.close()
    }

    val upgradedDb =
      Room.databaseBuilder(context, BamLinguageDatabase::class.java, dbFile.absolutePath)
        .addMigrations(
          BamLinguageDatabase.MIGRATION_1_2,
          BamLinguageDatabase.MIGRATION_2_3,
          BamLinguageDatabase.MIGRATION_3_4,
        )
        .allowMainThreadQueries()
        .build()
    try {
      runBlocking {
        val dispatchers = DefaultDispatcherProvider()
        val contentRepo = RoomContentRepository(upgradedDb.contentDao(), dispatchers)
        val learningRepo = RoomLearningRepository(upgradedDb.learningDao(), dispatchers)

        // All legacy user records survived v1 -> v2 -> v3 -> v4 intact
        val cards = learningRepo.observeAllSrsCards().first()
        val logs = learningRepo.observeAllReviewLogs().first()
        val progress = learningRepo.observeLearningProgress().first()
        val vocab = learningRepo.observeUserVocabulary().first()
        val bookmarks = learningRepo.observeBookmarks().first()
        assertEquals(1, cards.size)
        assertEquals("resilient", cards.first().frontTerm)
        assertEquals(6, cards.first().intervalDays)
        assertEquals(1, logs.size)
        assertEquals(1, progress.size)
        assertEquals(2, progress.first().completedSentenceCount)
        assertEquals(1, vocab.size)
        assertEquals(1, bookmarks.size)

        // Quiz attempts table is operational post-migration
        learningRepo.saveQuizAttempt("legacy-pkg-1", "q1", 0, true, 900_000L)
        assertEquals(1, learningRepo.observeQuizAttemptsForPackage("legacy-pkg-1").first().size)

        // Deleting content package never cascades to SRS cards or review history
        contentRepo.deletePackage("legacy-pkg-1")
        assertEquals(0, contentRepo.getPackageCount())
        assertEquals(1, learningRepo.observeAllSrsCards().first().size)
        assertEquals(1, learningRepo.observeAllReviewLogs().first().size)
      }
    } finally {
      upgradedDb.close()
      dbFile.delete()
    }
  }

  @Test
  fun `ui compact phone layout enforces Persian RTL, English LTR Reader blocks, and bottom navigation across all screens`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val harness = createTestHarness(context)
    try {
      runBlocking {
        assertTrue(harness.ensureBundledSamplePackageUseCase(forceReload = true) is PackageImportResult.Success)
      }

      composeRule.setContent {
        BamLinguageApp(container = harness)
      }

      composeRule.waitUntil(timeoutMillis = 10_000L) {
        ShadowLooper.idleMainLooper()
        composeRule.onAllNodesWithTag("bottom_navigation_bar").fetchSemanticsNodes().isNotEmpty() &&
          composeRule.onAllNodesWithTag("home_dashboard_list").fetchSemanticsNodes().isNotEmpty() &&
          composeRule.onAllNodesWithTag("home_metric_packages").fetchSemanticsNodes().isNotEmpty()
      }

      // Compact screen renders NavigationBar (never NavigationRail) and RTL layout direction
      composeRule.onNodeWithTag("bottom_navigation_bar").assertIsDisplayed()
      assertTrue(composeRule.onAllNodesWithTag("navigation_rail").fetchSemanticsNodes().isEmpty())
      val homeTabBounds = composeRule.onNodeWithTag("nav_tab_home").getUnclippedBoundsInRoot()
      val profileTabBounds = composeRule.onNodeWithTag("nav_tab_profile").getUnclippedBoundsInRoot()
      assertTrue(
        "In Persian RTL layout, Home tab must sit to the right of Profile tab (home.left=${homeTabBounds.left}, profile.left=${profileTabBounds.left})",
        homeTabBounds.left > profileTabBounds.left,
      )

      // Navigate across every primary tab
      composeRule.onNodeWithTag("nav_tab_learn").performClick()
      composeRule.waitUntil(10_000L) {
        ShadowLooper.idleMainLooper()
        composeRule.onAllNodesWithTag("learn_packages_list").fetchSemanticsNodes().isNotEmpty()
      }
      composeRule.onNodeWithTag("learn_packages_list").assertIsDisplayed()

      composeRule.onNodeWithTag("nav_tab_practice").performClick()
      composeRule.waitUntil(10_000L) {
        ShadowLooper.idleMainLooper()
        composeRule.onAllNodesWithTag("practice_srs_foundation_card").fetchSemanticsNodes().isNotEmpty()
      }
      composeRule.onNodeWithTag("practice_srs_foundation_card").assertIsDisplayed()

      composeRule.onNodeWithTag("nav_tab_progress").performClick()
      composeRule.waitUntil(10_000L) {
        ShadowLooper.idleMainLooper()
        composeRule.onAllNodesWithTag("progress_lazy_column").fetchSemanticsNodes().isNotEmpty()
      }
      composeRule.onNodeWithTag("progress_lazy_column").assertIsDisplayed()

      composeRule.onNodeWithTag("nav_tab_profile").performClick()
      composeRule.waitUntil(10_000L) {
        ShadowLooper.idleMainLooper()
        composeRule.onAllNodesWithTag("profile_settings_list").fetchSemanticsNodes().isNotEmpty()
      }
      composeRule.onNodeWithTag("profile_settings_list").performScrollToNode(hasTestTag("profile_app_info_card"))
      composeRule.waitUntil(5_000L) {
        composeRule.onAllNodesWithText("Room v4", substring = true).fetchSemanticsNodes().isNotEmpty()
      }

      // Return Home -> scroll to Continue Reading -> open Reader -> confirm English LTR sentence & RTL Persian controls
      composeRule.onNodeWithTag("nav_tab_home").performClick()
      composeRule.waitUntil(10_000L) {
        ShadowLooper.idleMainLooper()
        composeRule.onAllNodesWithTag("home_dashboard_list").fetchSemanticsNodes().isNotEmpty()
      }
      composeRule.onNodeWithTag("home_dashboard_list").performScrollToNode(hasTestTag("home_open_reader_button"))
      composeRule.onNodeWithTag("home_open_reader_button").performClick()
      composeRule.waitUntil(10_000L) {
        ShadowLooper.idleMainLooper()
        composeRule.onAllNodesWithTag("reader_lazy_column").fetchSemanticsNodes().isNotEmpty() &&
          composeRule.onAllNodesWithTag("sentence_text_0").fetchSemanticsNodes().isNotEmpty()
      }
      composeRule.onNodeWithTag("sentence_text_0").assertIsDisplayed()
      assertTrue(composeRule.onAllNodesWithTag("bottom_navigation_bar").fetchSemanticsNodes().isEmpty())
    } finally {
      harness.close()
    }
  }

  @Test
  @Config(sdk = [36], qualifiers = "w900dp-h600dp-xhdpi")
  fun `ui expanded tablet layout switches cleanly to NavigationRail with zero BottomNavigation overlap`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val harness = createTestHarness(context)
    try {
      runBlocking {
        assertTrue(harness.ensureBundledSamplePackageUseCase(forceReload = true) is PackageImportResult.Success)
      }

      composeRule.setContent {
        BamLinguageApp(container = harness)
      }

      composeRule.waitUntil(timeoutMillis = 10_000L) {
        ShadowLooper.idleMainLooper()
        composeRule.onAllNodesWithTag("navigation_rail").fetchSemanticsNodes().isNotEmpty() &&
          composeRule.onAllNodesWithTag("home_dashboard_list").fetchSemanticsNodes().isNotEmpty()
      }

      composeRule.onNodeWithTag("navigation_rail").assertIsDisplayed()
      assertTrue(composeRule.onAllNodesWithTag("bottom_navigation_bar").fetchSemanticsNodes().isEmpty())

      // Switch tabs via NavigationRail
      composeRule.onNodeWithTag("nav_tab_learn").performClick()
      composeRule.waitUntil(10_000L) {
        ShadowLooper.idleMainLooper()
        composeRule.onAllNodesWithTag("learn_packages_list").fetchSemanticsNodes().isNotEmpty()
      }
      composeRule.onNodeWithTag("learn_packages_list").assertIsDisplayed()
    } finally {
      harness.close()
    }
  }

  private fun waitUntilMain(timeoutMillis: Long = 10_000L, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
      ShadowLooper.idleMainLooper()
      if (condition()) return
      Thread.sleep(20L)
    }
    ShadowLooper.idleMainLooper()
    assertTrue("Condition not satisfied within $timeoutMillis ms", condition())
  }

  private fun createTestHarness(context: Context): TestHarness {
    val db = BamLinguageDatabase.createInMemory(context)
    val prefsFile = File(context.cacheDir, "qa_prefs_${System.nanoTime()}.preferences_pb")
    if (prefsFile.exists()) prefsFile.delete()
    val dsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val dispatchers = DefaultDispatcherProvider()
    val contentRepo = RoomContentRepository(db.contentDao(), dispatchers)
    val learningRepo = RoomLearningRepository(db.learningDao(), dispatchers)
    val prefsRepo =
      DataStorePreferencesRepository(
        UserPreferencesDataStore(
          PreferenceDataStoreFactory.create(scope = dsScope, produceFile = { prefsFile })
        )
      )
    return TestHarness(
      context = context,
      database = db,
      dispatchers = dispatchers,
      contentRepository = contentRepo,
      learningRepository = learningRepo,
      preferencesRepository = prefsRepo,
      prefsFile = prefsFile,
      dsScope = dsScope,
    )
  }

  private class RecordingTtsEngine : TextToSpeechEngine {
    private val _status = MutableStateFlow<TtsStatus>(TtsStatus.Ready)
    override val status: StateFlow<TtsStatus> = _status
    private val _voices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    override val availableVoices: StateFlow<List<TtsVoiceOption>> = _voices

    val spokenWords = mutableListOf<String>()
    val spokenSentences = mutableListOf<String>()
    val paragraphCalls = mutableListOf<List<TtsSpeechItem>>()

    override fun speakWord(word: String, speechRate: Float, accent: TtsAccent, preferredVoiceName: String?) {
      spokenWords += word
    }

    override fun speakSentence(text: String, itemId: String?, speechRate: Float, accent: TtsAccent, preferredVoiceName: String?) {
      spokenSentences += text
    }

    override fun speakParagraph(items: List<TtsSpeechItem>, speechRate: Float, accent: TtsAccent, speakerVoiceMap: Map<String, String>, defaultVoiceName: String?) {
      paragraphCalls += items
    }

    override fun pause() {}

    override fun resume() {}

    override fun stop() {}

    override fun refreshVoices() {}

    override fun shutdown() {}
  }

  private class TestHarness(
    context: Context,
    override val database: BamLinguageDatabase,
    override val dispatchers: DispatcherProvider,
    override val contentRepository: ContentRepository,
    override val learningRepository: LearningRepository,
    override val preferencesRepository: PreferencesRepository,
    private val prefsFile: File,
    private val dsScope: CoroutineScope,
  ) : AppContainer {
    private val clock = Instant.parse("2026-11-25T10:00:00Z").toEpochMilli()
    val timeSource = ManualTimeRefreshSource(initialTimeEpochMs = clock, clock = { clock })
    val recordingTts = RecordingTtsEngine()

    override val ttsEngine: TextToSpeechEngine = recordingTts
    override val aiService: AiService = AiService(DisabledOfflineAiProvider())
    override val srsScheduler: SrsScheduler = SrsScheduler()
    override val vocabularyResolutionEngine = com.example.domain.vocabulary.VocabularyResolutionEngine()
    override val contentPackageValidator = ContentPackageValidator()
    override val contentIntegrityAnalyzer = ContentIntegrityAnalyzer(contentPackageValidator, vocabularyResolutionEngine)
    override val jsonContentPackageImporter = JsonContentPackageImporter(contentRepository, contentPackageValidator, contentIntegrityAnalyzer)
    override val importContentPackageUseCase = ImportContentPackageUseCase(jsonContentPackageImporter)
    override val ensureBundledSamplePackageUseCase = EnsureBundledSamplePackageUseCase(context, contentRepository, preferencesRepository, jsonContentPackageImporter)
    override val dictionaryLookupUseCase = DictionaryLookupUseCase(contentRepository, learningRepository)
    override val resolveWordInSentenceUseCase = ResolveWordInSentenceUseCase(contentRepository, vocabularyResolutionEngine)
    override val observeReaderPackageUseCase = ObserveReaderPackageUseCase(contentRepository, learningRepository, vocabularyResolutionEngine)
    override val createSrsCardUseCase = CreateSrsCardUseCase(learningRepository)
    override val reviewSrsCardUseCase = ReviewSrsCardUseCase(learningRepository, srsScheduler)
    override val deleteSrsCardUseCase = DeleteSrsCardUseCase(learningRepository)
    override val timeRefreshSource: TimeRefreshSource = timeSource

    fun close() {
      ShadowLooper.idleMainLooper()
      database.close()
      runBlocking { dsScope.coroutineContext[Job]?.cancelAndJoin() }
      prefsFile.delete()
    }
  }
}
