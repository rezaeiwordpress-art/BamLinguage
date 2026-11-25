package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.content.importer.JsonContentPackageImporter
import com.example.content.validation.ContentPackageValidator
import com.example.core.common.CefrLevel
import com.example.core.common.DefaultDispatcherProvider
import com.example.core.common.ThemeMode
import com.example.core.common.TtsAccent
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.tts.AndroidTextToSpeechEngine
import com.example.data.local.database.BamLinguageDatabase
import com.example.data.repository.RoomContentRepository
import com.example.data.repository.RoomLearningRepository
import com.example.domain.model.GlossaryEntry
import com.example.domain.model.GlossarySense
import com.example.domain.model.ImportValidationErrorCode
import com.example.domain.model.PackageImportResult
import com.example.domain.model.PronunciationInfo
import com.example.domain.model.ReaderWordStatus
import com.example.domain.model.ReviewQuality
import com.example.domain.model.Sentence
import com.example.domain.model.SrsCardType
import com.example.domain.srs.SrsScheduler
import com.example.domain.usecase.CreateSrsCardResult
import com.example.domain.usecase.CreateSrsCardUseCase
import com.example.domain.usecase.DeleteSrsCardUseCase
import com.example.domain.usecase.DictionaryLookupUseCase
import com.example.domain.usecase.EnsureBundledSamplePackageUseCase
import com.example.domain.usecase.ObserveReaderPackageUseCase
import com.example.domain.usecase.ReviewSrsCardUseCase
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2DomainAndDbRobolectricTest {

  @Test
  fun `both bundled sample packages pass strict JSON schema and UTF-16 offset validation and import cleanly`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = BamLinguageDatabase.createInMemory(context)
      try {
        val dispatchers = DefaultDispatcherProvider()
        val contentRepo = RoomContentRepository(db.contentDao(), dispatchers)
        val validator = ContentPackageValidator()
        val importer = JsonContentPackageImporter(contentRepo, validator)

        for (assetPath in EnsureBundledSamplePackageUseCase.SAMPLE_ASSET_PATHS) {
          val rawJson = context.assets.open(assetPath).bufferedReader().use { it.readText() }
          val outcome = importer.importFromJson(rawJson, isBundledSample = true)
          assertTrue("Expected $assetPath to import cleanly, got $outcome", outcome is PackageImportResult.Success)
        }

        assertEquals(2, contentRepo.getPackageCount())
        val lateTrainBundle = contentRepo.getPackageBundle("story-b1-0001")
        assertNotNull(lateTrainBundle)
        assertTrue(lateTrainBundle!!.glossaryEntries.any { it.lemma == "miss" && it.senses.size >= 2 })
        assertTrue(lateTrainBundle.glossaryEntries.any { it.term == "run out of" && it.isMultiword })

        // Duplicate same-version import must fail cleanly
        val duplicateJson =
          context.assets.open(EnsureBundledSamplePackageUseCase.SAMPLE_ASSET_PATH).bufferedReader().use { it.readText() }
        val dupResult = importer.importFromJson(duplicateJson, allowSameVersionOverwrite = false)
        assertTrue(dupResult is PackageImportResult.Failure)
        assertEquals(
          ImportValidationErrorCode.PACKAGE_VERSION_CONFLICT,
          (dupResult as PackageImportResult.Failure).errors.first().code,
        )
      } finally {
        db.close()
      }
    }

  @Test
  fun `dictionary lookup searches exact terms, inflected forms, lemmas, and Persian meanings`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = BamLinguageDatabase.createInMemory(context)
      try {
        val dispatchers = DefaultDispatcherProvider()
        val contentRepo = RoomContentRepository(db.contentDao(), dispatchers)
        val learningRepo = RoomLearningRepository(db.learningDao(), dispatchers)
        val importer = JsonContentPackageImporter(contentRepo)
        val lookup = DictionaryLookupUseCase(contentRepo, learningRepo)

        val storyJson =
          context.assets.open("sample_packages/a_late_train_story.json").bufferedReader().use { it.readText() }
        assertTrue(importer.importFromJson(storyJson, isBundledSample = true) is PackageImportResult.Success)

        val missResults = lookup("miss").glossaryMatches
        assertTrue(missResults.any { it.lemma == "miss" })

        val trainsResults = lookup("trains").glossaryMatches
        assertTrue(trainsResults.any { it.lemma == "train" })

        val persianResults = lookup("تسلیم").glossaryMatches
        assertTrue(persianResults.any { it.term == "give up" })
      } finally {
        db.close()
      }
    }

  @Test
  fun `duplicate SRS prevention blocks same lemma-pos-sense across stories while allowing distinct senses and safe deletion`() =
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
        val deleteCard = DeleteSrsCardUseCase(learningRepo)

        val storyJson =
          context.assets.open("sample_packages/a_late_train_story.json").bufferedReader().use { it.readText() }
        assertTrue(importer.importFromJson(storyJson, isBundledSample = true) is PackageImportResult.Success)

        val sense1 = GlossarySense("s1", "story1::miss", "sense-1", 0, "از دست دادن", "To fail to catch.")
        val sense2 = GlossarySense("s2", "story2::miss", "sense-2", 1, "دلتنگ شدن", "To feel sad.")

        val entryStory1 =
          GlossaryEntry(
            id = "story1::miss",
            packageId = "story-b1-0001",
            sourceGlossaryId = "miss",
            term = "missed",
            normalizedTerm = "missed",
            lemma = "miss",
            forms = listOf("miss", "missed"),
            partOfSpeech = "verb",
            cefr = CefrLevel.A2,
            senses = listOf(sense1, sense2),
            phonetic = "/mɪst/",
            pronunciation = PronunciationInfo(uk = "/mɪst/", us = "/mɪst/"),
            persianMeaning = "از دست دادن",
            englishDefinition = "To fail to catch.",
            exampleSentence = "She missed her train.",
          )
        val entryStory2 = entryStory1.copy(id = "story2::miss", packageId = "other-story")

        val sentence =
          Sentence(
            id = "story-b1-0001::1",
            packageId = "story-b1-0001",
            sourceSentenceId = "1",
            orderIndex = 0,
            speakerId = null,
            englishText = "Sara missed her train again.",
            persianTranslation = "سارا دوباره از قطارش جا ماند.",
          )

        val firstCreate = createCard.createWordCardFromGlossary(entryStory1, sense1, sentence, "A Late Train", 1_000L)
        assertTrue(firstCreate is CreateSrsCardResult.Created)

        // Same lemma + pos + senseId from another story must NOT create a duplicate
        val duplicateCreate = createCard.createWordCardFromGlossary(entryStory2, sense1, sentence, "Other Story", 2_000L)
        assertTrue(duplicateCreate is CreateSrsCardResult.AlreadyExists)
        assertEquals(1, learningRepo.observeAllSrsCards().first().size)

        // Different sense creates a separate card
        val secondSenseCreate = createCard.createWordCardFromGlossary(entryStory2, sense2, sentence, "Other Story", 2_500L)
        assertTrue(secondSenseCreate is CreateSrsCardResult.Created)
        assertEquals(2, learningRepo.observeAllSrsCards().first().size)

        // Sentence card duplicate prevention
        val sentCard1 = createCard.createSentenceCard(sentence, "A Late Train", 3_000L)
        val sentCard2 = createCard.createSentenceCard(sentence, "A Late Train", 4_000L)
        assertTrue(sentCard1 is CreateSrsCardResult.Created)
        assertTrue(sentCard2 is CreateSrsCardResult.AlreadyExists)

        // Review first card, then delete it: review log cascades out, content package remains untouched
        val createdWordCard = (firstCreate as CreateSrsCardResult.Created).card
        reviewCard(createdWordCard, ReviewQuality.GOOD, 5_000L)
        assertEquals(1, learningRepo.observeRecentReviewLogs().first().size)

        deleteCard(createdWordCard.cardId)
        val remainingCards = learningRepo.observeAllSrsCards().first()
        assertEquals(2, remainingCards.size)
        assertEquals(0, learningRepo.observeRecentReviewLogs().first().size)
        assertNotNull(contentRepo.getPackageBundle("story-b1-0001"))

        // Deleting content package leaves remaining SRS cards intact
        contentRepo.deletePackage("story-b1-0001")
        assertEquals(0, contentRepo.getPackageCount())
        assertEquals(2, learningRepo.observeAllSrsCards().first().size)
      } finally {
        db.close()
      }
    }

  @Test
  fun `observe reader package reactively updates token color states across SRS lifecycle`() =
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
        var nowClock = 1_700_000_000_000L
        val observeReader = ObserveReaderPackageUseCase(contentRepo, learningRepo)

        val storyJson =
          context.assets.open("sample_packages/a_late_train_story.json").bufferedReader().use { it.readText() }
        assertTrue(importer.importFromJson(storyJson, isBundledSample = true) is PackageImportResult.Success)

        val initialSnapshot = observeReader("story-b1-0001") { nowClock }.first()!!
        val sentence1Id = initialSnapshot.bundle.sentences.first().id
        val missedHighlight1 =
          initialSnapshot.tokenHighlightsBySentenceId[sentence1Id]!!.first { it.surface == "missed" }
        assertEquals(ReaderWordStatus.NEW, missedHighlight1.status)

        // Add "missed" to SRS and review once (GOOD -> LEARNING)
        val missEntry = initialSnapshot.bundle.glossaryEntries.first { it.lemma == "miss" }
        val created =
          createCard.createWordCardFromGlossary(
            entry = missEntry,
            selectedSense = missEntry.senses.first(),
            contextSentence = initialSnapshot.bundle.sentences.first(),
            packageTitle = initialSnapshot.bundle.contentPackage.title,
            nowEpochMs = nowClock,
          ) as CreateSrsCardResult.Created

        val afterFirstReview = reviewCard(created.card, ReviewQuality.GOOD, nowClock).updatedCard
        val learningSnapshot = observeReader("story-b1-0001") { nowClock + 1_000L }.first()!!
        assertEquals(
          ReaderWordStatus.LEARNING,
          learningSnapshot.tokenHighlightsBySentenceId[sentence1Id]!!.first { it.surface == "missed" }.status,
        )

        // Second GOOD -> REVIEW
        nowClock += SrsScheduler.MILLIS_PER_DAY
        val afterSecondReview = reviewCard(afterFirstReview, ReviewQuality.GOOD, nowClock).updatedCard
        val reviewSnapshot = observeReader("story-b1-0001") { nowClock + 1_000L }.first()!!
        assertEquals(
          ReaderWordStatus.REVIEW,
          reviewSnapshot.tokenHighlightsBySentenceId[sentence1Id]!!.first { it.surface == "missed" }.status,
        )

        // Fast-forward past dueAtEpochMs -> DUE
        val dueClock = afterSecondReview.dueAtEpochMs + 60_000L
        val dueSnapshot = observeReader("story-b1-0001") { dueClock }.first()!!
        assertEquals(
          ReaderWordStatus.DUE,
          dueSnapshot.tokenHighlightsBySentenceId[sentence1Id]!!.first { it.surface == "missed" }.status,
        )
      } finally {
        db.close()
      }
    }

  @Test
  fun `room migration 1 to 2 upgrades v1 schema cleanly and preserves existing content and SRS cards`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbFile = File(context.cacheDir, "migration_test_${System.nanoTime()}.db")
    if (dbFile.exists()) dbFile.delete()

    val v1Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    try {
      v1Db.execSQL(
        """
        CREATE TABLE content_packages (
          packageId TEXT NOT NULL PRIMARY KEY,
          schemaVersion INTEGER NOT NULL,
          contentVersion INTEGER NOT NULL,
          contentType TEXT NOT NULL,
          level TEXT NOT NULL,
          topic TEXT NOT NULL,
          title TEXT NOT NULL,
          titleFa TEXT NOT NULL,
          description TEXT NOT NULL,
          descriptionFa TEXT NOT NULL,
          author TEXT NOT NULL,
          estimatedMinutes INTEGER NOT NULL,
          language TEXT NOT NULL,
          translationLanguage TEXT NOT NULL,
          tags TEXT NOT NULL,
          importedAtEpochMs INTEGER NOT NULL,
          isBundledSample INTEGER NOT NULL
        )
        """.trimIndent()
      )
      v1Db.execSQL("CREATE INDEX index_content_packages_level ON content_packages (level)")
      v1Db.execSQL("CREATE INDEX index_content_packages_contentType ON content_packages (contentType)")
      v1Db.execSQL("CREATE INDEX index_content_packages_importedAtEpochMs ON content_packages (importedAtEpochMs)")

      v1Db.execSQL(
        """
        CREATE TABLE speakers (
          id TEXT NOT NULL PRIMARY KEY,
          packageId TEXT NOT NULL,
          sourceSpeakerId TEXT NOT NULL,
          name TEXT NOT NULL,
          nameFa TEXT NOT NULL,
          role TEXT NOT NULL,
          FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
      )
      v1Db.execSQL("CREATE INDEX index_speakers_packageId ON speakers (packageId)")

      v1Db.execSQL(
        """
        CREATE TABLE sentences (
          id TEXT NOT NULL PRIMARY KEY,
          packageId TEXT NOT NULL,
          sourceSentenceId TEXT NOT NULL,
          orderIndex INTEGER NOT NULL,
          speakerId TEXT,
          englishText TEXT NOT NULL,
          persianTranslation TEXT NOT NULL,
          phoneticHint TEXT,
          grammarNoteFa TEXT,
          FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON UPDATE NO ACTION ON DELETE CASCADE,
          FOREIGN KEY(speakerId) REFERENCES speakers(id) ON UPDATE NO ACTION ON DELETE SET NULL
        )
        """.trimIndent()
      )
      v1Db.execSQL("CREATE UNIQUE INDEX index_sentences_packageId_orderIndex ON sentences (packageId, orderIndex)")
      v1Db.execSQL("CREATE INDEX index_sentences_speakerId ON sentences (speakerId)")

      v1Db.execSQL(
        """
        CREATE TABLE glossary_entries (
          id TEXT NOT NULL PRIMARY KEY,
          packageId TEXT NOT NULL,
          sourceGlossaryId TEXT NOT NULL,
          term TEXT NOT NULL,
          normalizedTerm TEXT NOT NULL,
          partOfSpeech TEXT NOT NULL,
          phonetic TEXT,
          persianMeaning TEXT NOT NULL,
          englishDefinition TEXT NOT NULL,
          exampleSentence TEXT,
          FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
      )
      v1Db.execSQL("CREATE INDEX index_glossary_entries_packageId_normalizedTerm ON glossary_entries (packageId, normalizedTerm)")
      v1Db.execSQL("CREATE INDEX index_glossary_entries_normalizedTerm ON glossary_entries (normalizedTerm)")

      v1Db.execSQL(
        """
        CREATE TABLE word_occurrences (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          packageId TEXT NOT NULL,
          sentenceId TEXT NOT NULL,
          glossaryEntryId TEXT NOT NULL,
          surfaceForm TEXT NOT NULL,
          startOffset INTEGER NOT NULL,
          endOffset INTEGER NOT NULL,
          FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON UPDATE NO ACTION ON DELETE CASCADE,
          FOREIGN KEY(sentenceId) REFERENCES sentences(id) ON UPDATE NO ACTION ON DELETE CASCADE,
          FOREIGN KEY(glossaryEntryId) REFERENCES glossary_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
      )
      v1Db.execSQL("CREATE INDEX index_word_occurrences_packageId ON word_occurrences (packageId)")
      v1Db.execSQL("CREATE INDEX index_word_occurrences_sentenceId ON word_occurrences (sentenceId)")
      v1Db.execSQL("CREATE INDEX index_word_occurrences_glossaryEntryId ON word_occurrences (glossaryEntryId)")

      v1Db.execSQL(
        """
        CREATE TABLE questions (
          id TEXT NOT NULL PRIMARY KEY,
          packageId TEXT NOT NULL,
          sourceQuestionId TEXT NOT NULL,
          orderIndex INTEGER NOT NULL,
          promptEn TEXT NOT NULL,
          promptFa TEXT NOT NULL,
          options TEXT NOT NULL,
          correctOptionIndex INTEGER NOT NULL,
          explanationFa TEXT,
          FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
      )
      v1Db.execSQL("CREATE UNIQUE INDEX index_questions_packageId_orderIndex ON questions (packageId, orderIndex)")

      v1Db.execSQL(
        """
        CREATE TABLE srs_cards (
          cardId TEXT NOT NULL PRIMARY KEY,
          frontTerm TEXT NOT NULL,
          backPersianMeaning TEXT NOT NULL,
          phonetic TEXT,
          exampleSentence TEXT,
          sourcePackageId TEXT,
          sourcePackageTitle TEXT,
          sourceGlossaryId TEXT,
          sourceSentenceId TEXT,
          state TEXT NOT NULL,
          repetitions INTEGER NOT NULL,
          intervalDays INTEGER NOT NULL,
          easeFactor REAL NOT NULL,
          dueAtEpochMs INTEGER NOT NULL,
          lastReviewedAtEpochMs INTEGER,
          createdAtEpochMs INTEGER NOT NULL
        )
        """.trimIndent()
      )
      v1Db.execSQL("CREATE INDEX index_srs_cards_dueAtEpochMs ON srs_cards (dueAtEpochMs)")
      v1Db.execSQL("CREATE INDEX index_srs_cards_state ON srs_cards (state)")
      v1Db.execSQL("CREATE INDEX index_srs_cards_sourcePackageId ON srs_cards (sourcePackageId)")
      v1Db.execSQL("CREATE INDEX index_srs_cards_frontTerm ON srs_cards (frontTerm)")

      v1Db.execSQL(
        """
        CREATE TABLE srs_review_logs (
          logId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          cardId TEXT NOT NULL,
          qualityScore INTEGER NOT NULL,
          previousIntervalDays INTEGER NOT NULL,
          newIntervalDays INTEGER NOT NULL,
          previousEaseFactor REAL NOT NULL,
          newEaseFactor REAL NOT NULL,
          reviewedAtEpochMs INTEGER NOT NULL,
          FOREIGN KEY(cardId) REFERENCES srs_cards(cardId) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
      )
      v1Db.execSQL("CREATE INDEX index_srs_review_logs_cardId ON srs_review_logs (cardId)")
      v1Db.execSQL("CREATE INDEX index_srs_review_logs_reviewedAtEpochMs ON srs_review_logs (reviewedAtEpochMs)")

      v1Db.execSQL(
        """
        CREATE TABLE learning_progress (
          progressId TEXT NOT NULL PRIMARY KEY,
          packageId TEXT NOT NULL,
          packageTitleSnapshot TEXT NOT NULL,
          completedSentenceCount INTEGER NOT NULL,
          totalSentenceCount INTEGER NOT NULL,
          correctQuestionsCount INTEGER NOT NULL,
          totalQuestionsCount INTEGER NOT NULL,
          lastReadSentenceIndex INTEGER NOT NULL,
          updatedAtEpochMs INTEGER NOT NULL
        )
        """.trimIndent()
      )
      v1Db.execSQL("CREATE UNIQUE INDEX index_learning_progress_packageId ON learning_progress (packageId)")
      v1Db.execSQL("CREATE INDEX index_learning_progress_updatedAtEpochMs ON learning_progress (updatedAtEpochMs)")

      v1Db.execSQL(
        """
        CREATE TABLE user_vocabulary (
          vocabId TEXT NOT NULL PRIMARY KEY,
          normalizedTerm TEXT NOT NULL,
          displayTerm TEXT NOT NULL,
          persianMeaning TEXT NOT NULL,
          phonetic TEXT,
          notesFa TEXT,
          sourcePackageId TEXT,
          masteryLevel INTEGER NOT NULL,
          savedAtEpochMs INTEGER NOT NULL
        )
        """.trimIndent()
      )
      v1Db.execSQL("CREATE UNIQUE INDEX index_user_vocabulary_normalizedTerm ON user_vocabulary (normalizedTerm)")
      v1Db.execSQL("CREATE INDEX index_user_vocabulary_sourcePackageId ON user_vocabulary (sourcePackageId)")

      v1Db.execSQL(
        """
        CREATE TABLE reader_bookmarks (
          bookmarkId TEXT NOT NULL PRIMARY KEY,
          packageId TEXT NOT NULL,
          packageTitleSnapshot TEXT NOT NULL,
          sentenceId TEXT NOT NULL,
          sentenceOrderIndex INTEGER NOT NULL,
          noteFa TEXT,
          createdAtEpochMs INTEGER NOT NULL
        )
        """.trimIndent()
      )
      v1Db.execSQL("CREATE UNIQUE INDEX index_reader_bookmarks_packageId_sentenceId ON reader_bookmarks (packageId, sentenceId)")
      v1Db.execSQL("CREATE INDEX index_reader_bookmarks_createdAtEpochMs ON reader_bookmarks (createdAtEpochMs)")

      v1Db.execSQL(
        """
        INSERT INTO content_packages VALUES (
          'pkg_v1', 1, 1, 'story', 'A2', 'Travel', 'V1 Story', 'داستان نسخه یک',
          'Desc', 'توضیح', 'BamLinguage', 3, 'en', 'fa', '[]', 1000, 1
        )
        """.trimIndent()
      )
      v1Db.execSQL(
        """
        INSERT INTO glossary_entries VALUES (
          'pkg_v1::crisp', 'pkg_v1', 'crisp', 'crisp', 'crisp', 'adjective', '/krɪsp/', 'خنک', 'Fresh and cool', 'Crisp air.'
        )
        """.trimIndent()
      )
      v1Db.execSQL(
        """
        INSERT INTO srs_cards VALUES (
          'card_v1', 'crisp', 'خنک', '/krɪsp/', 'Crisp air.', 'pkg_v1', 'V1 Story', 'pkg_v1::crisp', null,
          'REVIEW', 2, 6, 2.5, 999999, 500000, 1000
        )
        """.trimIndent()
      )
      v1Db.version = 1
    } finally {
      v1Db.close()
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
        val dispatchers = DefaultDispatcherProvider()
        val contentRepo = RoomContentRepository(migratedDb.contentDao(), dispatchers)
        val learningRepo = RoomLearningRepository(migratedDb.learningDao(), dispatchers)

        val pkg = contentRepo.getPackageById("pkg_v1")
        assertNotNull(pkg)
        assertEquals("V1 Story", pkg?.title)

        val glossary = contentRepo.searchGlossary("crisp")
        assertEquals(1, glossary.size)
        assertEquals("crisp", glossary.first().lemma)

        val cards = learningRepo.observeAllSrsCards().first()
        assertEquals(1, cards.size)
        assertEquals(SrsCardType.WORD, cards.first().cardType)
        assertEquals("card_v1", cards.first().contentId)
        assertEquals("crisp", cards.first().normalizedLemma)
      }
    } finally {
      migratedDb.close()
      dbFile.delete()
    }
  }

  @Test
  fun `tts engine handles headless environment gracefully without crashing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val ttsEngine = AndroidTextToSpeechEngine(context)
    ttsEngine.speakWord("missed", speechRate = 1.0f, accent = TtsAccent.US)
    ttsEngine.speakSentence("Sara missed her train again.", itemId = "1", speechRate = 0.9f, accent = TtsAccent.UK)
    ttsEngine.pause()
    ttsEngine.resume()
    ttsEngine.stop()
    ttsEngine.shutdown()
    assertNotNull(ttsEngine.status.value)
  }

  @Test
  fun `datastore persists theme mode, tts accent, tts speed, translation toggle, and daily goal`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val tempFile = File(context.filesDir, "test_prefs_${System.nanoTime()}.preferences_pb")
      val dataStore =
        PreferenceDataStoreFactory.create(
          scope = backgroundScope,
          produceFile = { tempFile },
        )
      val prefsStore = UserPreferencesDataStore(dataStore)

      prefsStore.setThemeMode(ThemeMode.DARK)
      prefsStore.setTtsSpeed(1.25f)
      prefsStore.setTtsAccent(TtsAccent.UK)
      prefsStore.setShowTranslationsInReader(false)
      prefsStore.setDailyGoalCards(30)

      val loaded = prefsStore.preferencesFlow.first()
      assertEquals(ThemeMode.DARK, loaded.themeMode)
      assertEquals(1.25f, loaded.ttsSpeed, 0.01f)
      assertEquals(TtsAccent.UK, loaded.ttsAccent)
      assertEquals(false, loaded.showTranslationsInReader)
      assertEquals(30, loaded.dailyGoalCards)
    }
}
