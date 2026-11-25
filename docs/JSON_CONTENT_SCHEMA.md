# BamLinguage — JSON Content Schema Documentation (`schemaVersion: 1`)

## Overview
All offline story and dialogue packages use `schemaVersion: 1` and are parsed via strict `kotlinx.serialization` (`ignoreUnknownKeys = false`), bounded to 5 MB (`5 * 1024 * 1024` UTF-16 chars), validated by `ContentPackageValidator`, audited for dictionary and phrase coverage by `ContentIntegrityAnalyzer`, version-checked against `ContentRepository`, and committed atomically inside a Room `@Transaction`. Both integer (`1`) and string (`"sent_1"`) identifiers are supported via `FlexibleIdSerializer`.

## Top-Level Fields
| Field | Type | Required | Constraints |
|---|---|---|---|
| `schemaVersion` | `Int` | Yes | Must equal `1` |
| `id` | `String` | Yes | Non-blank unique package identifier (whitespace trimmed) |
| `version` | `Int` | Yes | `>= 1`; upgrades require strictly higher version than installed package |
| `type` | `String` | Yes | `"story"` or `"dialogue"` |
| `title` | `String` | Yes | Non-blank English title |
| `titleFa` | `String` | No | Persian title (falls back to `title` if omitted) |
| `description` | `String` | Conditional | English description (at least one of `description` or `descriptionFa` non-blank) |
| `descriptionFa` | `String` | Conditional | Persian description |
| `level` | `String` | Yes | `"A1"`, `"A2"`, `"B1"`, `"B2"`, `"C1"`, or `"C2"` |
| `topic` | `String` | Yes | Non-blank |
| `language` | `String` | No | Defaults to `"en"` |
| `translationLanguage` | `String` | No | Defaults to `"fa"` |
| `estimatedMinutes` | `Int` | Yes | `>= 1` |
| `metadata` | `Object` | Yes | `{ author, createdAt, updatedAt, wordCount, sentenceCount, glossaryCount, tags[] }` |
| `speakers` | `Array` | Conditional | Required (`>= 1`) when `type == "dialogue"`; unique trimmed `id`s |
| `sentences` | `Array` | Yes | `>= 1` items `{ id, orderIndex?, text, translationFa, speakerId?, phoneticHint?, grammarNoteFa? }`; unique trimmed `id` and `orderIndex` |
| `glossary` | `Array` | No | `{ id, term, lemma, forms[], pos, cefr, senses[], pronunciation { uk, us }, synonyms[], antonyms[], sentenceIds[], occurrences[] }` |
| `wordOccurrences` | `Array` | No | Optional top-level occurrence spans (merged with nested `glossary[].occurrences[]`) |
| `questions` | `Array` | No | `{ id, orderIndex?, promptEn, promptFa, options[], correctOptionIndex, explanationFa? }` |

## Glossary Sense & Occurrence Rules
- **`senses[]`**: Each sense declares `{ id, meaningFa, definitionEn, examples: [{ text, translationFa }] }`. Trimmed sense IDs must be unique within a glossary entry.
- **`occurrences[]`**: Each occurrence declares `{ sentenceId, surface, start, end }`. Validator enforces:
  - `surface` is non-blank with no leading/trailing whitespace.
  - `0 <= start < end <= sentence.text.length` and `sentence.text.substring(start, end) == surface`.
  - Spans must fall on word boundaries (not mid-word slices inside adjacent letters/digits).
  - No duplicate `(sentenceId, entryId, start, end)` occurrence spans.
  - When `glossary[].sentenceIds` is populated, every `occurrences[].sentenceId` for that entry must be listed in `sentenceIds`.

## Integrity Checks (`ContentPackageValidator` & `ContentIntegrityAnalyzer`)
1. **Fatal Structural Errors** (block import):
   - Oversized payload (`> 5 MB`), invalid/unknown JSON syntax, or `version` conflict (`<= installedVersion`).
   - Duplicate trimmed IDs (`speakers`, `sentences`, `glossary`, `senses`, `questions`), duplicate `orderIndex`, or duplicate occurrence spans.
   - Broken referential integrity (`speakerId`, `sentenceIds`, `wordOccurrences.glossaryEntryId`, `occurrences[].sentenceId`, missing `sentenceIds` for an occurrence).
   - Invalid, out-of-bounds, blank, or mid-word UTF-16 occurrence offsets.
2. **Vocabulary & Phrase Coverage Warnings** (shown transparently in Pre-Import Review & Package Inspector):
   - Measures verified single-token coverage plus phrase coverage (`detectedPhrases` across sentences) without inflating scores from invalid occurrences.
   - Lists `missingWords` and `missingPhrases` explicitly when detected.
   - Coverage is never reported as `100%` (`isCoverageVerified == false`, capped at `<= 99%`) whenever any fatal error, missing word, or missing phrase exists.
3. **Linguistic Quality Warnings** (non-blocking):
   - Missing `lemma`, lemma-morphology mismatch, missing `pos`, missing IPA `pronunciation`, missing `definitionEn`, phrase span collisions, and metadata count mismatches.
