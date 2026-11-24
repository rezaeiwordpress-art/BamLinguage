# BamLinguage — Architecture Documentation (Phase 3 Release)

## 1. Identity & Mission
- **App Name**: `BamLinguage` (بام‌لینگویج)
- **Application ID**: `com.aistudio.bamlinguage.bmyxkr` (`namespace = "com.example"`)
- **Mission**: Reliable, offline-first native Android English learning application for Persian-speaking learners. Core reading, dictionary lookup, spaced repetition (SRS), comprehension quizzes, and Android Text-to-Speech work 100% offline with zero internet, VPNs, external AI APIs, or cloud accounts.

## 2. Logical Module Structure
The codebase is structured into 6 strictly separated logical modules under `com.example`:

1. **`core` (`com.example.core`)**
   - `common`: Shared enums (`CefrLevel`, `ContentType`, `ThemeMode`, `TtsAccent`), `AppResult`, and `DispatcherProvider`.
   - `datastore`: `UserPreferencesDataStore` & `UserPreferences` managing theme mode, TTS speed, TTS accent (`AUTO`/`US`/`UK`), preferred local voice, daily goal, new cards per day, Reader display preferences (`showTranslationsInReader`, `showIpaInReader`, `showGrammarNotesInReader`), SRS auto-play audio (`autoPlaySrsAudio`), and isolated optional AI configuration (disabled by default).
   - `tts`: `TextToSpeechEngine` abstraction & `AndroidTextToSpeechEngine` supporting word/sentence/paragraph-queue playback, `pause()`, `resume()`, `stop()`, `refreshVoices()`, English voice enumeration (`availableVoices`), US/UK accent & speaker-voice routing, and graceful `TtsStatus.Unavailable` fallback.
   - `ai`: Strictly optional, isolated `AiProvider`, `AiService`, `AiConfiguration`, `AiAvailability`, `AiResult`, and default `DisabledOfflineAiProvider`. Disabled by default (`AiProviderType.NONE`), zero hardcoded API keys, zero automatic requests, and all exceptions trapped so core offline functionality is never affected.

