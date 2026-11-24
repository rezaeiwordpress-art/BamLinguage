package com.example.content.importer

import com.example.content.schema.ContentPackageJsonDto
import com.example.content.validation.ContentIntegrityAnalyzer
import com.example.content.validation.ContentIntegrityReport
import com.example.content.validation.ContentPackageValidator
import com.example.content.validation.ValidationOutcome
import com.example.core.common.CefrLevel
import com.example.core.common.ContentType
import com.example.domain.model.ComprehensionQuestion
import com.example.domain.model.ContentPackage
import com.example.domain.model.ContentPackageBundle
import com.example.domain.model.GlossaryEntry
import com.example.domain.model.GlossaryExample
import com.example.domain.model.GlossarySense
import com.example.domain.model.ImportValidationError
import com.example.domain.model.ImportValidationErrorCode
import com.example.domain.model.PackageImportResult
import com.example.domain.model.PackageMetadata
import com.example.domain.model.PronunciationInfo
import com.example.domain.model.Sentence
import com.example.domain.model.Speaker
import com.example.domain.model.WordOccurrence
import com.example.domain.repository.ContentRepository
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface PackageImportPreview {
  data class Ready(
    val rawJson: String,
    val dto: ContentPackageJsonDto,
    val existingVersion: Int?,
    val versionConflictError: ImportValidationError?,
    val integrityReport: ContentIntegrityReport,
  ) : PackageImportPreview {
    val canImport: Boolean
      get() = integrityReport.isValidForImport && versionConflictError == null

    val isUpgrade: Boolean
      get() = existingVersion != null && dto.version > existingVersion
  }

  data class ParseFailed(
    val errors: List<ImportValidationError>,
  ) : PackageImportPreview
}

