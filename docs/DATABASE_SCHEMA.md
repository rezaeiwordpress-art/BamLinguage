# BamLinguage — Database Schema Documentation (Room v2)

Database Name: `bamlinguage.db` (`BamLinguageDatabase`, `version = 2`)

## Migration Policy
- **`MIGRATION_1_2` (v1 -> v2)**: Preserves all existing `content_packages`, `glossary_entries`, and `srs_cards` while adding Phase 2 metadata/morphology columns, creating `glossary_senses`, backfilling `lemma`, `contentId`, and `normalizedLemma`, and adding Phase 2 indexes.

## Design Principle: Content vs. Learning Separation
Content tables represent installable, replaceable, or deletable JSON packages. Learning tables represent durable user study history. **Learning tables have zero cascading foreign keys to `content_packages`**, guaranteeing that deleting or upgrading a content package never deletes `SrsCards`, `SrsReviewLogs`, `LearningProgress`, `UserVocabulary`, or `ReaderBookmarks`. Similarly, deleting an `SrsCard` cascades only to `SrsReviewLogs` and never modifies content tables.

## Content Tables (7 Tables)

1. **`content_packages`**
   - **PK**: `packageId` (`TEXT`)
   - **Columns**: `schemaVersion`, `contentVersion`, `contentType`, `level`, `topic`, `title`, `titleFa`, `description`, `descriptionFa`, `author`, `createdAt`, `updatedAt`, `wordCount`, `sentenceCountMeta`, `glossaryCountMeta`, `estimatedMinutes`, `language`, `translationLanguage`, `tags` (`List<String>`), `importedAtEpochMs`, `isBundledSample`
   - **Indexes**: `level`, `contentType`, `importedAtEpochMs`

2. **`speakers`**
   - **PK**: `id` (`TEXT`, scoped as `"$packageId::$sourceSpeakerId"`)
   - **FK**: `packageId` -> `content_packages(packageId)` (`ON DELETE CASCADE`)
   - **Columns**: `packageId`, `sourceSpeakerId`, `name`, `nameFa`, `role`
   - **Indexes**: `packageId`

3. **`sentences`**
   - **PK**: `id` (`TEXT`, scoped as `"$packageId::$sourceSentenceId"`)
   - **FKs**: `packageId` -> `content_packages(packageId)` (`ON DELETE CASCADE`), `speakerId` -> `speakers(id)` (`ON DELETE SET NULL`)
   - **Columns**: `packageId`, `sourceSentenceId`, `orderIndex`, `speakerId` (nullable), `englishText`, `persianTranslation`, `phoneticHint`, `grammarNoteFa`
   - **Indexes**: UNIQUE(`packageId`, `orderIndex`), `speakerId`

4. **`glossary_entries`**
   - **PK**: `id` (`TEXT`, scoped as `"$packageId::$sourceGlossaryId"`)
   - **FK**: `packageId` -> `content_packages(packageId)` (`ON DELETE CASCADE`)
   - **Columns**: `packageId`, `sourceGlossaryId`, `term`, `normalizedTerm`, `lemma`, `forms` (`List<String>`), `partOfSpeech`, `cefr`, `phonetic`, `pronunciationUk`, `pronunciationUs`, `persianMeaning`, `englishDefinition`, `exampleSentence`, `synonyms` (`List<String>`), `antonyms` (`List<String>`), `sentenceIds` (`List<String>`)
   - **Indexes**: (`packageId`, `normalizedTerm`), `normalizedTerm`, `lemma`

5. **`glossary_senses`** *(Added in v2)*
   - **PK**: `id` (`TEXT`, scoped as `"$glossaryEntryId::$sourceSenseId"`)
   - **FKs**: `glossaryEntryId` -> `glossary_entries(id)` (`ON DELETE CASCADE`), `packageId` -> `content_packages(packageId)` (`ON DELETE CASCADE`)
   - **Columns**: `glossaryEntryId`, `packageId`, `sourceSenseId`, `orderIndex`, `meaningFa`, `definitionEn`, `examplesJson`
   - **Indexes**: `glossaryEntryId`, `packageId`

