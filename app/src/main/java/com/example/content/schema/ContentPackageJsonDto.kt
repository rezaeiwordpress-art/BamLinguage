package com.example.content.schema

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Accepts both JSON integer/long IDs (e.g. `1`) and string IDs (e.g. `"sent_1"`).
 */
object FlexibleIdSerializer : KSerializer<String> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("FlexibleId", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): String {
    if (decoder is JsonDecoder) {
      val element = decoder.decodeJsonElement()
      if (element is JsonPrimitive) {
        return element.content
      }
    }
    return decoder.decodeString()
  }

  override fun serialize(encoder: Encoder, value: String) {
    encoder.encodeString(value)
  }
}

@Serializable
data class ContentPackageJsonDto(
  val schemaVersion: Int = 0,
  val id: String = "",
  val version: Int = 0,
  val type: String = "",
  val title: String = "",
  val titleFa: String = "",
  val description: String = "",
  val descriptionFa: String = "",
  val level: String = "",
  val topic: String = "",
  val language: String = "en",
  val translationLanguage: String = "fa",
  val estimatedMinutes: Int = 0,
  val metadata: PackageMetadataJsonDto = PackageMetadataJsonDto(),
  val speakers: List<SpeakerJsonDto> = emptyList(),
  val sentences: List<SentenceJsonDto> = emptyList(),
  val glossary: List<GlossaryEntryJsonDto> = emptyList(),
  val wordOccurrences: List<WordOccurrenceJsonDto> = emptyList(),
  val questions: List<QuestionJsonDto> = emptyList(),
)

@Serializable
data class PackageMetadataJsonDto(
  val author: String = "",
  val createdAt: String = "",
  val updatedAt: String = "",
  val wordCount: Int = 0,
  val sentenceCount: Int = 0,
  val glossaryCount: Int = 0,
  val tags: List<String> = emptyList(),
)

@Serializable
data class SpeakerJsonDto(
  @Serializable(with = FlexibleIdSerializer::class) val id: String = "",
  val name: String = "",
  val nameFa: String = "",
  val role: String = "",
)

@Serializable
data class SentenceJsonDto(
  @Serializable(with = FlexibleIdSerializer::class) val id: String = "",
  val orderIndex: Int? = null,
  val text: String = "",
  val translationFa: String = "",
  @Serializable(with = FlexibleIdSerializer::class) val speakerId: String? = null,
  val phoneticHint: String? = null,
  val grammarNoteFa: String? = null,
)

@Serializable
data class GlossaryExampleJsonDto(
  val text: String = "",
  val translationFa: String = "",
)

@Serializable
data class GlossarySenseJsonDto(
  @Serializable(with = FlexibleIdSerializer::class) val id: String = "",
  val meaningFa: String = "",
  val definitionEn: String = "",
  val examples: List<GlossaryExampleJsonDto> = emptyList(),
)

@Serializable
data class PronunciationJsonDto(
  val uk: String? = null,
  val us: String? = null,
)

@Serializable
data class GlossaryOccurrenceJsonDto(
  @Serializable(with = FlexibleIdSerializer::class) val sentenceId: String = "",
  val surface: String = "",
  val start: Int = -1,
  val end: Int = -1,
)

@Serializable
data class GlossaryEntryJsonDto(
  @Serializable(with = FlexibleIdSerializer::class) val id: String = "",
  val term: String = "",
  val lemma: String = "",
  val forms: List<String> = emptyList(),
  val pos: String = "",
  val cefr: String = "A1",
  val senses: List<GlossarySenseJsonDto> = emptyList(),
  val meaningFa: String = "",
  val definitionEn: String = "",
  val pronunciation: PronunciationJsonDto? = null,
  val synonyms: List<String> = emptyList(),
  val antonyms: List<String> = emptyList(),
  val sentenceIds: List<@Serializable(with = FlexibleIdSerializer::class) String> = emptyList(),
  val occurrences: List<GlossaryOccurrenceJsonDto> = emptyList(),
)

@Serializable
data class WordOccurrenceJsonDto(
  @Serializable(with = FlexibleIdSerializer::class) val sentenceId: String = "",
  @Serializable(with = FlexibleIdSerializer::class) val glossaryEntryId: String = "",
  val surfaceForm: String = "",
  val startOffset: Int = -1,
  val endOffset: Int = -1,
)

@Serializable
data class QuestionJsonDto(
  @Serializable(with = FlexibleIdSerializer::class) val id: String = "",
  val orderIndex: Int? = null,
  val promptEn: String = "",
  val promptFa: String = "",
  val options: List<String> = emptyList(),
  val correctOptionIndex: Int = -1,
  val explanationFa: String? = null,
)