class JsonContentPackageImporter(
  private val contentRepository: ContentRepository,
  private val validator: ContentPackageValidator = ContentPackageValidator(),
  private val integrityAnalyzer: ContentIntegrityAnalyzer = ContentIntegrityAnalyzer(validator),
  private val json: Json =
    Json {
      ignoreUnknownKeys = false
      isLenient = false
      coerceInputValues = false
    },
) {

  suspend fun previewFromJson(
    rawJson: String,
    allowSameVersionOverwrite: Boolean = false,
  ): PackageImportPreview {
    val sizeError = checkPayloadSize(rawJson)
    if (sizeError != null) {
      return PackageImportPreview.ParseFailed(listOf(sizeError))
    }

    val dto =
      decodeDto(rawJson)
        ?: return PackageImportPreview.ParseFailed(listOf(lastDecodeError(rawJson)))

    val report = integrityAnalyzer.analyzeDto(dto)
    val existing = if (dto.id.isNotBlank()) contentRepository.getPackageById(dto.id.trim()) else null
    val conflictError =
      if (existing != null &&
        (dto.version < existing.contentVersion ||
          (dto.version == existing.contentVersion && !allowSameVersionOverwrite))
      ) {
        ImportValidationError(
          code = ImportValidationErrorCode.PACKAGE_VERSION_CONFLICT,
          fieldPath = "version",
          messageFa =
            "بسته '${dto.id}' با نسخه ${existing.contentVersion} قبلاً نصب شده است. نسخه جدید (${dto.version}) باید بالاتر باشد.",
          messageEn =
            "Package '${dto.id}' already installed at version ${existing.contentVersion}; incoming version ${dto.version} is not newer.",
        )
      } else {
        null
      }

    return PackageImportPreview.Ready(
      rawJson = rawJson,
      dto = dto,
      existingVersion = existing?.contentVersion,
      versionConflictError = conflictError,
      integrityReport = report,
    )
  }

  suspend fun importFromJson(
    rawJson: String,
    isBundledSample: Boolean = false,
    allowSameVersionOverwrite: Boolean = false,
    nowEpochMs: Long = System.currentTimeMillis(),
  ): PackageImportResult {
    val sizeError = checkPayloadSize(rawJson)
    if (sizeError != null) {
      return PackageImportResult.Failure(listOf(sizeError))
    }

    val dto =
      try {
        json.decodeFromString(ContentPackageJsonDto.serializer(), rawJson)
      } catch (ex: SerializationException) {
        return PackageImportResult.Failure(
          listOf(
            ImportValidationError(
              code = ImportValidationErrorCode.INVALID_JSON_SYNTAX,
              fieldPath = "$",
              messageFa = "ساختار فایل JSON معتبر نیست یا فیلدهای ناشناخته دارد: ${ex.message ?: ""}",
              messageEn = "JSON parse failure: ${ex.message ?: "unknown"}",
            )
          )
        )
      } catch (ex: IllegalArgumentException) {
        return PackageImportResult.Failure(
          listOf(
            ImportValidationError(
              code = ImportValidationErrorCode.INVALID_JSON_SYNTAX,
              fieldPath = "$",
              messageFa = "ورودی JSON قابل پردازش نیست: ${ex.message ?: ""}",
              messageEn = "Illegal JSON argument: ${ex.message ?: "unknown"}",
            )
          )
        )
      }

    when (val validation = validator.validate(dto)) {
      is ValidationOutcome.Invalid -> return PackageImportResult.Failure(validation.errors)
      ValidationOutcome.Valid -> Unit
    }

    val existing = contentRepository.getPackageById(dto.id.trim())
    val wasUpgrade =
      if (existing != null) {
        if (dto.version < existing.contentVersion ||
          (dto.version == existing.contentVersion && !allowSameVersionOverwrite)
        ) {
          return PackageImportResult.Failure(
            listOf(
              ImportValidationError(
                code = ImportValidationErrorCode.PACKAGE_VERSION_CONFLICT,
                fieldPath = "version",
                messageFa =
                  "بسته '${dto.id}' با نسخه ${existing.contentVersion} قبلاً نصب شده است. نسخه جدید (${dto.version}) باید بالاتر باشد.",
                messageEn =
                  "Package '${dto.id}' already installed at version ${existing.contentVersion}; incoming version ${dto.version} is not newer.",
              )
            )
          )
        }
        true
      } else {
        false
      }

    val bundle = mapDtoToBundle(dto, isBundledSample = isBundledSample, nowEpochMs = nowEpochMs)

    return try {
      contentRepository.replacePackageTransactionally(bundle)
      PackageImportResult.Success(
        packageId = bundle.contentPackage.packageId,
        title = bundle.contentPackage.title,
        contentVersion = bundle.contentPackage.contentVersion,
        wasUpgrade = wasUpgrade,
        sentenceCount = bundle.sentences.size,
        glossaryCount = bundle.glossaryEntries.size,
        questionCount = bundle.questions.size,
      )
    } catch (t: Throwable) {
      PackageImportResult.Failure(
        listOf(
          ImportValidationError(
            code = ImportValidationErrorCode.DATABASE_FAILURE,
            fieldPath = "database",
            messageFa = "خطا در ذخیره‌سازی تراکنشی بسته در پایگاه داده: ${t.message ?: ""}",
            messageEn = "Transactional DB failure: ${t.message ?: "unknown"}",
          )
        )
      )
    }
  }

  private fun checkPayloadSize(rawJson: String): ImportValidationError? {
    if (rawJson.isBlank()) {
      return ImportValidationError(
        code = ImportValidationErrorCode.INVALID_JSON_SYNTAX,
        fieldPath = "$",
        messageFa = "فایل یا متن JSON خالی است.",
        messageEn = "JSON payload is empty.",
      )
    }
    if (rawJson.length > MAX_JSON_CHARS) {
      return ImportValidationError(
        code = ImportValidationErrorCode.INVALID_JSON_SYNTAX,
        fieldPath = "$",
        messageFa = "حجم فایل JSON بیش از حد مجاز (۵ مگابایت) است و برای جلوگیری از فشار حافظه رد شد.",
        messageEn = "JSON payload exceeds maximum safe size limit ($MAX_JSON_CHARS chars).",
      )
    }
    return null
  }

  private fun decodeDto(rawJson: String): ContentPackageJsonDto? =
    try {
      json.decodeFromString(ContentPackageJsonDto.serializer(), rawJson)
    } catch (_: Throwable) {
      null
    }

  private fun lastDecodeError(rawJson: String): ImportValidationError {
    val reason =
      try {
        json.decodeFromString(ContentPackageJsonDto.serializer(), rawJson)
        ""
      } catch (ex: Throwable) {
        ex.message.orEmpty()
      }
    return ImportValidationError(
      code = ImportValidationErrorCode.INVALID_JSON_SYNTAX,
      fieldPath = "$",
      messageFa = "ساختار فایل JSON معتبر نیست یا فیلدهای ناشناخته دارد: $reason",
      messageEn = "JSON parse failure: $reason",
    )
  }

  companion object {
    const val MAX_JSON_CHARS = 5 * 1024 * 1024
  }

  private fun mapDtoToBundle(
    dto: ContentPackageJsonDto,
    isBundledSample: Boolean,
    nowEpochMs: Long,
  ): ContentPackageBundle {
    val pkgId = dto.id.trim()
    val contentType = ContentType.fromWireValue(dto.type) ?: ContentType.STORY
    val level = CefrLevel.fromCode(dto.level) ?: CefrLevel.A1

    val contentPackage =
      ContentPackage(
        packageId = pkgId,
        schemaVersion = dto.schemaVersion,
        contentVersion = dto.version,
        contentType = contentType,
        level = level,
        topic = dto.topic.trim(),
        title = dto.title.trim(),
        titleFa = dto.titleFa.ifBlank { dto.title }.trim(),
        description = dto.description.ifBlank { dto.descriptionFa }.trim(),
        descriptionFa = dto.descriptionFa.ifBlank { dto.description }.trim(),
        metadata =
          PackageMetadata(
            author = dto.metadata.author.trim(),
            createdAt = dto.metadata.createdAt,
            updatedAt = dto.metadata.updatedAt,
            wordCount = dto.metadata.wordCount,
            sentenceCount = dto.metadata.sentenceCount.takeIf { it > 0 } ?: dto.sentences.size,
            glossaryCount = dto.metadata.glossaryCount.takeIf { it > 0 } ?: dto.glossary.size,
            estimatedMinutes = dto.estimatedMinutes,
            language = dto.language.ifBlank { "en" },
            translationLanguage = dto.translationLanguage.ifBlank { "fa" },
            tags = dto.metadata.tags.map { it.trim() }.filter { it.isNotEmpty() },
          ),
        importedAtEpochMs = nowEpochMs,
        isBundledSample = isBundledSample,
      )

    fun scopedId(rawId: String): String = "$pkgId::$rawId"

    val speakers =
      dto.speakers.map { spk ->
        Speaker(
          id = scopedId(spk.id),
          packageId = pkgId,
          sourceSpeakerId = spk.id,
          name = spk.name.trim(),
          nameFa = spk.nameFa.ifBlank { spk.name }.trim(),
          role = spk.role.trim(),
        )
      }

    val sentences =
      dto.sentences
        .mapIndexed { idx, s -> (s.orderIndex ?: idx) to s }
        .sortedBy { it.first }
        .map { (resolvedOrder, s) ->
          Sentence(
            id = scopedId(s.id),
            packageId = pkgId,
            sourceSentenceId = s.id,
            orderIndex = resolvedOrder,
            speakerId = s.speakerId?.takeIf { it.isNotBlank() }?.let { scopedId(it) },
            englishText = s.text,
            persianTranslation = s.translationFa,
            phoneticHint = s.phoneticHint?.takeIf { it.isNotBlank() },
            grammarNoteFa = s.grammarNoteFa?.takeIf { it.isNotBlank() },
          )
        }

    val nestedOccurrences = mutableListOf<WordOccurrence>()

    val glossaryEntries =
      dto.glossary.map { g ->
        val entryScopedId = scopedId(g.id)
        val normalized = g.term.trim().lowercase()
        val lemma = g.lemma.ifBlank { normalized }.trim().lowercase()
        val cefr = CefrLevel.fromCode(g.cefr) ?: CefrLevel.A1

        val senses =
          if (g.senses.isNotEmpty()) {
            g.senses.mapIndexed { sIdx, sense ->
              GlossarySense(
                id = "$entryScopedId::${sense.id}",
                glossaryEntryId = entryScopedId,
                sourceSenseId = sense.id,
                orderIndex = sIdx,
                meaningFa = sense.meaningFa.trim(),
                definitionEn = sense.definitionEn.trim(),
                examples =
                  sense.examples.map { ex ->
                    GlossaryExample(text = ex.text.trim(), translationFa = ex.translationFa.trim())
                  },
              )
            }
          } else {
            listOf(
              GlossarySense(
                id = "$entryScopedId::sense-1",
                glossaryEntryId = entryScopedId,
                sourceSenseId = "sense-1",
                orderIndex = 0,
                meaningFa = g.meaningFa.trim(),
                definitionEn = g.definitionEn.trim(),
                examples = emptyList(),
              )
            )
          }

        val primarySense = senses.first()
        val pronInfo =
          PronunciationInfo(
            uk = g.pronunciation?.uk?.takeIf { it.isNotBlank() },
            us = g.pronunciation?.us?.takeIf { it.isNotBlank() },
          )

        g.occurrences.forEach { occ ->
          nestedOccurrences +=
            WordOccurrence(
              packageId = pkgId,
              sentenceId = scopedId(occ.sentenceId),
              glossaryEntryId = entryScopedId,
              surfaceForm = occ.surface,
              startOffset = occ.start,
              endOffset = occ.end,
            )
        }

        GlossaryEntry(
          id = entryScopedId,
          packageId = pkgId,
          sourceGlossaryId = g.id,
          term = g.term.trim(),
          normalizedTerm = normalized,
          lemma = lemma,
          forms = g.forms.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct(),
          partOfSpeech = g.pos.ifBlank { "word" }.trim(),
          cefr = cefr,
          senses = senses,
          phonetic = pronInfo.primary(),
          pronunciation = pronInfo,
          persianMeaning = g.meaningFa.ifBlank { primarySense.meaningFa }.trim(),
          englishDefinition = g.definitionEn.ifBlank { primarySense.definitionEn }.trim(),
          exampleSentence = primarySense.examples.firstOrNull()?.text,
          synonyms = g.synonyms.map { it.trim() }.filter { it.isNotEmpty() },
          antonyms = g.antonyms.map { it.trim() }.filter { it.isNotEmpty() },
          sentenceIds = g.sentenceIds.map { scopedId(it) },
        )
      }

    val topLevelOccurrences =
      dto.wordOccurrences.map { occ ->
        WordOccurrence(
          packageId = pkgId,
          sentenceId = scopedId(occ.sentenceId),
          glossaryEntryId = scopedId(occ.glossaryEntryId),
          surfaceForm = occ.surfaceForm,
          startOffset = occ.startOffset,
          endOffset = occ.endOffset,
        )
      }

    val mergedOccurrences =
      (nestedOccurrences + topLevelOccurrences).distinctBy {
        "${it.sentenceId}:${it.glossaryEntryId}:${it.startOffset}:${it.endOffset}"
      }

    val questions =
      dto.questions
        .mapIndexed { idx, q -> (q.orderIndex ?: idx) to q }
        .sortedBy { it.first }
        .map { (resolvedOrder, q) ->
          ComprehensionQuestion(
            id = scopedId(q.id),
            packageId = pkgId,
            sourceQuestionId = q.id,
            orderIndex = resolvedOrder,
            promptEn = q.promptEn.trim(),
            promptFa = q.promptFa.ifBlank { q.promptEn }.trim(),
            options = q.options.map { it.trim() },
            correctOptionIndex = q.correctOptionIndex,
            explanationFa = q.explanationFa?.takeIf { it.isNotBlank() },
          )
        }

    return ContentPackageBundle(
      contentPackage = contentPackage,
      speakers = speakers,
      sentences = sentences,
      glossaryEntries = glossaryEntries,
      wordOccurrences = mergedOccurrences,
      questions = questions,
    )
  }
}