2. **`domain` (`com.example.domain`)**
   - `model`: Pure Kotlin models (`ContentPackage`, `Speaker`, `Sentence`, `GlossaryEntry`, `GlossarySense`, `GlossaryExample`, `PronunciationInfo`, `WordOccurrence`, `ComprehensionQuestion`, `SrsCard`, `ReaderWordStatus`, `SrsReviewLog`, `LearningProgress`, `UserVocabularyItem`, `ReaderBookmark`, `DashboardStats`, `PackageImportResult`).
   - `repository`: `ContentRepository`, `LearningRepository`, `PreferencesRepository` interfaces.
   - `vocabulary`:
     - `MorphologyAnalyzer`: Irregular inflection dictionary (`missed -> miss`, `running -> run`, `went -> go`, `better -> [good, well]`), invariant-word protection (`morning`, `his`, `this`), and guarded orthographic rules.
     - `VocabularyResolutionEngine`: UTF-16 tokenizer, multiword phrase span matcher (`run out of`, `give up`, `look after`, `take part in`), contextual POS disambiguator, and multi-sense ranker.
   - `srs`:
     - `SrsScheduler`: Pure SM-2 scheduler (`AGAIN`/`HARD`/`GOOD`/`EASY`), interval previews, backward clock clamping, timezone-aware local day boundary (`endOfLocalDayEpochMs`), normalized deduplication keys (`buildWordContentId`, `buildSentenceContentId`), and dynamic Reader color status (`displayStatusFor`).
     - `LearningAnalyticsCalculator`: Computes real Room-backed statistics (today's reviews, review accuracy %, consecutive streak days, word/sentence card breakdowns, reading completion %, continue-reading target, and 7-day daily activity chart points).
   - `usecase`: `ImportContentPackageUseCase` (with `preview` & `invoke`), `EnsureBundledSamplePackageUseCase`, `DictionaryLookupUseCase`, `ResolveWordInSentenceUseCase`, `ObserveReaderPackageUseCase`, `CreateSrsCardUseCase`, `ReviewSrsCardUseCase`, `DeleteSrsCardUseCase`.

3. **`data` (`com.example.data`)**
   - `local/entity`: 12 Room entities split cleanly into Content tables (7 tables including `glossary_senses`) and Learning tables (5 tables).
   - `local/dao`: `ContentDao` (with `@Transaction` package replacement & lemma/forms search) and `LearningDao` (with `@Transaction` review recording, `getSrsCardByContentId`, `deleteSrsCardById`, `deleteVocabularyItemById`, `resetReadingProgressAndBookmarks`, and `clearAllLearningData`).
   - `local/database`: `BamLinguageDatabase` (`version = 2`) with explicit `MIGRATION_1_2` and `RoomConverters`. Destructive migrations are never enabled.
   - `mapper`: Bidirectional Entity <-> Domain mappers.
   - `repository`: `RoomContentRepository`, `RoomLearningRepository`, `DataStorePreferencesRepository`.

4. **`content` (`com.example.content`)**
   - `schema`: `kotlinx.serialization` DTOs matching schema version `1` (`FlexibleIdSerializer`, `senses[]`, `pronunciation`, `synonyms`, `antonyms`, nested `occurrences[]`).
   - `validation`:
     - `ContentPackageValidator`: Structural schema, ID uniqueness, referential integrity, CEFR codes, question indices, and exact UTF-16 `start`/`end` slicing (`substring == surface`).
     - `ContentIntegrityAnalyzer`: 10-check quality & coverage audit distinguishing Fatal structural errors, Missing vocabulary warnings (exact token coverage %), and Linguistic quality warnings (lemma consistency, missing IPA/POS/definition, non-deterministic phrase collisions).
   - `importer`: `JsonContentPackageImporter` with 5 MB bounded payload guard (`MAX_JSON_CHARS`), pre-import `previewFromJson`, version conflict checks, and transactional Room replacement.
   - `assets/sample_packages/`: `bamiyan_morning_dialogue.json` (A2 dialogue) & `a_late_train_story.json` (B1 story), both verified with 100% glossary token coverage.

5. **`feature` (`com.example.feature`)**
   - `navigation`: Type-safe `@Serializable` routes (`HomeRoute`, `LearnRoute`, `ReaderRoute`, `PracticeRoute`, `ProgressRoute`, `ProfileRoute`).
   - `home`: Real Room-backed Persian RTL dashboard with daily goal progress, streak badge, continue reading card, quick actions, recent stories/dialogues, user vocabulary modal, and new-user onboarding state.
   - `learn`: Content Library & Dictionary with CEFR/type/topic/search filters, offline dictionary lookup + direct SRS promotion, SAF `OpenDocument` & JSON paste import with Pre-Import Review Dialog, Package Inspector with 10-check Integrity Report, and safe package deletion.
   - `reader`: Interactive `ReaderScreen`, `ColorCodedSentenceText` (`NEW`/`LEARNING`/`REVIEW`/`DUE` highlights), `VocabularyBottomSheet` (contextual senses, IPA visibility pref, TTS, add-to-SRS, manual fallback, optional user-initiated AI hook when enabled), comprehension questions, and bookmarks.
   - `practice`: Complete SM-2 flashcard review session respecting `newCardsPerDay` and `autoPlaySrsAudio`, interval previews, deck filter/deletion, and TTS studio.
   - `progress`: Detailed analytics cards, 7-day Compose `Canvas` activity chart, reading history, and clickable bookmarks opening directly in Reader.
   - `profile`: Settings for Theme, TTS speed/accent/local voice selector, daily goal, `newCardsPerDay`, Reader/SRS toggles, data management dialogs, optional disabled-by-default AI settings, and app info.

6. **`app` (`com.example.app` & `MainActivity`)**
   - `AppContainer` & `DefaultAppContainer`: Manual constructor dependency injection graph.
   - `BamLinguageApplication`: Initializes `AppContainer` and runs idempotent bundled sample seeding.
   - `MainActivity`: Sets `enableEdgeToEdge()`, forces `LayoutDirection.Rtl` for Persian UI while keeping English text LTR, applies `BamLinguageTheme`, and renders adaptive `NavigationBar` (`< 600.dp`) / `NavigationRail` (`>= 600.dp`).