6. **`word_occurrences`**
   - **PK**: `id` (`INTEGER PRIMARY KEY AUTOINCREMENT`)
   - **FKs**: `packageId` -> `content_packages(packageId)` (`ON DELETE CASCADE`), `sentenceId` -> `sentences(id)` (`ON DELETE CASCADE`), `glossaryEntryId` -> `glossary_entries(id)` (`ON DELETE CASCADE`)
   - **Columns**: `packageId`, `sentenceId`, `glossaryEntryId`, `surfaceForm`, `startOffset`, `endOffset`
   - **Indexes**: `packageId`, `sentenceId`, `glossaryEntryId`

7. **`questions`**
   - **PK**: `id` (`TEXT`, scoped as `"$packageId::$sourceQuestionId"`)
   - **FK**: `packageId` -> `content_packages(packageId)` (`ON DELETE CASCADE`)
   - **Columns**: `packageId`, `sourceQuestionId`, `orderIndex`, `promptEn`, `promptFa`, `options` (`List<String>`), `correctOptionIndex`, `explanationFa`
   - **Indexes**: UNIQUE(`packageId`, `orderIndex`)

## Learning Tables (5 Tables)

8. **`srs_cards`**
   - **PK**: `cardId` (`TEXT`)
   - **No FK to `content_packages`** (stores `sourcePackageId`, `sourcePackageTitle`, `sourceGlossaryId`, `sourceSentenceId` as preserved snapshots).
   - **Columns**: `cardType` (`WORD` | `SENTENCE`), `contentId` (normalized cross-story deduplication key: `word:<lemma>:<pos>:<senseId>` or `sentence:<pkgId>:<sentId>`), `frontTerm`, `backPersianMeaning`, `englishDefinition`, `normalizedLemma`, `pos`, `senseId`, `phonetic`, `exampleSentence`, `contextSentenceEn`, `contextSentenceFa`, `sourcePackageId`, `sourcePackageTitle`, `sourceGlossaryId`, `sourceSentenceId`, `state`, `repetitions`, `intervalDays`, `easeFactor`, `dueAtEpochMs`, `lastReviewedAtEpochMs`, `createdAtEpochMs`
   - **Indexes**: `dueAtEpochMs`, `state`, `sourcePackageId`, `frontTerm`, `contentId`, `cardType`

9. **`srs_review_logs`**
   - **PK**: `logId` (`INTEGER PRIMARY KEY AUTOINCREMENT`)
   - **FK**: `cardId` -> `srs_cards(cardId)` (`ON DELETE CASCADE`)
   - **Columns**: `cardId`, `qualityScore`, `previousIntervalDays`, `newIntervalDays`, `previousEaseFactor`, `newEaseFactor`, `reviewedAtEpochMs`
   - **Indexes**: `cardId`, `reviewedAtEpochMs`

10. **`learning_progress`**
    - **PK**: `progressId` (`TEXT`)
    - **Columns**: `packageId`, `packageTitleSnapshot`, `completedSentenceCount`, `totalSentenceCount`, `correctQuestionsCount`, `totalQuestionsCount`, `lastReadSentenceIndex`, `updatedAtEpochMs`
    - **Indexes**: UNIQUE(`packageId`), `updatedAtEpochMs`

11. **`user_vocabulary`**
    - **PK**: `vocabId` (`TEXT`)
    - **Columns**: `normalizedTerm`, `displayTerm`, `persianMeaning`, `phonetic`, `notesFa`, `sourcePackageId`, `masteryLevel`, `savedAtEpochMs`
    - **Indexes**: UNIQUE(`normalizedTerm`), `sourcePackageId`

12. **`reader_bookmarks`**
    - **PK**: `bookmarkId` (`TEXT`)
    - **Columns**: `packageId`, `packageTitleSnapshot`, `sentenceId`, `sentenceOrderIndex`, `noteFa`, `createdAtEpochMs`
    - **Indexes**: UNIQUE(`packageId`, `sentenceId`), `createdAtEpochMs`
