# BamLinguage (بام‌لینگویج)

**BamLinguage** is a native, offline-first Android English learning application designed for Persian-speaking learners, built with Kotlin, Jetpack Compose, Material 3, Room, DataStore, and Android Text-to-Speech (TTS).

- **Application ID:** `com.aistudio.bamlinguage.bmyxkr`
- **Namespace:** `com.example`
- **Min / Compile / Target SDK:** `24` / `36.1` / `36`

---

## Offline-First Architecture

Core learning works **100% offline** with no internet connection, VPN, user registration, cloud accounts, `.env` secrets, or mandatory Gemini/LLM APIs:

- **Story & Dialogue Reader:** Interactive color-coded sentence cards (`NEW`, `LEARNING`, `REVIEW`, `DUE`), exact UTF-16 word/phrase highlighting matched on `(lemma, pos, senseId)`, contextual multi-sense Persian/English dictionary bottom sheets, bookmarks, per-sentence completion tracking, and persistent comprehension quizzes.
- **Spaced Repetition System (SRS):** Deterministic SM-2 scheduler (`SrsScheduler`), duplicate-proof card creation enforced with SQLite `UNIQUE(contentId)`, non-destructive review updates preserving full `srs_review_logs` history, and `StableIdGenerator` SHA-256 identifiers across packages and restarts.
- **Offline Audio (Android TTS):** Word, sentence, and multi-speaker paragraph playback with US/UK accent routing, local voice enumeration, and speed control.
- **JSON Package Import & Validation:** Storage Access Framework (`OpenDocument`) and direct JSON import guarded by strict `kotlinx.serialization`, `ContentPackageValidator`, and `ContentIntegrityAnalyzer` (reporting missing words, missing phrases, broken `sentenceIds`/`speakerId` references, duplicate IDs/spans, and invalid or mid-word UTF-16 offsets—never claiming 100% coverage unless verified).
- **Reactive Clock & Foreground Refresh:** `AppTimeRefreshCoordinator` updates dashboard analytics, SRS due queues, Reader token highlights, and daily boundaries automatically on foreground resume (`ON_RESUME`), system clock/timezone changes, and scheduled due deadlines.

For detailed technical documentation, see:
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`docs/DATABASE_SCHEMA.md`](docs/DATABASE_SCHEMA.md)
- [`docs/JSON_CONTENT_SCHEMA.md`](docs/JSON_CONTENT_SCHEMA.md)
- [`docs/IMPORT_INSTRUCTIONS.md`](docs/IMPORT_INSTRUCTIONS.md)

---

## Module Structure

| Package | Responsibility |
|---|---|
| `com.example.core` | `StableIdGenerator`, `AppTimeRefreshCoordinator`, `UserPreferencesDataStore`, `AndroidTextToSpeechEngine`, isolated optional disabled-by-default `AiService` |
| `com.example.domain` | Pure Kotlin domain models, repository contracts, `MorphologyAnalyzer`, `VocabularyResolutionEngine`, `SrsScheduler`, `LearningAnalyticsCalculator`, and use cases |
| `com.example.data` | Room database (`BamLinguageDatabase` v4), entities, DAOs, entity mappers, and repository implementations |
| `com.example.content` | Strict JSON DTOs, `ContentPackageValidator`, `ContentIntegrityAnalyzer`, `JsonContentPackageImporter`, and bundled sample packages |
| `com.example.feature` | Compose UI screens & ViewModels (`home`, `learn`, `reader`, `practice`, `progress`, `profile`, `navigation`) |
| `com.example.app` | Manual dependency graph (`AppContainer` / `DefaultAppContainer`) and `BamLinguageApplication` |

---

## Build, Testing & Signing Configuration

No `.env` file or Gemini API key is needed to build or run tests.

### Gradle Commands

```bash
# Run all unit & Robolectric tests
./gradlew :app:testDebugUnitTest

# Build debug APK
./gradlew assembleDebug
```

### Debug & Release Signing

- **Debug builds (`debugConfig`):** Uses `${rootDir}/debug.keystore` with standard Android debug credentials (`android` / `androiddebugkey`).
- **Release builds (`release`):** Never commit release keystores or credentials into source control. Supply your upload keystore via environment variables before running `./gradlew assembleRelease` or `bundleRelease`:

```bash
export KEYSTORE_PATH=/path/to/your-upload-key.jks
export STORE_PASSWORD=your_store_password
export KEY_PASSWORD=your_key_password
./gradlew assembleRelease
```

Release keystores (`*.jks`, `*.p12`, `keystore.properties`) and `.env` files are excluded by `.gitignore`.
