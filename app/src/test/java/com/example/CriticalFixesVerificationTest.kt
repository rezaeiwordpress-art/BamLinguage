package com.example

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.content.importer.JsonContentPackageImporter
import com.example.core.ai.AiService
import com.example.core.ai.DisabledOfflineAiProvider
import com.example.core.common.CefrLevel
import com.example.core.common.ContentType
import com.example.core.common.DefaultDispatcherProvider
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.tts.TextToSpeechEngine
import com.example.core.tts.TtsStatus
import com.example.core.tts.TtsVoiceOption
import com.example.data.local.database.BamLinguageDatabase
import com.example.data.local.entity.SrsCardEntity
import com.example.data.repository.DataStorePreferencesRepository
import com.example.data.repository.RoomContentRepository
import com.example.data.repository.RoomLearningRepository
import com.example.domain.model.ContentPackage
import com.example.domain.model.ContentPackageBundle
import com.example.domain.model.GlossaryEntry
import com.example.domain.model.GlossaryExample
import com.example.domain.model.GlossarySense
import com.example.domain.model.PackageImportResult
import com.example.domain.model.PackageMetadata
import com.example.domain.model.PronunciationInfo
import com.example.domain.model.ReaderWordStatus
import com.example.domain.model.ReviewQuality
import com.example.domain.model.Sentence
import com.example.domain.model.SrsCardState
import com.example.domain.model.WordOccurrence
import com.example.domain.srs.SrsScheduler
import com.example.domain.usecase.CreateSrsCardResult
import com.example.domain.usecase.CreateSrsCardUseCase
import com.example.domain.usecase.ObserveReaderPackageUseCase
import com.example.domain.usecase.ResolveWordInSentenceUseCase
import com.example.domain.usecase.ReviewSrsCardUseCase
import com.example.feature.reader.ReaderScreen
import com.example.feature.reader.ReaderViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class CriticalFixesVerificationTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun `fix 1 updating SRS card during reviews and upserts preserves all previous review history`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = BamLinguageDatabase.createInMemory(context)
      try {
        val dispatchers = DefaultDispatcherProvider()
        val learningRepo = RoomLearningRepository(db.learningDao(), dispatchers)
        val createCard = CreateSrsCardUseCase(learningRepo)
        val reviewCard = ReviewSrsCardUseCase(learningRepo, SrsScheduler())

        val entry =
          GlossaryEntry(
            id = "pkg::miss",
            packageId = "pkg",
            sourceGlossaryId = "miss-verb-001",
            term = "miss",
            normalizedTerm = "miss",
            lemma = "miss",
            forms = listOf("miss", "missed"),
            partOfSpeech = "verb",
            cefr = CefrLevel.A2,
            senses = listOf(GlossarySense("pkg::miss::sense-1", "pkg::miss", "sense-1", 0, "جا ماندن", "To fail to catch.")),
            phonetic = "/mɪs/",
            pronunciation = PronunciationInfo(us = "/mɪs/"),
            persianMeaning = "جا ماندن",
            englishDefinition = "To fail to catch.",
            exampleSentence = "Sara missed her train.",
          )
        val created =
          createCard.createWordCardFromGlossary(entry, entry.senses.first(), null, "Story", 1_000L)
            as CreateSrsCardResult.Created

        val review1 = reviewCard(created.card, ReviewQuality.AGAIN, 10_000L).updatedCard
        val review2 = reviewCard(review1, ReviewQuality.GOOD, 90_000_000L).updatedCard
        val review3 = reviewCard(review2, ReviewQuality.EASY, 200_000_000L).updatedCard

        // Also update the card metadata via upsertSrsCard -> must NOT delete review logs
        learningRepo.upsertSrsCard(review3.copy(backPersianMeaning = "از دست دادن (به‌روزرسانی)"))

        val logsForCard = db.learningDao().getReviewLogsForCard(created.card.cardId)
        val allLogs = learningRepo.observeAllReviewLogs().first()
        assertEquals(3, logsForCard.size)
        assertEquals(3, allLogs.size)
        assertEquals(
          listOf(ReviewQuality.EASY.score, ReviewQuality.GOOD.score, ReviewQuality.AGAIN.score),
          logsForCard.map { it.qualityScore },
        )
        val persistedCard = learningRepo.getSrsCardByContentId(created.card.contentId)!!
        assertEquals("از دست دادن (به‌روزرسانی)", persistedCard.backPersianMeaning)
        assertEquals(SrsCardState.REVIEW, persistedCard.state)
      } finally {
        db.close()
      }
    }

  @Test
  fun `fix 2 reading a later sentence only marks that sentence completed and never auto-completes previous sentences`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = BamLinguageDatabase.createInMemory(context)
    val prefsFile = File(context.cacheDir, "fix2_prefs_${System.nanoTime()}.preferences_pb")
    val dsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
      val dispatchers = DefaultDispatcherProvider()
      val contentRepo = RoomContentRepository(db.contentDao(), dispatchers)
      val learningRepo = RoomLearningRepository(db.learningDao(), dispatchers)
      val ds = PreferenceDataStoreFactory.create(scope = dsScope, produceFile = { prefsFile })
      val prefsRepo = DataStorePreferencesRepository(UserPreferencesDataStore(ds))
      val importer = JsonContentPackageImporter(contentRepo)

      val bundle =
        runBlocking {
          val storyJson =
            context.assets.open("sample_packages/a_late_train_story.json").bufferedReader().use { it.readText() }
          assertTrue(importer.importFromJson(storyJson, isBundledSample = true) is PackageImportResult.Success)
          contentRepo.getPackageBundle("story-b1-0001")!!
        }
      assertTrue(bundle.sentences.size >= 4)

      val vm =
        ReaderViewModel(
          packageId = "story-b1-0001",
          observeReaderPackageUseCase = ObserveReaderPackageUseCase(contentRepo, learningRepo),
          resolveWordInSentenceUseCase = ResolveWordInSentenceUseCase(contentRepo),
          createSrsCardUseCase = CreateSrsCardUseCase(learningRepo),
          learningRepository = learningRepo,
          preferencesRepository = prefsRepo,
          ttsEngine = NoOpTtsEngine(),
          aiService = AiService(DisabledOfflineAiProvider()),
        )

      composeRule.setContent {
        ReaderScreen(viewModel = vm, onNavigateBack = {})
      }

      // Wait for Reader to render sentence cards
      composeRule.waitUntil(timeoutMillis = 10_000L) {
        composeRule.onAllNodesWithTag("reader_sentence_card_0").fetchSemanticsNodes().isNotEmpty()
      }
      composeRule.waitForIdle()

      // Click "ثبت مطالعه" directly on sentence index 2 (3rd sentence)
      val targetSentence = bundle.sentences[2]
      composeRule.onNodeWithTag("reader_lazy_column").performScrollToNode(hasTestTag("sentence_mark_read_2"))
      composeRule.onNodeWithTag("sentence_mark_read_2").performClick()
      composeRule.waitUntil(timeoutMillis = 10_000L) {
        composeRule.onAllNodesWithText("پیشرفت مطالعه: 1 از", substring = true).fetchSemanticsNodes().isNotEmpty() &&
          vm.uiState.value.snapshot?.progress?.completedSentenceIds?.contains(targetSentence.id) == true
      }
      composeRule.waitForIdle()

      val progressAfterThird =
        runBlocking { learningRepo.observeProgressForPackage("story-b1-0001").first()!! }
      assertEquals(1, progressAfterThird.completedSentenceCount)
      assertEquals(setOf(targetSentence.id), progressAfterThird.completedSentenceIds)
      assertFalse(bundle.sentences[0].id in progressAfterThird.completedSentenceIds)
      assertFalse(bundle.sentences[1].id in progressAfterThird.completedSentenceIds)

      // UI check: sentence 0 is NOT marked read, while sentence 2 IS marked read
      composeRule.onNodeWithTag("reader_lazy_column").performScrollToNode(hasTestTag("sentence_mark_read_0"))
      composeRule.onNodeWithTag("sentence_mark_read_0").assertTextContains("ثبت مطالعه")
      composeRule.onNodeWithTag("reader_lazy_column").performScrollToNode(hasTestTag("sentence_mark_read_2"))
      composeRule.onNodeWithTag("sentence_mark_read_2").assertTextContains("خوانده شد")

      // Marking sentence 0 now brings completed count to 2; marking sentence 2 again stays 2
      composeRule.onNodeWithTag("reader_lazy_column").performScrollToNode(hasTestTag("sentence_mark_read_0"))
      composeRule.onNodeWithTag("sentence_mark_read_0").performClick()
      composeRule.waitUntil(timeoutMillis = 10_000L) {
        composeRule.onAllNodesWithText("پیشرفت مطالعه: 2 از", substring = true).fetchSemanticsNodes().isNotEmpty() &&
          vm.uiState.value.snapshot?.progress?.completedSentenceCount == 2
      }
      vm.dismissFeedback()
      composeRule.onNodeWithTag("reader_lazy_column").performScrollToNode(hasTestTag("sentence_mark_read_2"))
      composeRule.onNodeWithTag("sentence_mark_read_2").performClick()
      composeRule.waitUntil(timeoutMillis = 10_000L) {
        ShadowLooper.idleMainLooper()
        vm.uiState.value.feedbackBanner != null
      }
      composeRule.waitForIdle()

      val progressAfterBoth =
        runBlocking { learningRepo.observeProgressForPackage("story-b1-0001").first()!! }
      assertEquals(2, progressAfterBoth.completedSentenceCount)
      assertEquals(setOf(bundle.sentences[0].id, targetSentence.id), progressAfterBoth.completedSentenceIds)
      composeRule.onNodeWithTag("reader_lazy_column").performScrollToNode(hasTestTag("sentence_mark_read_0"))
      composeRule.onNodeWithTag("sentence_mark_read_0").assertTextContains("خوانده شد")
    } finally {
      dsScope.cancel()
      db.close()
      prefsFile.delete()
    }
  }

  @Test
  fun `fix 3 reader vocabulary highlighting matches strictly on lemma plus pos plus senseId`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = BamLinguageDatabase.createInMemory(context)
      try {
        val dispatchers = DefaultDispatcherProvider()
        val contentRepo = RoomContentRepository(db.contentDao(), dispatchers)
        val learningRepo = RoomLearningRepository(db.learningDao(), dispatchers)
        val createCard = CreateSrsCardUseCase(learningRepo)
        val reviewCard = ReviewSrsCardUseCase(learningRepo, SrsScheduler())
        val observeReader = ObserveReaderPackageUseCase(contentRepo, learningRepo)

        val pkgId = "pkg_sense_disambiguation"
        val verbEntryId = "$pkgId::miss_verb"
        val nounEntryId = "$pkgId::miss_noun"
        val verbSense1 =
          GlossarySense(
            id = "$verbEntryId::sense-1",
            glossaryEntryId = verbEntryId,
            sourceSenseId = "sense-1",
            orderIndex = 0,
            meaningFa = "جا ماندن از",
            definitionEn = "To fail to catch a train or bus.",
            examples = listOf(GlossaryExample("Sara missed her train.", "سارا از قطارش جا ماند.")),
          )
        val verbSense2 =
          GlossarySense(
            id = "$verbEntryId::sense-2",
            glossaryEntryId = verbEntryId,
            sourceSenseId = "sense-2",
            orderIndex = 1,
            meaningFa = "دلتنگ شدن",
            definitionEn = "To feel sad because family or friends are away.",
            examples = listOf(GlossaryExample("I miss my family.", "دلم برای خانواده‌ام تنگ شده است.")),
          )
        val nounSense1 =
          GlossarySense(
            id = "$nounEntryId::sense-1",
            glossaryEntryId = nounEntryId,
            sourceSenseId = "sense-1",
            orderIndex = 0,
            meaningFa = "دوشیزه / خطا",
            definitionEn = "A failure to hit or catch.",
            examples = listOf(GlossaryExample("That miss cost the match.", "آن خطا باعث باخت شد.")),
          )

        val verbEntry =
          GlossaryEntry(
            id = verbEntryId,
            packageId = pkgId,
            sourceGlossaryId = "miss_verb",
            term = "miss",
            normalizedTerm = "miss",
            lemma = "miss",
            forms = listOf("miss", "misses", "missed", "missing"),
            partOfSpeech = "verb",
            cefr = CefrLevel.A2,
            senses = listOf(verbSense1, verbSense2),
            phonetic = "/mɪs/",
            pronunciation = PronunciationInfo(us = "/mɪs/"),
            persianMeaning = "جا ماندن از",
            englishDefinition = verbSense1.definitionEn,
            exampleSentence = "Sara missed her train.",
            sentenceIds = listOf("1", "2"),
          )
        val nounEntry =
          GlossaryEntry(
            id = nounEntryId,
            packageId = pkgId,
            sourceGlossaryId = "miss_noun",
            term = "miss",
            normalizedTerm = "miss",
            lemma = "miss",
            forms = listOf("miss", "misses"),
            partOfSpeech = "noun",
            cefr = CefrLevel.B1,
            senses = listOf(nounSense1),
            phonetic = "/mɪs/",
            pronunciation = PronunciationInfo(us = "/mɪs/"),
            persianMeaning = "خطا",
            englishDefinition = nounSense1.definitionEn,
            exampleSentence = "The miss was costly.",
            sentenceIds = listOf("3"),
          )

        val s1 = Sentence("$pkgId::1", pkgId, "1", 0, null, "Sara missed her train.", "سارا از قطارش جا ماند.")
        val s2 = Sentence("$pkgId::2", pkgId, "2", 1, null, "I miss my family.", "دلم برای خانواده‌ام تنگ شده.")
        val s3 = Sentence("$pkgId::3", pkgId, "3", 2, null, "The miss was costly.", "آن خطا گران تمام شد.")

        val bundle =
          ContentPackageBundle(
            contentPackage =
              ContentPackage(
                packageId = pkgId,
                schemaVersion = 1,
                contentVersion = 1,
                contentType = ContentType.STORY,
                level = CefrLevel.B1,
                topic = "test",
                title = "Sense Test",
                titleFa = "آزمون معنا",
                description = "Test",
                descriptionFa = "آزمون",
                metadata =
                  PackageMetadata(
                    author = "BamLinguage",
                    estimatedMinutes = 3,
                    language = "en",
                    translationLanguage = "fa",
                  ),
                importedAtEpochMs = 1_000L,
              ),
            speakers = emptyList(),
            sentences = listOf(s1, s2, s3),
            glossaryEntries = listOf(verbEntry, nounEntry),
            wordOccurrences =
              listOf(
                WordOccurrence(0, pkgId, s1.id, verbEntryId, "missed", 5, 11),
                WordOccurrence(0, pkgId, s2.id, verbEntryId, "miss", 2, 6),
                WordOccurrence(0, pkgId, s3.id, nounEntryId, "miss", 4, 8),
              ),
            questions = emptyList(),
          )
        contentRepo.replacePackageTransactionally(bundle)

        // Add ONLY (lemma="miss", pos="verb", senseId="sense-1") and review to LEARNING
        val now = 1_700_000_000_000L
        val created =
          createCard.createWordCardFromGlossary(verbEntry, verbSense1, s1, "Sense Test", now)
            as CreateSrsCardResult.Created
        reviewCard(created.card, ReviewQuality.GOOD, now)

        val snapshot = observeReader(pkgId) { now + 1_000L }.first()!!
        val s1Highlight = snapshot.tokenHighlightsBySentenceId[s1.id]!!.first { it.surface == "missed" }
        val s2Highlight = snapshot.tokenHighlightsBySentenceId[s2.id]!!.first { it.surface == "miss" }
        val s3Highlight = snapshot.tokenHighlightsBySentenceId[s3.id]!!.first { it.surface == "miss" }

        // Exact match (miss + verb + sense-1) highlights as LEARNING
        assertEquals(ReaderWordStatus.LEARNING, s1Highlight.status)
        assertEquals(created.card.cardId, s1Highlight.linkedCardId)

        // Different senseId (miss + verb + sense-2) does NOT inherit sense-1's SRS status
        assertEquals(ReaderWordStatus.NEW, s2Highlight.status)
        assertNull(s2Highlight.linkedCardId)

        // Different POS (miss + noun + sense-1) does NOT inherit verb's SRS status
        assertEquals(ReaderWordStatus.NEW, s3Highlight.status)
        assertNull(s3Highlight.linkedCardId)
      } finally {
        db.close()
      }
    }

  @Test
  fun `fix 4 duplicate SRS cards are prevented under simultaneous requests and enforced at SQLite level`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = BamLinguageDatabase.createInMemory(context)
      try {
        val dispatchers = DefaultDispatcherProvider()
        val learningRepo = RoomLearningRepository(db.learningDao(), dispatchers)
        val createCard = CreateSrsCardUseCase(learningRepo)

        val sense = GlossarySense("pkg::run::sense-1", "pkg::run", "sense-1", 0, "دویدن", "To move fast on foot.")
        val entry =
          GlossaryEntry(
            id = "pkg::run",
            packageId = "pkg",
            sourceGlossaryId = "run-verb-001",
            term = "run",
            normalizedTerm = "run",
            lemma = "run",
            forms = listOf("run", "runs", "running", "ran"),
            partOfSpeech = "verb",
            cefr = CefrLevel.A1,
            senses = listOf(sense),
            phonetic = "/rʌn/",
            pronunciation = PronunciationInfo(us = "/rʌn/"),
            persianMeaning = "دویدن",
            englishDefinition = "To move fast on foot.",
            exampleSentence = "They run every morning.",
          )
        val sentence = Sentence("pkg::1", "pkg", "1", 0, null, "They run every morning.", "آن‌ها هر صبح می‌دوند.")

        // 12 concurrent word creation calls
        val wordResults =
          coroutineScope {
            (1..12).map {
              async(Dispatchers.IO) {
                createCard.createWordCardFromGlossary(entry, sense, sentence, "Story", 5_000L + it)
              }
            }.awaitAll()
          }
        assertEquals(1, wordResults.count { it is CreateSrsCardResult.Created })
        assertEquals(11, wordResults.count { it is CreateSrsCardResult.AlreadyExists })
        val winningCardId = (wordResults.first { it is CreateSrsCardResult.Created } as CreateSrsCardResult.Created).card.cardId
        assertTrue(
          wordResults.filterIsInstance<CreateSrsCardResult.AlreadyExists>().all {
            it.existingCard.cardId == winningCardId
          }
        )

        // 8 concurrent sentence creation calls
        val sentenceResults =
          coroutineScope {
            (1..8).map {
              async(Dispatchers.IO) {
                createCard.createSentenceCard(sentence, "Story", 9_000L + it)
              }
            }.awaitAll()
          }
        assertEquals(1, sentenceResults.count { it is CreateSrsCardResult.Created })
        assertEquals(7, sentenceResults.count { it is CreateSrsCardResult.AlreadyExists })

        // Verify raw database row counts
        assertEquals(2, learningRepo.observeAllSrsCards().first().size)

        // Verify SQLite-level UNIQUE constraint rejects a raw duplicate contentId even with a new cardId
        val duplicateContentId = "word:run:verb:sense-1"
        var constraintThrown = false
        try {
          db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO srs_cards (
              cardId, cardType, contentId, frontTerm, backPersianMeaning, englishDefinition,
              normalizedLemma, pos, senseId, phonetic, exampleSentence, contextSentenceEn, contextSentenceFa,
              sourcePackageId, sourcePackageTitle, sourceGlossaryId, sourceSentenceId,
              state, repetitions, intervalDays, easeFactor, dueAtEpochMs, lastReviewedAtEpochMs, createdAtEpochMs
            ) VALUES (
              'card_forced_dup', 'WORD', '$duplicateContentId', 'run', 'دویدن', null,
              'run', 'verb', 'sense-1', null, null, null, null,
              'pkg', 'Story', 'pkg::run', null,
              'NEW', 0, 0, 2.5, 1000, null, 1000
            )
            """.trimIndent()
          )
        } catch (_: SQLiteConstraintException) {
          constraintThrown = true
        }
        assertTrue("Expected SQLiteConstraintException on duplicate contentId", constraintThrown)
        assertEquals(2, learningRepo.observeAllSrsCards().first().size)
      } finally {
        db.close()
      }
    }

  @Test
  fun `migration 2 to 3 deduplicates contentId, creates unique index, and adds completedSentenceIds`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbFile = File(context.cacheDir, "migration_2_3_${System.nanoTime()}.db")
    if (dbFile.exists()) dbFile.delete()

    val v2Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    try {
      v2Db.execSQL(
        """
        CREATE TABLE content_packages (
          packageId TEXT NOT NULL PRIMARY KEY, schemaVersion INTEGER NOT NULL, contentVersion INTEGER NOT NULL,
          contentType TEXT NOT NULL, level TEXT NOT NULL, topic TEXT NOT NULL, title TEXT NOT NULL,
          titleFa TEXT NOT NULL, description TEXT NOT NULL, descriptionFa TEXT NOT NULL, author TEXT NOT NULL,
          createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, wordCount INTEGER NOT NULL,
          sentenceCountMeta INTEGER NOT NULL, glossaryCountMeta INTEGER NOT NULL, estimatedMinutes INTEGER NOT NULL,
          language TEXT NOT NULL, translationLanguage TEXT NOT NULL, tags TEXT NOT NULL,
          importedAtEpochMs INTEGER NOT NULL, isBundledSample INTEGER NOT NULL
        )
        """.trimIndent()
      )
      v2Db.execSQL("CREATE INDEX index_content_packages_level ON content_packages (level)")
      v2Db.execSQL("CREATE INDEX index_content_packages_contentType ON content_packages (contentType)")
      v2Db.execSQL("CREATE INDEX index_content_packages_importedAtEpochMs ON content_packages (importedAtEpochMs)")
      v2Db.execSQL("CREATE TABLE speakers (id TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, sourceSpeakerId TEXT NOT NULL, name TEXT NOT NULL, nameFa TEXT NOT NULL, role TEXT NOT NULL, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE)")
      v2Db.execSQL("CREATE INDEX index_speakers_packageId ON speakers (packageId)")
      v2Db.execSQL("CREATE TABLE sentences (id TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, sourceSentenceId TEXT NOT NULL, orderIndex INTEGER NOT NULL, speakerId TEXT, englishText TEXT NOT NULL, persianTranslation TEXT NOT NULL, phoneticHint TEXT, grammarNoteFa TEXT, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE, FOREIGN KEY(speakerId) REFERENCES speakers(id) ON DELETE SET NULL)")
      v2Db.execSQL("CREATE UNIQUE INDEX index_sentences_packageId_orderIndex ON sentences (packageId, orderIndex)")
      v2Db.execSQL("CREATE INDEX index_sentences_speakerId ON sentences (speakerId)")
      v2Db.execSQL("CREATE TABLE glossary_entries (id TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, sourceGlossaryId TEXT NOT NULL, term TEXT NOT NULL, normalizedTerm TEXT NOT NULL, lemma TEXT NOT NULL, forms TEXT NOT NULL, partOfSpeech TEXT NOT NULL, cefr TEXT NOT NULL, phonetic TEXT, pronunciationUk TEXT, pronunciationUs TEXT, persianMeaning TEXT NOT NULL, englishDefinition TEXT NOT NULL, exampleSentence TEXT, synonyms TEXT NOT NULL, antonyms TEXT NOT NULL, sentenceIds TEXT NOT NULL, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE)")
      v2Db.execSQL("CREATE INDEX index_glossary_entries_packageId_normalizedTerm ON glossary_entries (packageId, normalizedTerm)")
      v2Db.execSQL("CREATE INDEX index_glossary_entries_normalizedTerm ON glossary_entries (normalizedTerm)")
      v2Db.execSQL("CREATE INDEX index_glossary_entries_lemma ON glossary_entries (lemma)")
      v2Db.execSQL("CREATE TABLE glossary_senses (id TEXT NOT NULL PRIMARY KEY, glossaryEntryId TEXT NOT NULL, packageId TEXT NOT NULL, sourceSenseId TEXT NOT NULL, orderIndex INTEGER NOT NULL, meaningFa TEXT NOT NULL, definitionEn TEXT NOT NULL, examplesJson TEXT NOT NULL, FOREIGN KEY(glossaryEntryId) REFERENCES glossary_entries(id) ON DELETE CASCADE, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE)")
      v2Db.execSQL("CREATE INDEX index_glossary_senses_glossaryEntryId ON glossary_senses (glossaryEntryId)")
      v2Db.execSQL("CREATE INDEX index_glossary_senses_packageId ON glossary_senses (packageId)")
      v2Db.execSQL("CREATE TABLE word_occurrences (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, packageId TEXT NOT NULL, sentenceId TEXT NOT NULL, glossaryEntryId TEXT NOT NULL, surfaceForm TEXT NOT NULL, startOffset INTEGER NOT NULL, endOffset INTEGER NOT NULL, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE, FOREIGN KEY(sentenceId) REFERENCES sentences(id) ON DELETE CASCADE, FOREIGN KEY(glossaryEntryId) REFERENCES glossary_entries(id) ON DELETE CASCADE)")
      v2Db.execSQL("CREATE INDEX index_word_occurrences_packageId ON word_occurrences (packageId)")
      v2Db.execSQL("CREATE INDEX index_word_occurrences_sentenceId ON word_occurrences (sentenceId)")
      v2Db.execSQL("CREATE INDEX index_word_occurrences_glossaryEntryId ON word_occurrences (glossaryEntryId)")
      v2Db.execSQL("CREATE TABLE questions (id TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, sourceQuestionId TEXT NOT NULL, orderIndex INTEGER NOT NULL, promptEn TEXT NOT NULL, promptFa TEXT NOT NULL, options TEXT NOT NULL, correctOptionIndex INTEGER NOT NULL, explanationFa TEXT, FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON DELETE CASCADE)")
      v2Db.execSQL("CREATE UNIQUE INDEX index_questions_packageId_orderIndex ON questions (packageId, orderIndex)")

      v2Db.execSQL(
        """
        CREATE TABLE srs_cards (
          cardId TEXT NOT NULL PRIMARY KEY, cardType TEXT NOT NULL, contentId TEXT NOT NULL,
          frontTerm TEXT NOT NULL, backPersianMeaning TEXT NOT NULL, englishDefinition TEXT,
          normalizedLemma TEXT NOT NULL, pos TEXT NOT NULL, senseId TEXT NOT NULL,
          phonetic TEXT, exampleSentence TEXT, contextSentenceEn TEXT, contextSentenceFa TEXT,
          sourcePackageId TEXT, sourcePackageTitle TEXT, sourceGlossaryId TEXT, sourceSentenceId TEXT,
          state TEXT NOT NULL, repetitions INTEGER NOT NULL, intervalDays INTEGER NOT NULL,
          easeFactor REAL NOT NULL, dueAtEpochMs INTEGER NOT NULL, lastReviewedAtEpochMs INTEGER,
          createdAtEpochMs INTEGER NOT NULL
        )
        """.trimIndent()
      )
      v2Db.execSQL("CREATE INDEX index_srs_cards_dueAtEpochMs ON srs_cards (dueAtEpochMs)")
      v2Db.execSQL("CREATE INDEX index_srs_cards_state ON srs_cards (state)")
      v2Db.execSQL("CREATE INDEX index_srs_cards_sourcePackageId ON srs_cards (sourcePackageId)")
      v2Db.execSQL("CREATE INDEX index_srs_cards_frontTerm ON srs_cards (frontTerm)")
      v2Db.execSQL("CREATE INDEX index_srs_cards_contentId ON srs_cards (contentId)")
      v2Db.execSQL("CREATE INDEX index_srs_cards_cardType ON srs_cards (cardType)")

      v2Db.execSQL("CREATE TABLE srs_review_logs (logId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, cardId TEXT NOT NULL, qualityScore INTEGER NOT NULL, previousIntervalDays INTEGER NOT NULL, newIntervalDays INTEGER NOT NULL, previousEaseFactor REAL NOT NULL, newEaseFactor REAL NOT NULL, reviewedAtEpochMs INTEGER NOT NULL, FOREIGN KEY(cardId) REFERENCES srs_cards(cardId) ON DELETE CASCADE)")
      v2Db.execSQL("CREATE INDEX index_srs_review_logs_cardId ON srs_review_logs (cardId)")
      v2Db.execSQL("CREATE INDEX index_srs_review_logs_reviewedAtEpochMs ON srs_review_logs (reviewedAtEpochMs)")
      v2Db.execSQL("CREATE TABLE learning_progress (progressId TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, packageTitleSnapshot TEXT NOT NULL, completedSentenceCount INTEGER NOT NULL, totalSentenceCount INTEGER NOT NULL, correctQuestionsCount INTEGER NOT NULL, totalQuestionsCount INTEGER NOT NULL, lastReadSentenceIndex INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL)")
      v2Db.execSQL("CREATE UNIQUE INDEX index_learning_progress_packageId ON learning_progress (packageId)")
      v2Db.execSQL("CREATE INDEX index_learning_progress_updatedAtEpochMs ON learning_progress (updatedAtEpochMs)")
      v2Db.execSQL("CREATE TABLE user_vocabulary (vocabId TEXT NOT NULL PRIMARY KEY, normalizedTerm TEXT NOT NULL, displayTerm TEXT NOT NULL, persianMeaning TEXT NOT NULL, phonetic TEXT, notesFa TEXT, sourcePackageId TEXT, masteryLevel INTEGER NOT NULL, savedAtEpochMs INTEGER NOT NULL)")
      v2Db.execSQL("CREATE UNIQUE INDEX index_user_vocabulary_normalizedTerm ON user_vocabulary (normalizedTerm)")
      v2Db.execSQL("CREATE INDEX index_user_vocabulary_sourcePackageId ON user_vocabulary (sourcePackageId)")
      v2Db.execSQL("CREATE TABLE reader_bookmarks (bookmarkId TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, packageTitleSnapshot TEXT NOT NULL, sentenceId TEXT NOT NULL, sentenceOrderIndex INTEGER NOT NULL, noteFa TEXT, createdAtEpochMs INTEGER NOT NULL)")
      v2Db.execSQL("CREATE UNIQUE INDEX index_reader_bookmarks_packageId_sentenceId ON reader_bookmarks (packageId, sentenceId)")
      v2Db.execSQL("CREATE INDEX index_reader_bookmarks_createdAtEpochMs ON reader_bookmarks (createdAtEpochMs)")

      // Seed duplicate contentId rows + progress in v2
      v2Db.execSQL("INSERT INTO srs_cards VALUES ('dup_1', 'WORD', 'word:go:verb:sense-1', 'go', 'رفتن', null, 'go', 'verb', 'sense-1', null, null, null, null, null, null, null, null, 'NEW', 0, 0, 2.5, 1000, null, 1000)")
      v2Db.execSQL("INSERT INTO srs_cards VALUES ('dup_2', 'WORD', 'word:go:verb:sense-1', 'go', 'رفتن', null, 'go', 'verb', 'sense-1', null, null, null, null, null, null, null, null, 'NEW', 0, 0, 2.5, 1000, null, 1001)")
      v2Db.execSQL("INSERT INTO learning_progress VALUES ('prog_p1', 'p1', 'Story', 1, 5, 0, 1, 0, 1000)")
      v2Db.version = 2
    } finally {
      v2Db.close()
    }

    val migratedDb =
      Room.databaseBuilder(context, BamLinguageDatabase::class.java, dbFile.absolutePath)
        .addMigrations(
          BamLinguageDatabase.MIGRATION_1_2,
          BamLinguageDatabase.MIGRATION_2_3,
          BamLinguageDatabase.MIGRATION_3_4,
        )
        .allowMainThreadQueries()
        .build()
    try {
      runTest {
        val repo = RoomLearningRepository(migratedDb.learningDao(), DefaultDispatcherProvider())
        val cards = repo.observeAllSrsCards().first()
        assertEquals(1, cards.size)
        assertEquals("dup_1", cards.first().cardId)
        val progress = repo.observeProgressForPackage("p1").first()
        assertNotNull(progress)
        assertTrue(progress!!.completedSentenceIds.isEmpty())
      }
    } finally {
      migratedDb.close()
      dbFile.delete()
    }
  }

  private class NoOpTtsEngine : TextToSpeechEngine {
    private val _status = MutableStateFlow<TtsStatus>(TtsStatus.Ready)
    override val status: StateFlow<TtsStatus> = _status
    private val _voices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    override val availableVoices: StateFlow<List<TtsVoiceOption>> = _voices

    override fun refreshVoices() {}

    override fun speakWord(
      word: String,
      speechRate: Float,
      accent: com.example.core.common.TtsAccent,
      preferredVoiceName: String?,
    ) {}

    override fun speakSentence(
      text: String,
      itemId: String?,
      speechRate: Float,
      accent: com.example.core.common.TtsAccent,
      preferredVoiceName: String?,
    ) {}

    override fun speakParagraph(
      items: List<com.example.core.tts.TtsSpeechItem>,
      speechRate: Float,
      accent: com.example.core.common.TtsAccent,
      speakerVoiceMap: Map<String, String>,
      defaultVoiceName: String?,
    ) {}

    override fun pause() {}

    override fun resume() {}

    override fun stop() {}

    override fun shutdown() {}
  }
}
