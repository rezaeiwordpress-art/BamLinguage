package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.content.importer.JsonContentPackageImporter
import com.example.content.importer.PackageImportPreview
import com.example.content.schema.ContentPackageJsonDto
import com.example.content.validation.ContentIntegrityAnalyzer
import com.example.content.validation.ContentPackageValidator
import com.example.content.validation.ValidationOutcome
import com.example.core.ai.AiService
import com.example.core.ai.DisabledOfflineAiProvider
import com.example.core.common.CefrLevel
import com.example.core.common.DefaultDispatcherProvider
import com.example.core.common.ManualTimeRefreshSource
import com.example.core.common.StableIdGenerator
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
import com.example.domain.model.GlossaryEntry
import com.example.domain.model.GlossarySense
import com.example.domain.model.ImportValidationErrorCode
import com.example.domain.model.PackageImportResult
import com.example.domain.model.PronunciationInfo
import com.example.domain.model.ReaderWordStatus
import com.example.domain.model.ReviewQuality
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
import com.example.feature.learn.LearnScreen
import com.example.feature.learn.LearnViewModel
import com.example.feature.practice.PracticeDeckFilter
import com.example.feature.practice.PracticeViewModel
import com.example.feature.progress.ProgressViewModel
import com.example.feature.reader.ReaderScreen
import com.example.feature.reader.ReaderViewModel
import java.io.File
import java.time.Instant
import java.time.ZoneId
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
class Phase4FixesVerificationTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val strictJson =
    Json {
      ignoreUnknownKeys = false
      isLenient = false
      coerceInputValues = false
    }

  @Test
  fun `fix 1 detects missing words, missing phrases, mid-word offsets, duplicate IDs, and broken references without false 100 percent coverage`() {
    val validator = ContentPackageValidator()
    val analyzer = ContentIntegrityAnalyzer(validator)

    // Case A: Mid-word slice ("miss" at 5..9 inside "missed"), out-of-bounds offset, duplicate trimmed IDs, and broken sentenceIds reference
    val brokenStructuralJson =
      """
      {
        "schemaVersion": 1,
        "id": "story-broken-01",
        "version": 1,
        "type": "dialogue",
        "title": "Broken Validation Case",
        "descriptionFa": "بسته آزمایشی با خطاهای ساختاری",
        "level": "A2",
        "topic": "travel",
        "estimatedMinutes": 2,
        "metadata": {
          "author": "QA",
          "createdAt": "2026-11-25",
          "updatedAt": "2026-11-25",
          "wordCount": 3,
          "sentenceCount": 2,
          "glossaryCount": 1
        },
        "speakers": [
          { "id": "spk1", "name": "Sara", "nameFa": "سارا", "role": "traveler" }
        ],
        "sentences": [
          { "id": "s1", "speakerId": "spk1", "text": "Sara missed her train.", "translationFa": "سارا جا ماند." },
          { "id": " s1 ", "speakerId": "missing-spk", "text": "Her train left.", "translationFa": "قطار رفت." }
        ],
        "glossary": [
          {
            "id": "gls-miss",
            "term": "miss",
            "lemma": "miss",
            "forms": ["miss", "missed"],
            "pos": "verb",
            "cefr": "A2",
            "senses": [
              { "id": "sense-1", "meaningFa": "جا ماندن", "definitionEn": "To fail to catch.", "examples": [] }
            ],
            "sentenceIds": ["s999"],
            "occurrences": [
              { "sentenceId": "s1", "surface": "miss", "start": 5, "end": 9 },
              { "sentenceId": "s1", "surface": "her", "start": 12, "end": 99 }
            ]
          }
        ],
        "questions": []
      }
      """.trimIndent()

    val brokenDto = strictJson.decodeFromString(ContentPackageJsonDto.serializer(), brokenStructuralJson)
    val outcome = validator.validate(brokenDto) as ValidationOutcome.Invalid
    val codes = outcome.errors.map { it.code }.toSet()
    assertTrue(ImportValidationErrorCode.DUPLICATE_INTERNAL_ID in codes)
    assertTrue(ImportValidationErrorCode.INVALID_REFERENTIAL_INTEGRITY in codes)
    assertTrue(ImportValidationErrorCode.INVALID_WORD_OFFSET in codes)

    val brokenReport = analyzer.analyzeDto(brokenDto)
    assertFalse(brokenReport.isValidForImport)
    assertFalse(brokenReport.isCoverageVerified)
    assertTrue(brokenReport.coveragePercent < 100)

    // Case B: All single words exist ("she", "wants", "to", "run", "out", "of", "time"),
    // so token lookup alone might look full, EXCEPT the phrasal verb "run out of" has no multiword glossary entry!
    val missingPhraseAndWordJson =
      """
      {
        "schemaVersion": 1,
        "id": "story-missing-phrase-01",
        "version": 1,
        "type": "story",
        "title": "Missing Phrase Story",
        "descriptionFa": "داستان برای بررسی عبارت جاافتاده",
        "level": "B1",
        "topic": "daily",
        "estimatedMinutes": 2,
        "metadata": {
          "author": "QA",
          "createdAt": "2026-11-25",
          "updatedAt": "2026-11-25",
          "wordCount": 7,
          "sentenceCount": 1,
          "glossaryCount": 6
        },
        "speakers": [],
        "sentences": [
          { "id": "s1", "text": "She wants to run out of tickets.", "translationFa": "او نمی‌خواهد بلیت‌هایش تمام شود." }
        ],
        "glossary": [
          {
            "id": "g-she", "term": "she", "lemma": "she", "pos": "pronoun", "cefr": "A1",
            "senses": [{ "id": "sense-1", "meaningFa": "او", "definitionEn": "Female pronoun." }],
            "sentenceIds": ["s1"], "occurrences": [{ "sentenceId": "s1", "surface": "She", "start": 0, "end": 3 }]
          },
          {
            "id": "g-want", "term": "want", "lemma": "want", "forms": ["wants"], "pos": "verb", "cefr": "A1",
            "senses": [{ "id": "sense-1", "meaningFa": "خواستن", "definitionEn": "Desire." }],
            "sentenceIds": ["s1"], "occurrences": [{ "sentenceId": "s1", "surface": "wants", "start": 4, "end": 9 }]
          },
          {
            "id": "g-to", "term": "to", "lemma": "to", "pos": "preposition", "cefr": "A1",
            "senses": [{ "id": "sense-1", "meaningFa": "برای", "definitionEn": "Infinitive marker." }],
            "sentenceIds": ["s1"], "occurrences": [{ "sentenceId": "s1", "surface": "to", "start": 10, "end": 12 }]
          },
          {
            "id": "g-run", "term": "run", "lemma": "run", "pos": "verb", "cefr": "A1",
            "senses": [{ "id": "sense-1", "meaningFa": "دویدن", "definitionEn": "Move quickly." }],
            "sentenceIds": ["s1"], "occurrences": [{ "sentenceId": "s1", "surface": "run", "start": 13, "end": 16 }]
          },
          {
            "id": "g-out", "term": "out", "lemma": "out", "pos": "adverb", "cefr": "A1",
            "senses": [{ "id": "sense-1", "meaningFa": "بیرون", "definitionEn": "Outside." }],
            "sentenceIds": ["s1"], "occurrences": [{ "sentenceId": "s1", "surface": "out", "start": 17, "end": 20 }]
          },
          {
            "id": "g-of", "term": "of", "lemma": "of", "pos": "preposition", "cefr": "A1",
            "senses": [{ "id": "sense-1", "meaningFa": "از", "definitionEn": "Belonging to." }],
            "sentenceIds": ["s1"], "occurrences": [{ "sentenceId": "s1", "surface": "of", "start": 21, "end": 23 }]
          }
        ],
        "questions": []
      }
      """.trimIndent()

    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = BamLinguageDatabase.createInMemory(context)
    val prefsFile = File(context.cacheDir, "fix1_prefs_${System.nanoTime()}.preferences_pb")
    val dsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
      val dispatchers = DefaultDispatcherProvider()
      val contentRepo = RoomContentRepository(db.contentDao(), dispatchers)
      val learningRepo = RoomLearningRepository(db.learningDao(), dispatchers)
      val prefsRepo = DataStorePreferencesRepository(
        UserPreferencesDataStore(PreferenceDataStoreFactory.create(scope = dsScope, produceFile = { prefsFile }))
      )
      val importer = JsonContentPackageImporter(contentRepo, validator, analyzer)
      val learnVm =
        LearnViewModel(
          contentRepository = contentRepo,
          learningRepository = learningRepo,
          preferencesRepository = prefsRepo,
          importContentPackageUseCase = ImportContentPackageUseCase(importer),
          ensureBundledSamplePackageUseCase = EnsureBundledSamplePackageUseCase(context, contentRepo, prefsRepo, importer),
          dictionaryLookupUseCase = DictionaryLookupUseCase(contentRepo, learningRepo),
          createSrsCardUseCase = CreateSrsCardUseCase(learningRepo),
          contentIntegrityAnalyzer = analyzer,
          ttsEngine = NoOpTtsEngine(),
        )

      val phraseReport =
        runBlocking {
          val preview = importer.previewFromJson(missingPhraseAndWordJson) as PackageImportPreview.Ready
          preview.integrityReport
        }
      assertTrue(phraseReport.isValidForImport)
      assertTrue("Expected missing word 'tickets'", "tickets" in phraseReport.missingWords)
      assertTrue("Expected missing phrase 'run out of'", "run out of" in phraseReport.missingPhrases)
      assertFalse(phraseReport.isCoverageVerified)
      assertTrue("Coverage must stay below 100% when words or phrases are missing", phraseReport.coveragePercent < 100)

      // Verify pre-import UI displays the coverage warning explicitly before import
      composeRule.setContent {
        LearnScreen(viewModel = learnVm, onOpenReader = {})
      }
      learnVm.requestImportPreview(missingPhraseAndWordJson)
      composeRule.waitUntil(timeoutMillis = 10_000L) {
        composeRule.onAllNodesWithTag("integrity_coverage_label").fetchSemanticsNodes().isNotEmpty()
      }
      composeRule.onNodeWithTag("integrity_coverage_label").assertIsDisplayed()
      composeRule.onNodeWithTag("integrity_missing_vocab_header").assertIsDisplayed()
      composeRule.onNodeWithTag("integrity_coverage_label").assertTextContains("هشدار", substring = true)
    } finally {
      dsScope.cancel()
      db.close()
      prefsFile.delete()
    }
  }

  @Test
  fun `fix 2 replaces String hashCode with SHA-256 IDs, separates POS and sense, and keeps repeated imports duplicate-free`() =
    runTest {
      // Classic Java 32-bit hashCode collision pair: "Aa".hashCode() == "BB".hashCode() == 2112
      assertEquals("Aa".hashCode(), "BB".hashCode())
      assertNotEquals(
        StableIdGenerator.buildUserVocabId("Aa"),
        StableIdGenerator.buildUserVocabId("BB"),
      )
      assertNotEquals(
        StableIdGenerator.buildSentenceContentId(null, null, "Aa"),
        StableIdGenerator.buildSentenceContentId(null, null, "BB"),
      )
      // Whitespace/case normalization remains deterministic
      assertEquals(
        StableIdGenerator.buildUserVocabId("  Run  Out Of "),
        StableIdGenerator.buildUserVocabId("run out of"),
      )
      // Colon escaping prevents "a:b" + "c" colliding with "a" + "b:c"
      assertNotEquals(
        StableIdGenerator.buildWordContentId("a:b", "noun", "c"),
        StableIdGenerator.buildWordContentId("a", "b:c", "c"),
      )

      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = BamLinguageDatabase.createInMemory(context)
      try {
        val dispatchers = DefaultDispatcherProvider()
        val contentRepo = RoomContentRepository(db.contentDao(), dispatchers)
        val learningRepo = RoomLearningRepository(db.learningDao(), dispatchers)
        val importer = JsonContentPackageImporter(contentRepo)
        val createCard = CreateSrsCardUseCase(learningRepo)

        val storyJson = context.assets.open("sample_packages/a_late_train_story.json").bufferedReader().use { it.readText() }
        assertTrue(importer.importFromJson(storyJson, isBundledSample = true) is PackageImportResult.Success)
        val bundle = contentRepo.getPackageBundle("story-b1-0001")!!
        val missVerbEntry = bundle.glossaryEntries.first { it.lemma == "miss" }

        // Separate senses of same (lemma, pos) stay distinct
        val verbSense1 = missVerbEntry.senses[0]
        val verbSense2 = missVerbEntry.senses[1]
        val verbCard1 = createCard.createWordCardFromGlossary(missVerbEntry, verbSense1, bundle.sentences.first(), "Story", 1_000L)
        val verbCard2 = createCard.createWordCardFromGlossary(missVerbEntry, verbSense2, bundle.sentences.first(), "Story", 2_000L)
        assertTrue(verbCard1 is CreateSrsCardResult.Created)
        assertTrue(verbCard2 is CreateSrsCardResult.Created)

        // Same lemma & senseId with a different part of speech stays separate
        val missNounEntry =
          GlossaryEntry(
            id = "story-b1-0001::miss-noun",
            packageId = "story-b1-0001",
            sourceGlossaryId = "miss-noun",
            term = "miss",
            normalizedTerm = "miss",
            lemma = "miss",
            forms = listOf("miss", "misses"),
            partOfSpeech = "noun",
            cefr = CefrLevel.B1,
            senses = listOf(GlossarySense("s-noun", "story-b1-0001::miss-noun", verbSense1.sourceSenseId, 0, "خطا", "Failure to hit")),
            phonetic = "/mɪs/",
            pronunciation = PronunciationInfo(us = "/mɪs/"),
            persianMeaning = "خطا",
            englishDefinition = "Failure to hit",
            exampleSentence = null,
          )
        val nounCard = createCard.createWordCardFromGlossary(missNounEntry, missNounEntry.senses.first(), bundle.sentences.first(), "Story", 3_000L)
        assertTrue(nounCard is CreateSrsCardResult.Created)

        // Manual vocab cards for "Aa" vs "BB" stay distinct despite hashCode collision
        val manualAa = createCard.createManualWordAndCard("Aa", "آا", null, "noun", null, "Personal", true, 4_000L)
        val manualBb = createCard.createManualWordAndCard("BB", "بی‌بی", null, "noun", null, "Personal", true, 5_000L)
        assertTrue(manualAa is CreateSrsCardResult.Created)
        assertTrue(manualBb is CreateSrsCardResult.Created)
        assertEquals(2, learningRepo.observeUserVocabulary().first().size)

        // Repeat import / upgrade of the same package must NOT create duplicate cards or allow re-adding existing meanings
        val v2Json = storyJson.replace("\"version\": 1", "\"version\": 2")
        assertTrue(importer.importFromJson(v2Json, allowSameVersionOverwrite = true) is PackageImportResult.Success)
        val upgradedBundle = contentRepo.getPackageBundle("story-b1-0001")!!
        val upgradedMiss = upgradedBundle.glossaryEntries.first { it.lemma == "miss" }
        val repeatCreate = createCard.createWordCardFromGlossary(upgradedMiss, upgradedMiss.senses.first(), upgradedBundle.sentences.first(), "Story v2", 6_000L)
        assertTrue(repeatCreate is CreateSrsCardResult.AlreadyExists)
        assertEquals(5, learningRepo.observeAllSrsCards().first().size)
      } finally {
        db.close()
      }
    }

  @Test
  fun `fix 3 dashboard, practice queue, progress, and reader highlights refresh automatically on time change and foreground resume`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = BamLinguageDatabase.createInMemory(context)
    val prefsFile = File(context.cacheDir, "fix3_prefs_${System.nanoTime()}.preferences_pb")
    val dsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val collectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
      val dispatchers = DefaultDispatcherProvider()
      val contentRepo = RoomContentRepository(db.contentDao(), dispatchers)
      val learningRepo = RoomLearningRepository(db.learningDao(), dispatchers)
      val prefsRepo = DataStorePreferencesRepository(
        UserPreferencesDataStore(PreferenceDataStoreFactory.create(scope = dsScope, produceFile = { prefsFile }))
      )
      val scheduler = SrsScheduler()
      val createCard = CreateSrsCardUseCase(learningRepo)
      val reviewCard = ReviewSrsCardUseCase(learningRepo, scheduler)
      val importer = JsonContentPackageImporter(contentRepo)

      val (bundle, reviewed, initialClock) =
        runBlocking {
          val storyJson = context.assets.open("sample_packages/a_late_train_story.json").bufferedReader().use { it.readText() }
          assertTrue(importer.importFromJson(storyJson, isBundledSample = true) is PackageImportResult.Success)
          val importedBundle = contentRepo.getPackageBundle("story-b1-0001")!!
          val missEntry = importedBundle.glossaryEntries.first { it.lemma == "miss" }

          // Start at 2026-11-25 10:00 UTC; review once -> interval = 1 day, due at +24h
          val t0 = Instant.parse("2026-11-25T10:00:00Z").toEpochMilli()
          val created =
            createCard.createWordCardFromGlossary(
              entry = missEntry,
              selectedSense = missEntry.senses.first(),
              contextSentence = importedBundle.sentences.first(),
              packageTitle = importedBundle.contentPackage.title,
              nowEpochMs = t0,
            ) as CreateSrsCardResult.Created
          val updated = reviewCard(created.card, ReviewQuality.GOOD, t0).updatedCard
          Triple(importedBundle, updated, t0)
        }

      var clockNow = initialClock
      val timeSource = ManualTimeRefreshSource(initialTimeEpochMs = clockNow, clock = { clockNow })

      val observeReader = ObserveReaderPackageUseCase(contentRepo, learningRepo)
      val homeVm =
        HomeViewModel(
          contentRepository = contentRepo,
          learningRepository = learningRepo,
          preferencesRepository = prefsRepo,
          ensureBundledSamplePackageUseCase = EnsureBundledSamplePackageUseCase(context, contentRepo, prefsRepo, importer),
          createSrsCardUseCase = createCard,
          ttsEngine = NoOpTtsEngine(),
          timeRefreshSource = timeSource,
        )
      val practiceVm =
        PracticeViewModel(
          ttsEngine = NoOpTtsEngine(),
          learningRepository = learningRepo,
          preferencesRepository = prefsRepo,
          srsScheduler = scheduler,
          reviewSrsCardUseCase = reviewCard,
          deleteSrsCardUseCase = DeleteSrsCardUseCase(learningRepo),
          timeRefreshSource = timeSource,
        )
      val progressVm =
        ProgressViewModel(
          contentRepository = contentRepo,
          learningRepository = learningRepo,
          timeRefreshSource = timeSource,
        )

      val homeCollectJob = collectScope.launch { homeVm.uiState.collect() }
      val practiceCollectJob = collectScope.launch { practiceVm.uiState.collect() }
      val progressCollectJob = collectScope.launch { progressVm.uiState.collect() }
      val readerSnapshotFlow = observeReader("story-b1-0001", timeSource.refreshTicks)

      // Hook a real Lifecycle ON_RESUME observer just like MainActivity
      val lifecycleOwner = SimpleLifecycleOwner(Lifecycle.State.STARTED)
      val observer =
        androidx.lifecycle.LifecycleEventObserver { _, event ->
          if (event == Lifecycle.Event.ON_RESUME) {
            timeSource.notifyForegrounded()
          }
        }
      lifecycleOwner.lifecycle.addObserver(observer)

      // Initially at day 0 (before dueAt): 0 due cards, 1 review today, streak = 1, Reader token = LEARNING
      waitUntilMain(timeoutMillis = 10_000L) {
        !homeVm.uiState.value.isLoading &&
          practiceVm.uiState.value.allCards.size == 1 &&
          progressVm.uiState.value.analytics.reviewedTodayCount == 1 &&
          homeVm.uiState.value.analytics.reviewedTodayCount == 1
      }
      assertEquals(0, homeVm.uiState.value.stats.dueSrsCards)
      assertEquals(0, homeVm.uiState.value.analytics.dueTotalCount)
      assertEquals(1, homeVm.uiState.value.analytics.streakDays)
      assertEquals(0, practiceVm.uiState.value.dueQueue.size)
      practiceVm.setDeckFilter(PracticeDeckFilter.DUE)
      waitUntilMain(timeoutMillis = 5_000L) {
        practiceVm.uiState.value.deckFilter == PracticeDeckFilter.DUE
      }
      assertEquals(0, practiceVm.uiState.value.filteredDeck.size)
      assertEquals(0, progressVm.uiState.value.stats.dueSrsCards)
      val sentence1Id = bundle.sentences.first().id
      assertEquals(
        ReaderWordStatus.LEARNING,
        runBlocking { readerSnapshotFlow.first()!! }
          .tokenHighlightsBySentenceId[sentence1Id]!!
          .first { it.surface == "missed" }
          .status,
      )

      // Advance wall clock past dueAtEpochMs (next day + 2h) and simulate foreground resume (ON_RESUME)
      clockNow = kotlin.math.max(reviewed.dueAtEpochMs + 120_000L, Instant.parse("2026-11-26T12:00:00Z").toEpochMilli())
      // Also test ON_RESUME signal directly without changing ManualTimeRefreshSource.currentNow
      lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

      waitUntilMain(timeoutMillis = 10_000L) {
        homeVm.uiState.value.stats.dueSrsCards == 1 &&
          practiceVm.uiState.value.dueQueue.size == 1 &&
          progressVm.uiState.value.stats.dueSrsCards == 1
      }

      // Verify all three dashboards + Practice DUE filter + Reader token highlights updated reactively
      assertEquals(1, homeVm.uiState.value.analytics.dueTotalCount)
      assertEquals(0, homeVm.uiState.value.analytics.reviewedTodayCount) // new day rolled over!
      assertEquals(1, homeVm.uiState.value.analytics.streakDays) // reviewed yesterday keeps streak = 1
      assertEquals(1, practiceVm.uiState.value.filteredDeck.size)
      assertEquals(1, progressVm.uiState.value.analytics.dueTotalCount)
      assertEquals(
        ReaderWordStatus.DUE,
        runBlocking { readerSnapshotFlow.first()!! }
          .tokenHighlightsBySentenceId[sentence1Id]!!
          .first { it.surface == "missed" }
          .status,
      )

      // Advance another 2 days without review -> streak resets to 0 on time change emission
      val twoDaysLater =
        Instant.ofEpochMilli(clockNow).atZone(ZoneId.systemDefault()).toLocalDate().plusDays(2)
          .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 60_000L
      clockNow = twoDaysLater
      timeSource.advanceTo(twoDaysLater)
      waitUntilMain(timeoutMillis = 10_000L) {
        homeVm.uiState.value.analytics.streakDays == 0 && progressVm.uiState.value.analytics.streakDays == 0
      }

      homeCollectJob.cancel()
      practiceCollectJob.cancel()
      progressCollectJob.cancel()
      lifecycleOwner.lifecycle.removeObserver(observer)
    } finally {
      collectScope.cancel()
      dsScope.cancel()
      db.close()
      prefsFile.delete()
    }
  }

  @Test
  fun `fix 4 quiz attempts persist across Room v3-to-v4 migration and simulated cold restart in Reader`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbFile = File(context.cacheDir, "fix4_quiz_restart_${System.nanoTime()}.db")
    val prefsFile = File(context.cacheDir, "fix4_prefs_${System.nanoTime()}.preferences_pb")
    if (dbFile.exists()) dbFile.delete()
    if (prefsFile.exists()) prefsFile.delete()

    val dispatchers = DefaultDispatcherProvider()

    // Part A: Seed a Room v3 database and verify MIGRATION_3_4 creates quiz_attempts while preserving v3 rows
    val v3Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    try {
      v3Db.execSQL("CREATE TABLE content_packages (packageId TEXT NOT NULL PRIMARY KEY, schemaVersion INTEGER NOT NULL, contentVersion INTEGER NOT NULL, contentType TEXT NOT NULL, level TEXT NOT NULL, topic TEXT NOT NULL, title TEXT NOT NULL, titleFa TEXT NOT NULL, description TEXT NOT NULL, descriptionFa TEXT NOT NULL, author TEXT NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, wordCount INTEGER NOT NULL, sentenceCountMeta INTEGER NOT NULL, glossaryCountMeta INTEGER NOT NULL, estimatedMinutes INTEGER NOT NULL, language TEXT NOT NULL, translationLanguage TEXT NOT NULL, tags TEXT NOT NULL, importedAtEpochMs INTEGER NOT NULL, isBundledSample INTEGER NOT NULL)")
      v3Db.execSQL("CREATE INDEX index_content_packages_level ON content_packages (level)")
      v3Db.execSQL("CREATE INDEX index_content_packages_contentType ON content_packages (contentType)")
      v3Db.execSQL("CREATE INDEX index_content_packages_importedAtEpochMs ON content_packages (importedAtEpochMs)")
      v3Db.execSQL("CREATE TABLE speakers (id TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, sourceSpeakerId TEXT NOT NULL, name TEXT NOT NULL, nameFa TEXT NOT NULL, role TEXT NOT NULL, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE)")
      v3Db.execSQL("CREATE INDEX index_speakers_packageId ON speakers (packageId)")
      v3Db.execSQL("CREATE TABLE sentences (id TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, sourceSentenceId TEXT NOT NULL, orderIndex INTEGER NOT NULL, speakerId TEXT, englishText TEXT NOT NULL, persianTranslation TEXT NOT NULL, phoneticHint TEXT, grammarNoteFa TEXT, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE, FOREIGN KEY(speakerId) REFERENCES speakers(id) ON DELETE SET NULL)")
      v3Db.execSQL("CREATE UNIQUE INDEX index_sentences_packageId_orderIndex ON sentences (packageId, orderIndex)")
      v3Db.execSQL("CREATE INDEX index_sentences_speakerId ON sentences (speakerId)")
      v3Db.execSQL("CREATE TABLE glossary_entries (id TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, sourceGlossaryId TEXT NOT NULL, term TEXT NOT NULL, normalizedTerm TEXT NOT NULL, lemma TEXT NOT NULL, forms TEXT NOT NULL, partOfSpeech TEXT NOT NULL, cefr TEXT NOT NULL, phonetic TEXT, pronunciationUk TEXT, pronunciationUs TEXT, persianMeaning TEXT NOT NULL, englishDefinition TEXT NOT NULL, exampleSentence TEXT, synonyms TEXT NOT NULL, antonyms TEXT NOT NULL, sentenceIds TEXT NOT NULL, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE)")
      v3Db.execSQL("CREATE INDEX index_glossary_entries_packageId_normalizedTerm ON glossary_entries (packageId, normalizedTerm)")
      v3Db.execSQL("CREATE INDEX index_glossary_entries_normalizedTerm ON glossary_entries (normalizedTerm)")
      v3Db.execSQL("CREATE INDEX index_glossary_entries_lemma ON glossary_entries (lemma)")
      v3Db.execSQL("CREATE TABLE glossary_senses (id TEXT NOT NULL PRIMARY KEY, glossaryEntryId TEXT NOT NULL, packageId TEXT NOT NULL, sourceSenseId TEXT NOT NULL, orderIndex INTEGER NOT NULL, meaningFa TEXT NOT NULL, definitionEn TEXT NOT NULL, examplesJson TEXT NOT NULL, FOREIGN KEY(glossaryEntryId) REFERENCES glossary_entries(id) ON DELETE CASCADE, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE)")
      v3Db.execSQL("CREATE INDEX index_glossary_senses_glossaryEntryId ON glossary_senses (glossaryEntryId)")
      v3Db.execSQL("CREATE INDEX index_glossary_senses_packageId ON glossary_senses (packageId)")
      v3Db.execSQL("CREATE TABLE word_occurrences (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, packageId TEXT NOT NULL, sentenceId TEXT NOT NULL, glossaryEntryId TEXT NOT NULL, surfaceForm TEXT NOT NULL, startOffset INTEGER NOT NULL, endOffset INTEGER NOT NULL, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE, FOREIGN KEY(sentenceId) REFERENCES sentences(id) ON DELETE CASCADE, FOREIGN KEY(glossaryEntryId) REFERENCES glossary_entries(id) ON DELETE CASCADE)")
      v3Db.execSQL("CREATE INDEX index_word_occurrences_packageId ON word_occurrences (packageId)")
      v3Db.execSQL("CREATE INDEX index_word_occurrences_sentenceId ON word_occurrences (sentenceId)")
      v3Db.execSQL("CREATE INDEX index_word_occurrences_glossaryEntryId ON word_occurrences (glossaryEntryId)")
      v3Db.execSQL("CREATE TABLE questions (id TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, sourceQuestionId TEXT NOT NULL, orderIndex INTEGER NOT NULL, promptEn TEXT NOT NULL, promptFa TEXT NOT NULL, options TEXT NOT NULL, correctOptionIndex INTEGER NOT NULL, explanationFa TEXT, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE)")
      v3Db.execSQL("CREATE UNIQUE INDEX index_questions_packageId_orderIndex ON questions (packageId, orderIndex)")
      v3Db.execSQL("CREATE TABLE srs_cards (cardId TEXT NOT NULL PRIMARY KEY, cardType TEXT NOT NULL, contentId TEXT NOT NULL, frontTerm TEXT NOT NULL, backPersianMeaning TEXT NOT NULL, englishDefinition TEXT, normalizedLemma TEXT NOT NULL, pos TEXT NOT NULL, senseId TEXT NOT NULL, phonetic TEXT, exampleSentence TEXT, contextSentenceEn TEXT, contextSentenceFa TEXT, sourcePackageId TEXT, sourcePackageTitle TEXT, sourceGlossaryId TEXT, sourceSentenceId TEXT, state TEXT NOT NULL, repetitions INTEGER NOT NULL, intervalDays INTEGER NOT NULL, easeFactor REAL NOT NULL, dueAtEpochMs INTEGER NOT NULL, lastReviewedAtEpochMs INTEGER, createdAtEpochMs INTEGER NOT NULL)")
      v3Db.execSQL("CREATE INDEX index_srs_cards_dueAtEpochMs ON srs_cards (dueAtEpochMs)")
      v3Db.execSQL("CREATE INDEX index_srs_cards_state ON srs_cards (state)")
      v3Db.execSQL("CREATE INDEX index_srs_cards_sourcePackageId ON srs_cards (sourcePackageId)")
      v3Db.execSQL("CREATE INDEX index_srs_cards_frontTerm ON srs_cards (frontTerm)")
      v3Db.execSQL("CREATE UNIQUE INDEX index_srs_cards_contentId ON srs_cards (contentId)")
      v3Db.execSQL("CREATE INDEX index_srs_cards_cardType ON srs_cards (cardType)")
      v3Db.execSQL("CREATE TABLE srs_review_logs (logId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, cardId TEXT NOT NULL, qualityScore INTEGER NOT NULL, previousIntervalDays INTEGER NOT NULL, newIntervalDays INTEGER NOT NULL, previousEaseFactor REAL NOT NULL, newEaseFactor REAL NOT NULL, reviewedAtEpochMs INTEGER NOT NULL, FOREIGN KEY(cardId) REFERENCES srs_cards(cardId) ON DELETE CASCADE)")
      v3Db.execSQL("CREATE INDEX index_srs_review_logs_cardId ON srs_review_logs (cardId)")
      v3Db.execSQL("CREATE INDEX index_srs_review_logs_reviewedAtEpochMs ON srs_review_logs (reviewedAtEpochMs)")
      v3Db.execSQL("CREATE TABLE learning_progress (progressId TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, packageTitleSnapshot TEXT NOT NULL, completedSentenceCount INTEGER NOT NULL, totalSentenceCount INTEGER NOT NULL, correctQuestionsCount INTEGER NOT NULL, totalQuestionsCount INTEGER NOT NULL, lastReadSentenceIndex INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, completedSentenceIds TEXT NOT NULL)")
      v3Db.execSQL("CREATE UNIQUE INDEX index_learning_progress_packageId ON learning_progress (packageId)")
      v3Db.execSQL("CREATE INDEX index_learning_progress_updatedAtEpochMs ON learning_progress (updatedAtEpochMs)")
      v3Db.execSQL("CREATE TABLE user_vocabulary (vocabId TEXT NOT NULL PRIMARY KEY, normalizedTerm TEXT NOT NULL, displayTerm TEXT NOT NULL, persianMeaning TEXT NOT NULL, phonetic TEXT, notesFa TEXT, sourcePackageId TEXT, masteryLevel INTEGER NOT NULL, savedAtEpochMs INTEGER NOT NULL)")
      v3Db.execSQL("CREATE UNIQUE INDEX index_user_vocabulary_normalizedTerm ON user_vocabulary (normalizedTerm)")
      v3Db.execSQL("CREATE INDEX index_user_vocabulary_sourcePackageId ON user_vocabulary (sourcePackageId)")
      v3Db.execSQL("CREATE TABLE reader_bookmarks (bookmarkId TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, packageTitleSnapshot TEXT NOT NULL, sentenceId TEXT NOT NULL, sentenceOrderIndex INTEGER NOT NULL, noteFa TEXT, createdAtEpochMs INTEGER NOT NULL)")
      v3Db.execSQL("CREATE UNIQUE INDEX index_reader_bookmarks_packageId_sentenceId ON reader_bookmarks (packageId, sentenceId)")
      v3Db.execSQL("CREATE INDEX index_reader_bookmarks_createdAtEpochMs ON reader_bookmarks (createdAtEpochMs)")
      v3Db.execSQL("INSERT INTO srs_cards VALUES ('v3_card', 'WORD', 'word:go:verb:sense-1', 'go', 'رفتن', null, 'go', 'verb', 'sense-1', null, null, null, null, null, null, null, null, 'NEW', 0, 0, 2.5, 1000, null, 1000)")
      v3Db.version = 3
    } finally {
      v3Db.close()
    }

    // Session 1: Open upgraded v4 DB, import story, answer quiz question, and mark first sentence read
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

    try {
      val contentRepo1 = RoomContentRepository(db1.contentDao(), dispatchers)
      val learningRepo1 = RoomLearningRepository(db1.learningDao(), dispatchers)
      val prefsRepo1 = DataStorePreferencesRepository(
        UserPreferencesDataStore(PreferenceDataStoreFactory.create(scope = scope1, produceFile = { prefsFile }))
      )
      val importer1 = JsonContentPackageImporter(contentRepo1)

      val bundle =
        runBlocking {
          // Verify v3 SRS card survived v3->v4 migration
          assertEquals(1, learningRepo1.observeAllSrsCards().first().size)
          val storyJson = context.assets.open("sample_packages/a_late_train_story.json").bufferedReader().use { it.readText() }
          assertTrue(importer1.importFromJson(storyJson, isBundledSample = true) is PackageImportResult.Success)
          contentRepo1.getPackageBundle("story-b1-0001")!!
        }
      assertTrue(bundle.questions.isNotEmpty())
      val firstQuestion = bundle.questions.first()

      val vm1 =
        ReaderViewModel(
          packageId = "story-b1-0001",
          observeReaderPackageUseCase = ObserveReaderPackageUseCase(contentRepo1, learningRepo1),
          resolveWordInSentenceUseCase = ResolveWordInSentenceUseCase(contentRepo1),
          createSrsCardUseCase = CreateSrsCardUseCase(learningRepo1),
          learningRepository = learningRepo1,
          preferencesRepository = prefsRepo1,
          ttsEngine = NoOpTtsEngine(),
          aiService = AiService(DisabledOfflineAiProvider()),
        )
      val collect1 = scope1.launch { vm1.uiState.collect() }

      waitUntilMain(timeoutMillis = 10_000L) {
        vm1.uiState.value.snapshot != null
      }

      // Answer question 0 with the correct option, then verify attempt count & persistence
      vm1.answerQuestion(firstQuestion.id, firstQuestion.correctOptionIndex)
      waitUntilMain(timeoutMillis = 10_000L) {
        runBlocking {
          learningRepo1.observeQuizAttemptsForPackage("story-b1-0001").first().isNotEmpty() &&
            learningRepo1.observeProgressForPackage("story-b1-0001").first()?.correctQuestionsCount == 1
        }
      }
      collect1.cancel()
    } finally {
      ShadowLooper.idleMainLooper()
      db1.close()
      runBlocking {
        scope1.coroutineContext[Job]?.cancelAndJoin()
      }
    }

    // Session 2: Simulate cold restart with fresh DB + fresh ReaderViewModel + ReaderScreen
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

    try {
      val contentRepo2 = RoomContentRepository(db2.contentDao(), dispatchers)
      val learningRepo2 = RoomLearningRepository(db2.learningDao(), dispatchers)
      val prefsRepo2 = DataStorePreferencesRepository(
        UserPreferencesDataStore(PreferenceDataStoreFactory.create(scope = scope2, produceFile = { prefsFile }))
      )

      val persistedAttempts = runBlocking { learningRepo2.observeQuizAttemptsForPackage("story-b1-0001").first() }
      assertEquals(1, persistedAttempts.size)
      assertTrue(persistedAttempts.first().isCorrect)

      val vm2 =
        ReaderViewModel(
          packageId = "story-b1-0001",
          observeReaderPackageUseCase = ObserveReaderPackageUseCase(contentRepo2, learningRepo2),
          resolveWordInSentenceUseCase = ResolveWordInSentenceUseCase(contentRepo2),
          createSrsCardUseCase = CreateSrsCardUseCase(learningRepo2),
          learningRepository = learningRepo2,
          preferencesRepository = prefsRepo2,
          ttsEngine = NoOpTtsEngine(),
          aiService = AiService(DisabledOfflineAiProvider()),
        )

      composeRule.setContent {
        ReaderScreen(viewModel = vm2, onNavigateBack = {})
      }

      composeRule.waitUntil(timeoutMillis = 10_000L) {
        ShadowLooper.idleMainLooper()
        vm2.uiState.value.answeredQuestions.isNotEmpty() &&
          composeRule.onAllNodesWithTag("reader_lazy_column").fetchSemanticsNodes().isNotEmpty()
      }

      val bundle2 = runBlocking { contentRepo2.getPackageBundle("story-b1-0001")!! }
      val firstQ = bundle2.questions.first()
      assertEquals(firstQ.correctOptionIndex, vm2.uiState.value.answeredQuestions[firstQ.id])

      // Scroll down to question 0 and verify explanation & selected state are rendered immediately after restart
      composeRule.onNodeWithTag("reader_lazy_column").performScrollToNode(hasTestTag("reader_question_0"))
      composeRule.onNodeWithTag("question_0_option_${firstQ.correctOptionIndex}").assertIsDisplayed()
      firstQ.explanationFa?.let { explanation ->
        assertTrue(composeRule.onAllNodesWithText(explanation, substring = true).fetchSemanticsNodes().isNotEmpty())
      }

      // Change answer to a wrong option -> persists updated selection + attemptCount == 2 + correctQuestionsCount == 0
      val wrongOption = (firstQ.correctOptionIndex + 1) % firstQ.options.size
      composeRule.onNodeWithTag("question_0_option_$wrongOption").performClick()
      composeRule.waitUntil(timeoutMillis = 10_000L) {
        ShadowLooper.idleMainLooper()
        runBlocking {
          val attempt = learningRepo2.observeQuizAttemptsForPackage("story-b1-0001").first().firstOrNull()
          val progress = learningRepo2.observeProgressForPackage("story-b1-0001").first()
          attempt?.selectedOptionIndex == wrongOption &&
            attempt.attemptCount == 2 &&
            progress?.correctQuestionsCount == 0
        }
      }
      val updatedProgress = runBlocking { learningRepo2.observeProgressForPackage("story-b1-0001").first()!! }
      assertEquals(0, updatedProgress.correctQuestionsCount)
    } finally {
      db2.close()
      scope2.cancel()
      dbFile.delete()
      prefsFile.delete()
    }
  }

  private class NoOpTtsEngine : TextToSpeechEngine {
    private val _status = MutableStateFlow<TtsStatus>(TtsStatus.Ready)
    override val status: StateFlow<TtsStatus> = _status
    private val _voices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    override val availableVoices: StateFlow<List<TtsVoiceOption>> = _voices

    override fun refreshVoices() {}

    override fun speakWord(word: String, speechRate: Float, accent: TtsAccent, preferredVoiceName: String?) {}

    override fun speakSentence(text: String, itemId: String?, speechRate: Float, accent: TtsAccent, preferredVoiceName: String?) {}

    override fun speakParagraph(items: List<TtsSpeechItem>, speechRate: Float, accent: TtsAccent, speakerVoiceMap: Map<String, String>, defaultVoiceName: String?) {}

    override fun pause() {}

    override fun resume() {}

    override fun stop() {}

    override fun shutdown() {}
  }

  private class SimpleLifecycleOwner(initialState: Lifecycle.State) : LifecycleOwner {
    private val registry = LifecycleRegistry.createUnsafe(this).apply {
      currentState = initialState
    }

    override val lifecycle: Lifecycle
      get() = registry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
      registry.handleLifecycleEvent(event)
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
}
