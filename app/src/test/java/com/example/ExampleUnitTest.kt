package com.example

import com.example.content.schema.ContentPackageJsonDto
import com.example.content.schema.GlossaryEntryJsonDto
import com.example.content.schema.GlossaryExampleJsonDto
import com.example.content.schema.GlossaryOccurrenceJsonDto
import com.example.content.schema.GlossarySenseJsonDto
import com.example.content.schema.PackageMetadataJsonDto
import com.example.content.schema.PronunciationJsonDto
import com.example.content.schema.QuestionJsonDto
import com.example.content.schema.SentenceJsonDto
import com.example.content.schema.SpeakerJsonDto
import com.example.content.validation.ContentIntegrityAnalyzer
import com.example.content.validation.ContentPackageValidator
import com.example.content.validation.ValidationOutcome
import com.example.core.ai.AiAvailability
import com.example.core.ai.AiConfiguration
import com.example.core.ai.AiProvider
import com.example.core.ai.AiProviderType
import com.example.core.ai.AiResult
import com.example.core.ai.AiService
import com.example.core.ai.DisabledOfflineAiProvider
import com.example.core.common.CefrLevel
import com.example.core.common.ContentType
import com.example.domain.model.ContentPackage
import com.example.domain.model.ContentPackageSummary
import com.example.domain.model.GlossaryEntry
import com.example.domain.model.GlossaryExample
import com.example.domain.model.GlossarySense
import com.example.domain.model.ImportValidationErrorCode
import com.example.domain.model.LearningProgress
import com.example.domain.model.PackageMetadata
import com.example.domain.model.PronunciationInfo
import com.example.domain.model.ReaderWordStatus
import com.example.domain.model.ReviewQuality
import com.example.domain.model.Sentence
import com.example.domain.model.SrsCard
import com.example.domain.model.SrsCardState
import com.example.domain.model.SrsCardType
import com.example.domain.model.SrsReviewLog
import com.example.domain.model.WordOccurrence
import com.example.domain.srs.LearningAnalyticsCalculator
import com.example.domain.srs.SrsScheduler
import com.example.domain.vocabulary.MorphologyAnalyzer
import com.example.domain.vocabulary.VocabularyResolutionEngine
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  private val validator = ContentPackageValidator()
  private val resolutionEngine = VocabularyResolutionEngine()
  private val scheduler = SrsScheduler()

  private fun makeEntry(
    id: String,
    term: String,
    lemma: String = term,
    forms: List<String> = listOf(term),
    pos: String,
    cefr: CefrLevel = CefrLevel.A2,
    senses: List<GlossarySense>,
  ): GlossaryEntry =
    GlossaryEntry(
      id = id,
      packageId = "pkg",
      sourceGlossaryId = id.substringAfter("::"),
      term = term,
      normalizedTerm = term.lowercase(),
      lemma = lemma,
      forms = forms,
      partOfSpeech = pos,
      cefr = cefr,
      senses = senses,
      phonetic = null,
      pronunciation = PronunciationInfo(),
      persianMeaning = senses.first().meaningFa,
      englishDefinition = senses.first().definitionEn,
      exampleSentence = senses.first().examples.firstOrNull()?.text,
    )

  private fun validSampleDto(): ContentPackageJsonDto =
    ContentPackageJsonDto(
      schemaVersion = 1,
      id = "story-b1-0001",
      version = 1,
      type = "story",
      title = "A Late Train",
      titleFa = "قطار دیرهنگام",
      description = "A story about missing a train",
      descriptionFa = "داستانی درباره از دست دادن قطار",
      level = "B1",
      topic = "travel",
      language = "en",
      translationLanguage = "fa",
      estimatedMinutes = 5,
      metadata =
        PackageMetadataJsonDto(
          author = "BamLinguage",
          createdAt = "2026-11-24",
          updatedAt = "2026-11-24",
          wordCount = 120,
          sentenceCount = 1,
          glossaryCount = 1,
        ),
      speakers = emptyList(),
      sentences =
        listOf(
          SentenceJsonDto(
            id = "1",
            orderIndex = 0,
            text = "Sara missed her train again.",
            translationFa = "سارا دوباره از قطارش جا ماند.",
            speakerId = null,
          )
        ),
      glossary =
        listOf(
          GlossaryEntryJsonDto(
            id = "missed-verb-001",
            term = "missed",
            lemma = "miss",
            forms = listOf("miss", "misses", "missing", "missed"),
            pos = "verb",
            cefr = "A2",
            senses =
              listOf(
                GlossarySenseJsonDto(
                  id = "sense-1",
                  meaningFa = "از دست دادن",
                  definitionEn = "To fail to catch or reach something.",
                  examples =
                    listOf(
                      GlossaryExampleJsonDto(
                        text = "She missed her train.",
                        translationFa = "او از قطارش جا ماند.",
                      )
                    ),
                )
              ),
            pronunciation = PronunciationJsonDto(uk = "/mɪst/", us = "/mɪst/"),
            synonyms = listOf("fail to catch"),
            antonyms = listOf("catch"),
            sentenceIds = listOf("1"),
            occurrences =
              listOf(
                GlossaryOccurrenceJsonDto(
                  sentenceId = "1",
                  surface = "missed",
                  start = 5,
                  end = 11,
                )
              ),
          )
        ),
      questions =
        listOf(
          QuestionJsonDto(
            id = "q_1",
            orderIndex = 0,
            promptEn = "What did Sara miss?",
            promptFa = "سارا چه چیزی را از دست داد؟",
            options = listOf("Her bus", "Her train"),
            correctOptionIndex = 1,
          )
        ),
    )

  @Test
  fun `valid phase 2 content schema and UTF-16 occurrence offsets pass validation`() {
    val result = validator.validate(validSampleDto())
    assertEquals(ValidationOutcome.Valid, result)
  }

  @Test
  fun `invalid schemaVersion, CEFR level, speaker ref, and mismatched UTF-16 offset are rejected`() {
    val invalid =
      validSampleDto().copy(
        schemaVersion = 99,
        type = "dialogue",
        level = "Z9",
        speakers = listOf(SpeakerJsonDto(id = "spk_1", name = "Ali", nameFa = "علی", role = "Guide")),
        sentences =
          listOf(
            SentenceJsonDto(
              id = "1",
              orderIndex = 0,
              speakerId = "missing_speaker",
              text = "Sara missed her train again.",
              translationFa = "سارا دوباره از قطارش جا ماند.",
            )
          ),
        glossary =
          listOf(
            GlossaryEntryJsonDto(
              id = "missed-verb-001",
              term = "missed",
              lemma = "miss",
              pos = "verb",
              cefr = "A2",
              senses = listOf(GlossarySenseJsonDto(id = "sense-1", meaningFa = "از دست دادن")),
              occurrences =
                listOf(
                  GlossaryOccurrenceJsonDto(
                    sentenceId = "1",
                    surface = "wrong_slice",
                    start = 5,
                    end = 11,
                  )
                ),
            )
          ),
      )

    val result = validator.validate(invalid)
    assertTrue(result is ValidationOutcome.Invalid)
    val codes = (result as ValidationOutcome.Invalid).errors.map { it.code }.toSet()
    assertTrue(ImportValidationErrorCode.UNSUPPORTED_SCHEMA_VERSION in codes)
    assertTrue(ImportValidationErrorCode.INVALID_CEFR_LEVEL in codes)
    assertTrue(ImportValidationErrorCode.INVALID_REFERENTIAL_INTEGRITY in codes)
    assertTrue(ImportValidationErrorCode.INVALID_WORD_OFFSET in codes)
  }

  @Test
  fun `morphology analyzer resolves irregulars, double consonants, multi-lemma better, and protects invariant words`() {
    assertEquals("miss", MorphologyAnalyzer.resolveLemmas("missed").first().lemma)
    assertEquals("run", MorphologyAnalyzer.resolveLemmas("running").first().lemma)
    assertEquals("go", MorphologyAnalyzer.resolveLemmas("went").first().lemma)

    val betterLemmas = MorphologyAnalyzer.resolveLemmas("better").map { it.lemma }
    assertTrue("good" in betterLemmas)
    assertTrue("well" in betterLemmas)

    // Invariant words must NOT be stripped naively
    assertEquals(listOf("morning"), MorphologyAnalyzer.resolveLemmas("morning").map { it.lemma })
    assertEquals(listOf("his"), MorphologyAnalyzer.resolveLemmas("his").map { it.lemma })
    assertEquals(listOf("this"), MorphologyAnalyzer.resolveLemmas("this").map { it.lemma })
  }

  @Test
  fun `vocabulary resolution prioritizes multiword phrases over single words and supports inflected forms`() {
    val sentence =
      Sentence(
        id = "pkg::2",
        packageId = "pkg",
        sourceSentenceId = "2",
        orderIndex = 1,
        speakerId = null,
        englishText = "She ran out of time and never gives up.",
        persianTranslation = "او وقت کم آورد و هرگز تسلیم نمی‌شود.",
      )

    val runSingle =
      makeEntry(
        id = "pkg::run",
        term = "run",
        forms = listOf("run", "runs", "ran", "running"),
        pos = "verb",
        cefr = CefrLevel.A1,
        senses = listOf(GlossarySense("s1", "pkg::run", "s1", 0, "دویدن", "To move fast on foot.")),
      )
    val runOutOfPhrase =
      makeEntry(
        id = "pkg::run-out-of",
        term = "run out of",
        forms = listOf("run out of", "ran out of", "running out of"),
        pos = "phrasal verb",
        cefr = CefrLevel.B1,
        senses = listOf(GlossarySense("s1", "pkg::run-out-of", "s1", 0, "تمام کردن", "To have none left.")),
      )
    val giveUpPhrase =
      makeEntry(
        id = "pkg::give-up",
        term = "give up",
        forms = listOf("give up", "gives up", "gave up"),
        pos = "phrasal verb",
        cefr = CefrLevel.B1,
        senses = listOf(GlossarySense("s1", "pkg::give-up", "s1", 0, "تسلیم شدن", "To stop trying.")),
      )

    // Tap inside "ran" (offset 5) -> must prioritize "ran out of" phrase over single "run"
    val resolvedPhrase =
      resolutionEngine.resolveInSentence(
        sentence = sentence,
        tappedOffset = 5,
        packageGlossary = listOf(runSingle, runOutOfPhrase, giveUpPhrase),
        sentenceOccurrences = emptyList(),
      )
    assertNotNull(resolvedPhrase?.primaryMatch)
    assertEquals("run out of", resolvedPhrase?.primaryMatch?.entry?.term)
    assertEquals("ran out of", resolvedPhrase?.matchedSurface)

    // Tap inside "gives" (offset 31) -> matches inflected multiword form "gives up"
    val resolvedGiveUp =
      resolutionEngine.resolveInSentence(
        sentence = sentence,
        tappedOffset = 31,
        packageGlossary = listOf(runSingle, runOutOfPhrase, giveUpPhrase),
        sentenceOccurrences = emptyList(),
      )
    assertEquals("give up", resolvedGiveUp?.primaryMatch?.entry?.term)
    assertEquals("gives up", resolvedGiveUp?.matchedSurface)
  }

  @Test
  fun `contextual disambiguation distinguishes multiple senses and good vs well for better`() {
    val missEntry =
      makeEntry(
        id = "pkg::miss",
        term = "miss",
        forms = listOf("miss", "missed", "misses", "missing"),
        pos = "verb",
        cefr = CefrLevel.A2,
        senses =
          listOf(
            GlossarySense(
              id = "pkg::miss::sense-1",
              glossaryEntryId = "pkg::miss",
              sourceSenseId = "sense-1",
              orderIndex = 0,
              meaningFa = "جا ماندن / از دست دادن",
              definitionEn = "To fail to catch a train or bus.",
              examples = listOf(GlossaryExample("She missed her train.", "او از قطارش جا ماند.")),
            ),
            GlossarySense(
              id = "pkg::miss::sense-2",
              glossaryEntryId = "pkg::miss",
              sourceSenseId = "sense-2",
              orderIndex = 1,
              meaningFa = "دلتنگ شدن",
              definitionEn = "To feel sad because someone is far away.",
              examples = listOf(GlossaryExample("I miss my family.", "دلم برای خانواده‌ام تنگ شده است.")),
            ),
          ),
      )

    val goodEntry =
      makeEntry(
        id = "pkg::good",
        term = "good",
        forms = listOf("good", "better", "best"),
        pos = "adjective",
        cefr = CefrLevel.A1,
        senses = listOf(GlossarySense("s1", "pkg::good", "sense-1", 0, "بهتر", "Of higher quality.")),
      )

    val wellEntry =
      makeEntry(
        id = "pkg::well",
        term = "well",
        forms = listOf("well", "better", "best"),
        pos = "adverb",
        cefr = CefrLevel.A2,
        senses = listOf(GlossarySense("s2", "pkg::well", "sense-1", 0, "بهتر (قید)", "In a better manner.")),
      )

    val missSentence =
      Sentence(
        id = "pkg::1",
        packageId = "pkg",
        sourceSentenceId = "1",
        orderIndex = 0,
        speakerId = null,
        englishText = "Sara missed her train again.",
        persianTranslation = "سارا دوباره از قطارش جا ماند.",
      )
    val missRes =
      resolutionEngine.resolveInSentence(
        sentence = missSentence,
        tappedOffset = 6,
        packageGlossary = listOf(missEntry),
        sentenceOccurrences =
          listOf(
            WordOccurrence(
              id = 1L,
              packageId = "pkg",
              sentenceId = "pkg::1",
              glossaryEntryId = "pkg::miss",
              surfaceForm = "missed",
              startOffset = 5,
              endOffset = 11,
            )
          ),
      )
    assertEquals("verb", missRes?.predictedPos)
    assertEquals("sense-1", missRes?.primaryMatch?.recommendedSense?.sourceSenseId)

    // "a better route" -> adjective ("good")
    val adjSentence =
      Sentence(
        id = "pkg::2",
        packageId = "pkg",
        sourceSentenceId = "2",
        orderIndex = 1,
        speakerId = null,
        englishText = "She found a better route.",
        persianTranslation = "او مسیر بهتری پیدا کرد.",
      )
    val adjRes =
      resolutionEngine.resolveInSentence(
        sentence = adjSentence,
        tappedOffset = 13,
        packageGlossary = listOf(goodEntry, wellEntry),
        sentenceOccurrences = emptyList(),
      )
    assertEquals("adjective", adjRes?.predictedPos)
    assertEquals("good", adjRes?.primaryMatch?.entry?.lemma)

    // "to feel better prepared" -> adverb ("well")
    val advSentence =
      Sentence(
        id = "pkg::3",
        packageId = "pkg",
        sourceSentenceId = "3",
        orderIndex = 2,
        speakerId = null,
        englishText = "She tries to feel better prepared.",
        persianTranslation = "او تلاش می‌کند آمادگی بیشتری احساس کند.",
      )
    val advRes =
      resolutionEngine.resolveInSentence(
        sentence = advSentence,
        tappedOffset = 19,
        packageGlossary = listOf(goodEntry, wellEntry),
        sentenceOccurrences = emptyList(),
      )
    assertEquals("adverb", advRes?.predictedPos)
    assertEquals("well", advRes?.primaryMatch?.entry?.lemma)

    // Unknown word fallback returns null primaryMatch so UI triggers manual add card
    val unknownRes =
      resolutionEngine.resolveInSentence(
        sentence = adjSentence,
        tappedOffset = 19, // "route" not in glossary
        packageGlossary = listOf(goodEntry, wellEntry),
        sentenceOccurrences = emptyList(),
      )
    assertNotNull(unknownRes)
    assertEquals("route", unknownRes?.tappedToken?.normalized)
    assertNull(unknownRes?.primaryMatch)
  }

  @Test
  fun `srs scheduler covers SM-2 ratings, backward clock clamping, timezone boundary, and reader color status`() {
    val baseNow = 1_700_000_000_000L
    val initialCard =
      SrsCard(
        cardId = "card_1",
        frontTerm = "missed",
        backPersianMeaning = "از دست دادن",
        normalizedLemma = "miss",
        pos = "verb",
        senseId = "sense-1",
        state = SrsCardState.NEW,
        dueAtEpochMs = baseNow,
        createdAtEpochMs = baseNow,
      )

    // Reader status starts NEW
    assertEquals(ReaderWordStatus.NEW, SrsScheduler.displayStatusFor(initialCard, baseNow))

    val previews = scheduler.previewIntervals(initialCard, baseNow)
    assertEquals(1, previews[ReviewQuality.AGAIN])
    assertEquals(1, previews[ReviewQuality.HARD])
    assertEquals(1, previews[ReviewQuality.GOOD])
    assertEquals(4, previews[ReviewQuality.EASY])

    // First GOOD -> LEARNING (interval = 1 day)
    val firstGood = scheduler.scheduleNextReview(initialCard, ReviewQuality.GOOD, baseNow)
    assertEquals(SrsCardState.LEARNING, firstGood.updatedCard.state)
    assertEquals(1, firstGood.updatedCard.intervalDays)
    assertEquals(
      ReaderWordStatus.LEARNING,
      SrsScheduler.displayStatusFor(firstGood.updatedCard, baseNow + 1_000L),
    )

    // Second GOOD -> REVIEW (interval = 6 days)
    val secondReviewTime = baseNow + SrsScheduler.MILLIS_PER_DAY
    val secondGood =
      scheduler.scheduleNextReview(firstGood.updatedCard, ReviewQuality.GOOD, secondReviewTime)
    assertEquals(SrsCardState.REVIEW, secondGood.updatedCard.state)
    assertEquals(6, secondGood.updatedCard.intervalDays)
    assertEquals(
      ReaderWordStatus.REVIEW,
      SrsScheduler.displayStatusFor(secondGood.updatedCard, secondReviewTime + 1_000L),
    )

    // Time advances past due date -> displayStatusFor returns DUE without mutating persisted state
    val afterDueTime = secondGood.updatedCard.dueAtEpochMs + 10_000L
    assertEquals(
      ReaderWordStatus.DUE,
      SrsScheduler.displayStatusFor(secondGood.updatedCard, afterDueTime),
    )
    assertEquals(SrsCardState.REVIEW, secondGood.updatedCard.state)

    // Backward clock anomaly safety (timestamp < lastReviewedAtEpochMs) clamps safely
    val backwardOutcome =
      scheduler.scheduleNextReview(secondGood.updatedCard, ReviewQuality.AGAIN, baseNow - 500_000L)
    assertEquals(secondReviewTime, backwardOutcome.updatedCard.lastReviewedAtEpochMs)
    assertEquals(SrsCardState.RELEARNING, backwardOutcome.updatedCard.state)
    assertEquals(1, backwardOutcome.updatedCard.intervalDays)
    assertTrue(backwardOutcome.updatedCard.easeFactor >= SrsScheduler.MIN_EASE_FACTOR)

    // Timezone daily boundary stays within same local day
    val tehranCutoff = SrsScheduler.endOfLocalDayEpochMs(baseNow, ZoneId.of("Asia/Tehran"))
    assertTrue(tehranCutoff >= baseNow)
    assertTrue(tehranCutoff - baseNow < SrsScheduler.MILLIS_PER_DAY)
  }

  @Test
  fun `content integrity analyzer separates missing vocab warnings, linguistic warnings, and multiword collisions`() {
    val analyzer = ContentIntegrityAnalyzer(validator, resolutionEngine)
    val partialDto =
      validSampleDto().copy(
        glossary =
          listOf(
            // Covers "missed", leaves "Sara", "her", "train", "again" uncovered; missing IPA + bad lemma
            GlossaryEntryJsonDto(
              id = "missed-verb-001",
              term = "missed",
              lemma = "wronglemma",
              forms = listOf("missed"),
              pos = "verb",
              cefr = "A2",
              senses = listOf(GlossarySenseJsonDto(id = "sense-1", meaningFa = "از دست دادن", definitionEn = "")),
              pronunciation = null,
            ),
            GlossaryEntryJsonDto(
              id = "phrase-a",
              term = "give up",
              lemma = "give up",
              forms = listOf("give up"),
              pos = "phrasal verb",
              cefr = "B1",
              senses = listOf(GlossarySenseJsonDto(id = "s1", meaningFa = "تسلیم شدن", definitionEn = "Stop trying.")),
              pronunciation = PronunciationJsonDto(us = "/ɡɪv ʌp/"),
            ),
            GlossaryEntryJsonDto(
              id = "phrase-b",
              term = "give up",
              lemma = "give up",
              forms = listOf("give up"),
              pos = "idiom",
              cefr = "B1",
              senses = listOf(GlossarySenseJsonDto(id = "s1", meaningFa = "رها کردن", definitionEn = "Quit.")),
              pronunciation = PronunciationJsonDto(us = "/ɡɪv ʌp/"),
            ),
          )
      )

    val report = analyzer.analyzeDto(partialDto)
    assertTrue(report.isValidForImport)
    assertFalse(report.hasCompleteDictionaryCoverage)
    assertTrue(report.coveragePercent in 1..99)
    assertTrue(report.uncoveredTokens.any { it.equals("train", ignoreCase = true) })
    assertTrue(report.missingVocabularyWarnings.isNotEmpty())
    assertTrue(report.linguisticWarnings.any { it.messageEn.contains("IPA") })
    assertTrue(report.linguisticWarnings.any { it.messageEn.contains("Collides", ignoreCase = true) })
  }

  @Test
  fun `learning analytics calculator computes real streak, accuracy, card breakdowns, and 7-day activity`() {
    val zone = ZoneId.of("UTC")
    val dayNow = 1_700_050_000_000L // exact mid-day timestamp

    // Empty state returns zeros and null accuracy
    val emptySummary =
      LearningAnalyticsCalculator.compute(
        cards = emptyList(),
        reviewLogs = emptyList(),
        progressList = emptyList(),
        packages = emptyList(),
        nowEpochMs = dayNow,
        zoneId = zone,
      )
    assertEquals(0, emptySummary.streakDays)
    assertNull(emptySummary.reviewAccuracyPercent)
    assertEquals(7, emptySummary.dailyActivity.size)
    assertTrue(emptySummary.dailyActivity.all { it.reviewCount == 0 })

    val cards =
      listOf(
        SrsCard(
          cardId = "w1",
          cardType = SrsCardType.WORD,
          frontTerm = "miss",
          backPersianMeaning = "از دست دادن",
          state = SrsCardState.REVIEW,
          dueAtEpochMs = dayNow + SrsScheduler.MILLIS_PER_DAY * 3,
          createdAtEpochMs = dayNow - SrsScheduler.MILLIS_PER_DAY * 2,
        ),
        SrsCard(
          cardId = "w2",
          cardType = SrsCardType.WORD,
          frontTerm = "station",
          backPersianMeaning = "ایستگاه",
          state = SrsCardState.LEARNING,
          dueAtEpochMs = dayNow - 1_000L, // due now
          createdAtEpochMs = dayNow - SrsScheduler.MILLIS_PER_DAY,
        ),
        SrsCard(
          cardId = "s1",
          cardType = SrsCardType.SENTENCE,
          frontTerm = "Sara missed her train.",
          backPersianMeaning = "سارا جا ماند.",
          state = SrsCardState.NEW,
          dueAtEpochMs = dayNow,
          createdAtEpochMs = dayNow,
        ),
      )

    val logs =
      listOf(
        SrsReviewLog(1, "w1", qualityScore = 4, 1, 6, 2.5, 2.5, dayNow),
        SrsReviewLog(2, "w1", qualityScore = 2, 1, 1, 2.5, 2.3, dayNow - SrsScheduler.MILLIS_PER_DAY),
        SrsReviewLog(3, "w2", qualityScore = 5, 0, 1, 2.5, 2.6, dayNow - SrsScheduler.MILLIS_PER_DAY * 2),
      )

    val pkgSummary =
      ContentPackageSummary(
        contentPackage =
          ContentPackage(
            packageId = "story-1",
            schemaVersion = 1,
            contentVersion = 1,
            contentType = ContentType.STORY,
            level = CefrLevel.B1,
            topic = "travel",
            title = "Story One",
            titleFa = "داستان یک",
            description = "Desc",
            descriptionFa = "توضیح",
            metadata = PackageMetadata("BamLinguage", estimatedMinutes = 3, language = "en", translationLanguage = "fa"),
            importedAtEpochMs = dayNow,
          ),
        sentenceCount = 10,
        glossaryCount = 12,
        questionCount = 2,
        speakerCount = 0,
      )
    val progress =
      listOf(
        LearningProgress(
          progressId = "prog_story-1",
          packageId = "story-1",
          packageTitleSnapshot = "Story One",
          completedSentenceCount = 5,
          totalSentenceCount = 10,
          correctQuestionsCount = 1,
          totalQuestionsCount = 2,
          lastReadSentenceIndex = 4,
          updatedAtEpochMs = dayNow,
        )
      )

    val summary =
      LearningAnalyticsCalculator.compute(
        cards = cards,
        reviewLogs = logs,
        progressList = progress,
        packages = listOf(pkgSummary),
        nowEpochMs = dayNow,
        zoneId = zone,
      )

    assertEquals(1, summary.reviewedTodayCount)
    assertEquals(66, summary.reviewAccuracyPercent) // 2 of 3 passing
    assertEquals(3, summary.streakDays)
    assertEquals(2, summary.totalWordCards)
    assertEquals(1, summary.totalSentenceCards)
    assertEquals(1, summary.learnedVocabularyCount)
    assertEquals(1, summary.learningWordsCount)
    assertEquals(1, summary.dueWordsCount)
    assertEquals(50, summary.overallReadingPercent)
    assertEquals("story-1", summary.continueReadingPackageId)
    assertEquals(1, summary.dailyActivity.last().reviewCount)
  }

  @Test
  fun `optional isolated ai service defaults to disabled offline and traps provider exceptions safely`() = runBlocking {
    val defaultService = AiService(DisabledOfflineAiProvider())
    val defaultConfig = AiConfiguration()
    assertEquals(AiAvailability.DisabledByUser, defaultService.checkAvailability(defaultConfig))

    val disabledResult = defaultService.requestUserInitiatedExplanation("missed", defaultConfig)
    assertTrue(disabledResult is AiResult.Unavailable)

    val throwingProvider =
      object : AiProvider {
        override val id: String = "faulty"
        override val displayName: String = "Faulty"

        override fun checkAvailability(configuration: AiConfiguration): AiAvailability =
          AiAvailability.Ready("Faulty")

        override suspend fun requestExplanation(prompt: String, configuration: AiConfiguration): AiResult {
          error("Simulated network crash")
        }
      }
    val faultTolerantService = AiService(throwingProvider)
    val enabledConfig = AiConfiguration(enabled = true, providerType = AiProviderType.CUSTOM_ENDPOINT, endpointUrl = "https://example.invalid")
    val safeResult = faultTolerantService.requestUserInitiatedExplanation("missed", enabledConfig)
    assertTrue(safeResult is AiResult.Unavailable)
  }
}
