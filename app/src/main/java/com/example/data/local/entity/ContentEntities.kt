package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "content_packages",
  indices = [
    Index(value = ["level"]),
    Index(value = ["contentType"]),
    Index(value = ["importedAtEpochMs"]),
  ],
)
data class ContentPackageEntity(
  @PrimaryKey val packageId: String,
  val schemaVersion: Int,
  val contentVersion: Int,
  val contentType: String,
  val level: String,
  val topic: String,
  val title: String,
  val titleFa: String,
  val description: String,
  val descriptionFa: String,
  val author: String,
  val createdAt: String = "",
  val updatedAt: String = "",
  val wordCount: Int = 0,
  val sentenceCountMeta: Int = 0,
  val glossaryCountMeta: Int = 0,
  val estimatedMinutes: Int,
  val language: String,
  val translationLanguage: String,
  val tags: List<String>,
  val importedAtEpochMs: Long,
  val isBundledSample: Boolean,
)

@Entity(
  tableName = "speakers",
  foreignKeys = [
    ForeignKey(
      entity = ContentPackageEntity::class,
      parentColumns = ["packageId"],
      childColumns = ["packageId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [
    Index(value = ["packageId"]),
  ],
)
data class SpeakerEntity(
  @PrimaryKey val id: String,
  val packageId: String,
  val sourceSpeakerId: String,
  val name: String,
  val nameFa: String,
  val role: String,
)

@Entity(
  tableName = "sentences",
  foreignKeys = [
    ForeignKey(
      entity = ContentPackageEntity::class,
      parentColumns = ["packageId"],
      childColumns = ["packageId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = SpeakerEntity::class,
      parentColumns = ["id"],
      childColumns = ["speakerId"],
      onDelete = ForeignKey.SET_NULL,
    ),
  ],
  indices = [
    Index(value = ["packageId", "orderIndex"], unique = true),
    Index(value = ["speakerId"]),
  ],
)
data class SentenceEntity(
  @PrimaryKey val id: String,
  val packageId: String,
  val sourceSentenceId: String,
  val orderIndex: Int,
  val speakerId: String?,
  val englishText: String,
  val persianTranslation: String,
  val phoneticHint: String?,
  val grammarNoteFa: String?,
)

@Entity(
  tableName = "glossary_entries",
  foreignKeys = [
    ForeignKey(
      entity = ContentPackageEntity::class,
      parentColumns = ["packageId"],
      childColumns = ["packageId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [
    Index(value = ["packageId", "normalizedTerm"]),
    Index(value = ["normalizedTerm"]),
    Index(value = ["lemma"]),
  ],
)
data class GlossaryEntryEntity(
  @PrimaryKey val id: String,
  val packageId: String,
  val sourceGlossaryId: String,
  val term: String,
  val normalizedTerm: String,
  val lemma: String = "",
  val forms: List<String> = emptyList(),
  val partOfSpeech: String,
  val cefr: String = "A1",
  val phonetic: String?,
  val pronunciationUk: String? = null,
  val pronunciationUs: String? = null,
  val persianMeaning: String,
  val englishDefinition: String,
  val exampleSentence: String?,
  val synonyms: List<String> = emptyList(),
  val antonyms: List<String> = emptyList(),
  val sentenceIds: List<String> = emptyList(),
)

@Entity(
  tableName = "glossary_senses",
  foreignKeys = [
    ForeignKey(
      entity = GlossaryEntryEntity::class,
      parentColumns = ["id"],
      childColumns = ["glossaryEntryId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = ContentPackageEntity::class,
      parentColumns = ["packageId"],
      childColumns = ["packageId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["glossaryEntryId"]),
    Index(value = ["packageId"]),
  ],
)
data class GlossarySenseEntity(
  @PrimaryKey val id: String,
  val glossaryEntryId: String,
  val packageId: String,
  val sourceSenseId: String,
  val orderIndex: Int,
  val meaningFa: String,
  val definitionEn: String,
  val examplesJson: String,
)

@Entity(
  tableName = "word_occurrences",
  foreignKeys = [
    ForeignKey(
      entity = ContentPackageEntity::class,
      parentColumns = ["packageId"],
      childColumns = ["packageId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = SentenceEntity::class,
      parentColumns = ["id"],
      childColumns = ["sentenceId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = GlossaryEntryEntity::class,
      parentColumns = ["id"],
      childColumns = ["glossaryEntryId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["packageId"]),
    Index(value = ["sentenceId"]),
    Index(value = ["glossaryEntryId"]),
  ],
)
data class WordOccurrenceEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val packageId: String,
  val sentenceId: String,
  val glossaryEntryId: String,
  val surfaceForm: String,
  val startOffset: Int,
  val endOffset: Int,
)

@Entity(
  tableName = "questions",
  foreignKeys = [
    ForeignKey(
      entity = ContentPackageEntity::class,
      parentColumns = ["packageId"],
      childColumns = ["packageId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [
    Index(value = ["packageId", "orderIndex"], unique = true),
  ],
)
data class QuestionEntity(
  @PrimaryKey val id: String,
  val packageId: String,
  val sourceQuestionId: String,
  val orderIndex: Int,
  val promptEn: String,
  val promptFa: String,
  val options: List<String>,
  val correctOptionIndex: Int,
  val explanationFa: String?,
)
