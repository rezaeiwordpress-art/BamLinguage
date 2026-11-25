# BamLinguage — Architecture Documentation

## 1. Identity & Mission
- **App Name**: `BamLinguage` (بام‌لینگویج)
- **Application ID**: `com.aistudio.bamlinguage.bmyxkr` (`namespace = "com.example"`)
- **Mission**: Reliable, offline-first native Android English learning application for Persian-speaking learners. Reading, dictionary lookup, spaced repetition (SRS), comprehension quizzes, learning analytics, and Android Text-to-Speech work 100% offline with zero internet, VPNs, `.env` API keys, or cloud accounts.

## 2. Logical Module Structure
The codebase is structured into 6 strictly separated logical modules under `com.example`:

1. **`core` (`com.example.core`)**
   - `common`: `StableIdGenerator` (collision-resistant SHA-256 IDs & key normalization), `AppTimeRefreshCoordinator` (`TimeRefreshSource` for foreground `ON_RESUME`, time/timezone broadcasts, and scheduled SRS/midnight deadlines + `ManualTimeRefreshSource` for deterministic tests), shared enums (`CefrLevel`, `ContentType`, `ThemeMode`, `TtsAccent`), and `DispatcherProvider`.
   - `datastore`: `UserPreferencesDataStore` & `UserPreferences` managing theme mode, TTS speed, TTS accent (`AUTO`/`US`/`UK`), preferred local voice, daily goal, new cards per day, Reader toggles, SRS auto-play audio, and optional AI toggle (disabled by default).
   - `tts`: `TextToSpeechEngine` abstraction & `AndroidTextToSpeechEngine` supporting word/sentence/paragraph-queue playback, `pause()`, `resume()`, `stop()`, English voice enumeration (`availableVoices`), US/UK accent & speaker-voice routing, and graceful `TtsStatus.Unavailable` fallback.
   - `ai`: Strictly optional, isolated `AiProvider`, `AiService`, and default `DisabledOfflineAiProvider`. Core functionality never depends on network AI or API keys.

2. **`domain` (`com.example.domain`)**
   - `model`: Pure Kotlin models (`ContentPackage`, `Speaker`, `Sentence`, `GlossaryEntry`, `GlossarySense`, `GlossaryExample`, `PronunciationInfo`, `WordOccurrence`, `ComprehensionQuestion`, `SrsCard`, `ReaderWordStatus`, `SrsReviewLog`, `LearningProgress`, `QuizAttempt`, `UserVocabularyItem`, `ReaderBookmark`, `DashboardStats`, `PackageImportResult`).
   - `repository`: `ContentRepository`, `LearningRepository`, `PreferencesRepository` interfaces.
   - `vocabulary`:
     - `MorphologyAnalyzer`: Irregular inflection dictionary (`missed -> miss`, `running -> run`, `went -> go`, `better -> [good, well]`), invariant-word protection, and guarded orthographic rules.
     - `VocabularyResolutionEngine`: UTF-16 tokenizer, multiword phrase span matcher (`run out of`, `give up`, `look after`, `take part in`), contextual POS disambiguator, and multi-sense ranker.
   - `srs`:
     - `SrsScheduler`: Pure SM-2 scheduler (`AGAIN`/`HARD`/`GOOD`/`EASY`), interval previews, backward clock clamping, timezone-aware local day boundary (`endOfLocalDayEpochMs`), deterministic deduplication keys via `StableIdGenerator`, and dynamic Reader status (`displayStatusFor`).
     - `LearningAnalyticsCalculator`: Computes real Room-backed statistics (today's reviews, review accuracy %, consecutive streak days, word/sentence card breakdowns, reading completion %, continue-reading target, and 7-day daily activity chart points) parameterized by reactive `nowEpochMs`.
   - `usecase`: `ImportContentPackageUseCase`, `EnsureBundledSamplePackageUseCase`, `DictionaryLookupUseCase`, `ResolveWordInSentenceUseCase`, `ObserveReaderPackageUseCase`, `CreateSrsCardUseCase`, `ReviewSrsCardUseCase`, `DeleteSrsCardUseCase`.

3. **`data` (`com.example.data`)**
   - `local/entity`: 13 Room entities split cleanly into Content tables (7 tables) and Learning tables (6 tables including `quiz_attempts`).
   - `local/dao`: `ContentDao` (transactional package replacement, lemma/forms search) and `LearningDao` (`@Update` + `@Insert(IGNORE)` upserts preserving `srs_review_logs`, atomic `saveQuizAttempt`, `upsertUserVocabulary`, `resetReadingProgressAndBookmarks`, `clearAllLearningData`).
   - `local/database`: `BamLinguageDatabase` (`version = 4`) with explicit `MIGRATION_1_2`, `MIGRATION_2_3`, and `MIGRATION_3_4` (never destructive).
   - `mapper`: Bidirectional Entity <-> Domain mappers.
   - `repository`: `RoomContentRepository` and `RoomLearningRepository` (protected by `srsInsertMutex` + SQLite unique `contentId`), and `DataStorePreferencesRepository`.

4. **`content` (`com.example.content`)**
   - `schema`: Strict `kotlinx.serialization` DTOs (`schemaVersion = 1`).
   - `validation`:
     - `ContentPackageValidator`: Validates trimmed ID uniqueness, duplicate occurrence spans, broken `speakerId` / `sentenceIds` / `wordOccurrences` references, CEFR codes, question indices, and exact non-blank/non-mid-word UTF-16 occurrence spans.
     - `ContentIntegrityAnalyzer`: Audits fatal errors, verified token + phrase coverage (detecting missing words and missing catalog/entry phrases without auto-filling fake meanings), and linguistic warnings. `coveragePercent` never reports `100%` unless `isCoverageVerified` is true.
   - `importer`: `JsonContentPackageImporter` with 5 MB guard, pre-import preview, version conflict guard, and transactional DB replacement.
   - `assets/sample_packages/`: `bamiyan_morning_dialogue.json` & `a_late_train_story.json`.

5. **`feature` (`com.example.feature`)**
   - `home`, `learn`, `reader`, `practice`, `progress`, `profile`, and type-safe `navigation`.
   - ViewModels (`HomeViewModel`, `PracticeViewModel`, `ProgressViewModel`, `ReaderViewModel`) subscribe to `TimeRefreshSource` so due queues, analytics, streaks, and Reader token highlights update automatically over time and on app foreground resume.
   - `ReaderViewModel` persists comprehension quiz selections in Room `quiz_attempts` so answers and explanations survive process restarts.

6. **`app` (`com.example.app` & `MainActivity`)**
   - `AppContainer` & `DefaultAppContainer`: Constructor injection container holding `TimeRefreshSource`, repositories, TTS, and use cases.
   - `MainActivity`: `enableEdgeToEdge()`, RTL Persian layout direction with LTR English text blocks, lifecycle `ON_RESUME` observer, system time/timezone `BroadcastReceiver`, and adaptive `NavigationBar` / `NavigationRail`.
