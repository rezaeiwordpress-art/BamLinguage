# BamLinguage — JSON Content Schema Documentation (`schemaVersion: 1`)

## Overview
All offline story and dialogue packages use `schemaVersion: 1` and are parsed via strict `kotlinx.serialization` (`ignoreUnknownKeys = false`), bounded to 5 MB (`5 * 1024 * 1024` UTF-16 chars), validated by `ContentPackageValidator`, audited for dictionary coverage and linguistic quality by `ContentIntegrityAnalyzer`, version-checked against `ContentRepository`, and committed atomically inside a Room `@Transaction`. Both integer (`1`) and string (`"sent_1"`) identifiers are supported seamlessly via `FlexibleIdSerializer`.

## Top-Level Fields
| Field | Type | Required | Constraints |
|---|---|---|---|
| `schemaVersion` | `Int` | Yes | Must equal `1` |
| `id` | `String` | Yes | Non-blank unique package identifier |
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
| `speakers` | `Array` | Conditional | Required (`>= 1`) when `type == "dialogue"`; unique `id`s |
| `sentences` | `Array` | Yes | `>= 1` items `{ id, orderIndex?, text, translationFa, speakerId?, phoneticHint?, grammarNoteFa? }`; unique `id` and unique `orderIndex` |
| `glossary` | `Array` | No | `{ id, term, lemma, forms[], pos, cefr, senses[], pronunciation { uk, us }, synonyms[], antonyms[], sentenceIds[], occurrences[] }` |
| `wordOccurrences` | `Array` | No | Optional top-level occurrence spans (merged with nested `glossary[].occurrences[]`) |
| `questions` | `Array` | No | `{ id, orderIndex?, promptEn, promptFa, options[], correctOptionIndex, explanationFa? }` |

## Glossary Sense & Occurrence Rules
- **`senses[]`**: Each sense declares `{ id, meaningFa, definitionEn, examples: [{ text, translationFa }] }`. Sense IDs must be unique within a glossary entry.
- **`occurrences[]`**: Each occurrence declares `{ sentenceId, surface, start, end }`. Validator enforces `0 <= start < end <= sentence.text.length` and `sentence.text.substring(start, end) == surface` (exact UTF-16 indices).

## Integrity Checks (`ContentPackageValidator` & `ContentIntegrityAnalyzer`)
1. **Fatal Structural Errors** (block import):
   - Oversized payload (`> 5 MB`) or invalid/unknown JSON syntax.
   - `schemaVersion != 1` or non-positive `version` or version conflict (`<= installedVersion`).
   - Missing required fields or invalid CEFR/contentType codes.
   - Duplicate internal IDs or duplicate `orderIndex`.
   - Broken referential integrity (`speakerId`, `sentenceIds`, `occurrences[].sentenceId`).
   - Out-of-bounds or mismatched UTF-16 occurrence offsets (`sentence.text.substring(start, end) != surface`).
   - Invalid question options (`< 2` options) or out-of-range `correctOptionIndex`.
2. **Missing Vocabulary Warnings** (reported transparently in Pre-Import Review & Package Inspector):
   - Computes real token coverage (`coveredTokens / totalMeaningfulTokens`) across all sentences and lists uncovered surface tokens without inventing definitions.
3. **Linguistic Quality Warnings** (non-blocking):
   - Missing `lemma`, lemma-morphology mismatch, missing `pos`, missing IPA `pronunciation`, missing `definitionEn`, non-deterministic multiword phrase collisions across glossary entries, and metadata count mismatches.
