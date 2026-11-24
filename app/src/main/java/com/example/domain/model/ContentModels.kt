package com.example.domain.model

import com.example.core.common.CefrLevel
import com.example.core.common.ContentType

data class PackageMetadata(
  val author: String,
  val createdAt: String = "",
  val updatedAt: String = "",
  val wordCount: Int = 0,
  val sentenceCount: Int = 0,
  val glossaryCount: Int = 0,
  val estimatedMinutes: Int,
  val language: String,
  val translationLanguage: String,
  val tags: List<String> = emptyList(),
)

data class ContentPackage(
  val packageId: String,
  val schemaVersion: Int,
  val contentVersion: Int,
  val contentType: ContentType,
  val level: CefrLevel,
  val topic: String,
  val title: String,
  val titleFa: String,
  val description: String,
  val descriptionFa: String,
  val metadata: PackageMetadata,
  val importedAtEpochMs: Long,
  val isBundledSample: Boolean = false,
)

data class Speaker(
  val id: String,
  val packageId: String,
  val sourceSpeakerId: String,
  val name: String,
  val nameFa: String,
  val role: String,
)

data class Sentence(
  val id: String,
  val packageId: String,
  val sourceSentenceId: String,
  val orderIndex: Int,
  val speakerId: String?,
  val englishText: String,
  val persianTranslation: String,
  val phoneticHint: String? = null,
  val grammarNoteFa: String? = null,
)

data class GlossaryExample(
  val text: String,
  val translationFa: String,
)

data class GlossarySense(
  val id: String,
  val glossaryEntryId: String,
  val sourceSenseId: String,
  val orderIndex: Int,
  val meaningFa: String,
  val definitionEn: String,
  val examples: List<GlossaryExample> = emptyList(),
)

data class PronunciationInfo(
  val uk: String? = null,
  val us: String? = null,
) {
  fun primary(): String? = us ?: uk
}

data class GlossaryEntry(
  val id: String,
  val packageId: String,
  val sourceGlossaryId: String,
  val term: String,
  val normalizedTerm: String,
  val lemma: String,
  val forms: List<String>,
  val partOfSpeech: String,
  val cefr: CefrLevel,
  val senses: List<GlossarySense>,
  val phonetic: String?,
  val pronunciation: PronunciationInfo,
  val persianMeaning: String,
  val englishDefinition: String,
  val exampleSentence: String?,
  val synonyms: List<String> = emptyList(),
  val antonyms: List<String> = emptyList(),
  val sentenceIds: List<String> = emptyList(),
) {
  val isMultiword: Boolean
    get() = term.trim().contains(' ') || normalizedTerm.trim().contains(' ')
}

data class WordOccurrence(
  val id: Long = 0L,
  val packageId: String,
  val sentenceId: String,
  val glossaryEntryId: String,
  val surfaceForm: String,
  val startOffset: Int,
  val endOffset: Int,
)

data class ComprehensionQuestion(
  val id: String,
  val packageId: String,
  val sourceQuestionId: String,
  val orderIndex: Int,
  val promptEn: String,
  val promptFa: String,
  val options: List<String>,
  val correctOptionIndex: Int,
  val explanationFa: String?,
)

data class ContentPackageBundle(
  val contentPackage: ContentPackage,
  val speakers: List<Speaker>,
  val sentences: List<Sentence>,
  val glossaryEntries: List<GlossaryEntry>,
  val wordOccurrences: List<WordOccurrence>,
  val questions: List<ComprehensionQuestion>,
)

data class ContentPackageSummary(
  val contentPackage: ContentPackage,
  val sentenceCount: Int,
  val glossaryCount: Int,
  val questionCount: Int,
  val speakerCount: Int,
)
