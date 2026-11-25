# BamLinguage — Database Schema Documentation (Room v4)

Database Name: `bamlinguage.db` (`BamLinguageDatabase`, `version = 4`)

## Migration Policy
- **`MIGRATION_1_2` (v1 -> v2)**: Preserves existing packages, glossary entries, and SRS cards while adding Phase 2 metadata/morphology columns, creating `glossary_senses`, and backfilling `lemma`, `contentId`, and `normalizedLemma`.
- **`MIGRATION_2_3` (v2 -> v3)**: Deduplicates `srs_cards.contentId`, upgrades `index_srs_cards_contentId` to `UNIQUE`, and adds `completedSentenceIds` to `learning_progress` for accurate per-sentence progress tracking.
- **`MIGRATION_3_4` (v3 -> v4)**: Creates `quiz_attempts` with indexes (`packageId`, `UNIQUE(packageId, questionId)`, `answeredAtEpochMs`) so comprehension quiz selections and accuracy persist across app restarts.

## Design Principle: Content vs. Learning Separation
Content tables represent installable, replaceable, or deletable JSON packages. Learning tables represent durable user study history. **Learning tables have zero cascading foreign keys to `content_packages`**, guaranteeing that deleting or upgrading a content package never deletes `SrsCards`, `SrsReviewLogs`, `LearningProgress`, `QuizAttempts`, `UserVocabulary`, or `ReaderBookmarks`. Similarly, updating an `SrsCard` uses `@Update` + `@Insert(IGNORE)` rather than SQLite `REPLACE` so `ON DELETE CASCADE` on `srs_review_logs` never wipes historical reviews.

## Content Tables (7 Tables)

1. **`content_packages`** — PK: `packageId` (`TEXT`); Indexes: `level`, `contentType`, `importedAtEpochMs`.
2. **`speakers`** — PK: `id` (`"$packageId::$sourceSpeakerId"`); FK `packageId -> content_packages` (`CASCADE`).
3. **`sentences`** — PK: `id` (`"$packageId::$sourceSentenceId"`); FKs `packageId` (`CASCADE`), `speakerId` (`SET NULL`); Indexes: `UNIQUE(packageId, orderIndex)`, `speakerId`.
4. **`glossary_entries`** — PK: `id` (`"$packageId::$sourceGlossaryId"`); FK `packageId` (`CASCADE`); Indexes: `(packageId, normalizedTerm)`, `normalizedTerm`, `lemma`.
5. **`glossary_senses`** — PK: `id` (`"$glossaryEntryId::$sourceSenseId"`); FKs `glossaryEntryId` (`CASCADE`), `packageId` (`CASCADE`).
6. **`word_occurrences`** — PK: `id` (`AUTOINCREMENT`); FKs `packageId`, `sentenceId`, `glossaryEntryId` (`CASCADE`).
7. **`questions`** — PK: `id` (`"$packageId::$sourceQuestionId"`); FK `packageId` (`CASCADE`); Index: `UNIQUE(packageId, orderIndex)`.

## Learning Tables (6 Tables)

8. **`srs_cards`**
   - **PK**: `cardId` (`TEXT`)
   - **Columns**: `cardType` (`WORD` | `SENTENCE`), `contentId` (`word:<lemma>:<pos>:<senseId>` with escaped segments, or `sentence:<pkgId>:<sentId>` / `sentence:text:<sha256>`), `frontTerm`, `backPersianMeaning`, `englishDefinition`, `normalizedLemma`, `pos`, `senseId`, `phonetic`, `exampleSentence`, `contextSentenceEn`, `contextSentenceFa`, `sourcePackageId`, `sourcePackageTitle`, `sourceGlossaryId`, `sourceSentenceId`, `state`, `repetitions`, `intervalDays`, `easeFactor`, `dueAtEpochMs`, `lastReviewedAtEpochMs`, `createdAtEpochMs`
   - **Indexes**: `dueAtEpochMs`, `state`, `sourcePackageId`, `frontTerm`, `UNIQUE(contentId)`, `cardType`

9. **`srs_review_logs`**
   - **PK**: `logId` (`INTEGER PRIMARY KEY AUTOINCREMENT`)
   - **FK**: `cardId -> srs_cards(cardId)` (`ON DELETE CASCADE`)
   - **Indexes**: `cardId`, `reviewedAtEpochMs`

10. **`learning_progress`**
    - **PK**: `progressId` (`TEXT`)
    - **Columns**: `packageId`, `packageTitleSnapshot`, `completedSentenceCount`, `totalSentenceCount`, `correctQuestionsCount`, `totalQuestionsCount`, `lastReadSentenceIndex`, `updatedAtEpochMs`, `completedSentenceIds` (`List<String>`)
    - **Indexes**: `UNIQUE(packageId)`, `updatedAtEpochMs`

11. **`user_vocabulary`**
    - **PK**: `vocabId` (`TEXT`, deterministic `"vocab_<sha256_24hex>"`)
    - **Indexes**: `UNIQUE(normalizedTerm)`, `sourcePackageId`

12. **`reader_bookmarks`**
    - **PK**: `bookmarkId` (`TEXT`)
    - **Indexes**: `UNIQUE(packageId, sentenceId)`, `createdAtEpochMs`

13. **`quiz_attempts`** *(Added in v4)*
    - **PK**: `attemptId` (`TEXT`, deterministic `"qa_<sha256_24hex>"`)
    - **Columns**: `attemptId`, `packageId`, `questionId`, `selectedOptionIndex`, `isCorrect`, `attemptCount`, `answeredAtEpochMs`
    - **Indexes**: `packageId`, `UNIQUE(packageId, questionId)`, `answeredAtEpochMs`
