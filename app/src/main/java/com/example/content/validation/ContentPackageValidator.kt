package com.example.content.validation

import com.example.content.schema.ContentPackageJsonDto
import com.example.core.common.CefrLevel
import com.example.core.common.ContentType
import com.example.domain.model.ImportValidationError
import com.example.domain.model.ImportValidationErrorCode

sealed interface ValidationOutcome {
  data object Valid : ValidationOutcome

  data class Invalid(val errors: List<ImportValidationError>) : ValidationOutcome
}

class ContentPackageValidator {

  fun validate(dto: ContentPackageJsonDto): ValidationOutcome {
    val errors = mutableListOf<ImportValidationError>()

    if (dto.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
      errors +=
        ImportValidationError(
          code = ImportValidationErrorCode.UNSUPPORTED_SCHEMA_VERSION,
          fieldPath = "schemaVersion",
          messageFa = "نسخه طرحواره (${dto.schemaVersion}) پشتیبانی نمی‌شود. فقط نسخه ۱ معتبر است.",
          messageEn = "Unsupported schemaVersion ${dto.schemaVersion}. Expected $SUPPORTED_SCHEMA_VERSION.",
        )
    }

    if (dto.id.isBlank()) {
      errors += missingField("id", "شناسه بسته (id) نمی‌تواند خالی باشد.")
    }

    if (dto.version < 1) {
      errors +=
        ImportValidationError(
          code = ImportValidationErrorCode.INVALID_CONTENT_VERSION,
          fieldPath = "version",
          messageFa = "نسخه محتوا (version) باید ۱ یا بیشتر باشد.",
          messageEn = "version must be >= 1.",
        )
    }

    val contentType = ContentType.fromWireValue(dto.type)
    if (contentType == null) {
      errors +=
        ImportValidationError(
          code = ImportValidationErrorCode.INVALID_CONTENT_TYPE,
          fieldPath = "type",
          messageFa = "نوع محتوا باید story یا dialogue باشد.",
          messageEn = "type must be 'story' or 'dialogue'.",
        )
    }

    if (CefrLevel.fromCode(dto.level) == null) {
      errors +=
        ImportValidationError(
          code = ImportValidationErrorCode.INVALID_CEFR_LEVEL,
          fieldPath = "level",
          messageFa = "سطح CEFR نامعتبر است. مقادیر مجاز: A1, A2, B1, B2, C1, C2.",
          messageEn = "Invalid CEFR level '${dto.level}'. Allowed: A1..C2.",
        )
    }

    if (dto.topic.isBlank()) {
      errors += missingField("topic", "موضوع بسته (topic) الزامی است.")
    }
    if (dto.title.isBlank()) {
      errors += missingField("title", "عنوان انگلیسی بسته (title) الزامی است.")
    }
    if (dto.description.isBlank() && dto.descriptionFa.isBlank()) {
      errors += missingField("descriptionFa", "توضیحات بسته الزامی است.")
    }
    if (dto.metadata.author.isBlank()) {
      errors += missingField("metadata.author", "نام نویسنده در metadata الزامی است.")
    }
    if (dto.estimatedMinutes < 1) {
      errors += missingField("estimatedMinutes", "زمان تخمینی مطالعه باید حداقل ۱ دقیقه باشد.")
    }

    if (dto.sentences.isEmpty()) {
      errors += missingField("sentences", "بسته محتوا باید حداقل یک جمله داشته باشد.")
    }

    if (contentType == ContentType.DIALOGUE && dto.speakers.isEmpty()) {
      errors +=
        ImportValidationError(
          code = ImportValidationErrorCode.INVALID_REFERENTIAL_INTEGRITY,
          fieldPath = "speakers",
          messageFa = "بسته‌های گفت‌وگو (dialogue) باید حداقل یک گوینده داشته باشند.",
          messageEn = "Dialogue packages must declare at least one speaker.",
        )
    }

    val speakerIds = mutableSetOf<String>()
    dto.speakers.forEachIndexed { index, spk ->
      if (spk.id.isBlank()) {
        errors += missingField("speakers[$index].id", "شناسه گوینده خالی است.")
      } else if (!speakerIds.add(spk.id)) {
        errors += duplicateId("speakers[$index].id", "شناسه گوینده تکراری است: ${spk.id}")
      }
      if (spk.name.isBlank()) {
        errors += missingField("speakers[$index].name", "نام گوینده خالی است.")
      }
    }

    val sentenceMap = mutableMapOf<String, String>()
    val sentenceOrderIndices = mutableSetOf<Int>()
    dto.sentences.forEachIndexed { index, sentence ->
      if (sentence.id.isBlank()) {
        errors += missingField("sentences[$index].id", "شناسه جمله خالی است.")
      } else if (sentenceMap.containsKey(sentence.id)) {
        errors += duplicateId("sentences[$index].id", "شناسه جمله تکراری است: ${sentence.id}")
      } else {
        sentenceMap[sentence.id] = sentence.text
      }

      val resolvedOrder = sentence.orderIndex ?: index
      if (resolvedOrder < 0) {
        errors += missingField("sentences[$index].orderIndex", "ترتیب جمله نمی‌تواند منفی باشد.")
      } else if (!sentenceOrderIndices.add(resolvedOrder)) {
        errors += duplicateId("sentences[$index].orderIndex", "شماره ترتیب جمله تکراری است: $resolvedOrder")
      }

      if (sentence.text.isBlank()) {
        errors += missingField("sentences[$index].text", "متن انگلیسی جمله خالی است.")
      }
      if (sentence.translationFa.isBlank()) {
        errors += missingField("sentences[$index].translationFa", "ترجمه فارسی جمله خالی است.")
      }
      if (!sentence.speakerId.isNullOrBlank() && sentence.speakerId !in speakerIds) {
        errors +=
          ImportValidationError(
            code = ImportValidationErrorCode.INVALID_REFERENTIAL_INTEGRITY,
            fieldPath = "sentences[$index].speakerId",
            messageFa = "گوینده '${sentence.speakerId}' در فهرست speakers تعریف نشده است.",
            messageEn = "Sentence speakerId '${sentence.speakerId}' not found in speakers.",
          )
      }
    }

    val glossaryIds = mutableSetOf<String>()
    dto.glossary.forEachIndexed { index, entry ->
      if (entry.id.isBlank()) {
        errors += missingField("glossary[$index].id", "شناسه واژه در واژه‌نامه خالی است.")
      } else if (!glossaryIds.add(entry.id)) {
        errors += duplicateId("glossary[$index].id", "شناسه واژه تکراری است: ${entry.id}")
      }
      if (entry.term.isBlank()) {
        errors += missingField("glossary[$index].term", "عنوان واژه انگلیسی خالی است.")
      }
      if (CefrLevel.fromCode(entry.cefr) == null) {
        errors +=
          ImportValidationError(
            code = ImportValidationErrorCode.INVALID_CEFR_LEVEL,
            fieldPath = "glossary[$index].cefr",
            messageFa = "سطح CEFR واژه '${entry.term}' نامعتبر است: ${entry.cefr}",
            messageEn = "Invalid CEFR level '${entry.cefr}' on glossary entry '${entry.id}'.",
          )
      }
      val hasSenses = entry.senses.isNotEmpty()
      val hasFallbackMeaning = entry.meaningFa.isNotBlank()
      if (!hasSenses && !hasFallbackMeaning) {
        errors += missingField("glossary[$index].senses", "حداقل یک معنی فارسی برای واژه '${entry.term}' الزامی است.")
      }
      val senseIds = mutableSetOf<String>()
      entry.senses.forEachIndexed { sIdx, sense ->
        if (sense.id.isBlank()) {
          errors += missingField("glossary[$index].senses[$sIdx].id", "شناسه معنی واژه خالی است.")
        } else if (!senseIds.add(sense.id)) {
          errors += duplicateId("glossary[$index].senses[$sIdx].id", "شناسه معنی تکراری است: ${sense.id}")
        }
        if (sense.meaningFa.isBlank()) {
          errors += missingField("glossary[$index].senses[$sIdx].meaningFa", "معنی فارسی در sense خالی است.")
        }
      }

      entry.sentenceIds.forEach { refSentenceId ->
        if (refSentenceId !in sentenceMap) {
          errors +=
            ImportValidationError(
              code = ImportValidationErrorCode.INVALID_REFERENTIAL_INTEGRITY,
              fieldPath = "glossary[$index].sentenceIds",
              messageFa = "جمله '$refSentenceId' در ارجاع sentenceIds واژه '${entry.term}' یافت نشد.",
              messageEn = "Glossary entry '${entry.id}' references missing sentenceId '$refSentenceId'.",
            )
        }
      }

      entry.occurrences.forEachIndexed { oIdx, occ ->
        validateOffset(
          fieldPrefix = "glossary[$index].occurrences[$oIdx]",
          sentenceId = occ.sentenceId,
          surface = occ.surface,
          start = occ.start,
          end = occ.end,
          sentenceMap = sentenceMap,
          errors = errors,
        )
      }
    }

    dto.wordOccurrences.forEachIndexed { index, occ ->
      if (occ.glossaryEntryId !in glossaryIds) {
        errors +=
          ImportValidationError(
            code = ImportValidationErrorCode.INVALID_REFERENTIAL_INTEGRITY,
            fieldPath = "wordOccurrences[$index].glossaryEntryId",
            messageFa = "مدخل واژه‌نامه '${occ.glossaryEntryId}' یافت نشد.",
            messageEn = "WordOccurrence references missing glossaryEntryId '${occ.glossaryEntryId}'.",
          )
      }
      validateOffset(
        fieldPrefix = "wordOccurrences[$index]",
        sentenceId = occ.sentenceId,
        surface = occ.surfaceForm,
        start = occ.startOffset,
        end = occ.endOffset,
        sentenceMap = sentenceMap,
        errors = errors,
      )
    }

    val questionIds = mutableSetOf<String>()
    val questionOrderIndices = mutableSetOf<Int>()
    dto.questions.forEachIndexed { index, question ->
      if (question.id.isBlank()) {
        errors += missingField("questions[$index].id", "شناسه پرسش خالی است.")
      } else if (!questionIds.add(question.id)) {
        errors += duplicateId("questions[$index].id", "شناسه پرسش تکراری است: ${question.id}")
      }
      val resolvedOrder = question.orderIndex ?: index
      if (resolvedOrder < 0) {
        errors += missingField("questions[$index].orderIndex", "ترتیب پرسش نمی‌تواند منفی باشد.")
      } else if (!questionOrderIndices.add(resolvedOrder)) {
        errors += duplicateId("questions[$index].orderIndex", "ترتیب پرسش تکراری است: $resolvedOrder")
      }
      if (question.promptEn.isBlank()) {
        errors += missingField("questions[$index].promptEn", "متن انگلیسی پرسش خالی است.")
      }
      if (question.options.size < 2) {
        errors +=
          ImportValidationError(
            code = ImportValidationErrorCode.INVALID_QUESTION_OPTIONS,
            fieldPath = "questions[$index].options",
            messageFa = "هر پرسش درک مطلب باید حداقل ۲ گزینه داشته باشد.",
            messageEn = "Question must have at least 2 options.",
          )
      } else if (question.correctOptionIndex !in question.options.indices) {
        errors +=
          ImportValidationError(
            code = ImportValidationErrorCode.INVALID_QUESTION_OPTIONS,
            fieldPath = "questions[$index].correctOptionIndex",
            messageFa = "شاخص گزینه صحیح (${question.correctOptionIndex}) خارج از محدوده گزینه‌ها است.",
            messageEn = "correctOptionIndex ${question.correctOptionIndex} out of bounds for ${question.options.size} options.",
          )
      }
    }

    return if (errors.isEmpty()) ValidationOutcome.Valid else ValidationOutcome.Invalid(errors)
  }

  private fun validateOffset(
    fieldPrefix: String,
    sentenceId: String,
    surface: String,
    start: Int,
    end: Int,
    sentenceMap: Map<String, String>,
    errors: MutableList<ImportValidationError>,
  ) {
    val sentenceText = sentenceMap[sentenceId]
    if (sentenceText == null) {
      errors +=
        ImportValidationError(
          code = ImportValidationErrorCode.INVALID_REFERENTIAL_INTEGRITY,
          fieldPath = "$fieldPrefix.sentenceId",
          messageFa = "جمله '$sentenceId' برای رخداد واژه یافت نشد.",
          messageEn = "Occurrence references missing sentenceId '$sentenceId'.",
        )
      return
    }
    if (start < 0 || end <= start || end > sentenceText.length) {
      errors +=
        ImportValidationError(
          code = ImportValidationErrorCode.INVALID_WORD_OFFSET,
          fieldPath = fieldPrefix,
          messageFa = "محدوده UTF-16 واژه در جمله '$sentenceId' نامعتبر است [$start, $end).",
          messageEn = "Invalid UTF-16 offset [$start, $end) for sentence length ${sentenceText.length}.",
        )
      return
    }
    if (surface.isNotBlank()) {
      val slice = sentenceText.substring(start, end)
      if (slice != surface) {
        errors +=
          ImportValidationError(
            code = ImportValidationErrorCode.INVALID_WORD_OFFSET,
            fieldPath = "$fieldPrefix.surface",
            messageFa = "واژه '$surface' با زیررشته واقعی در بازه [$start, $end) یعنی '$slice' هم‌خوانی ندارد.",
            messageEn = "Exact UTF-16 slice '$slice' at [$start, $end) does not match surface '$surface'.",
          )
      }
    }
  }

  private fun missingField(fieldPath: String, messageFa: String): ImportValidationError =
    ImportValidationError(
      code = ImportValidationErrorCode.MISSING_REQUIRED_FIELD,
      fieldPath = fieldPath,
      messageFa = messageFa,
      messageEn = "Missing or invalid required field: $fieldPath",
    )

  private fun duplicateId(fieldPath: String, messageFa: String): ImportValidationError =
    ImportValidationError(
      code = ImportValidationErrorCode.DUPLICATE_INTERNAL_ID,
      fieldPath = fieldPath,
      messageFa = messageFa,
      messageEn = "Duplicate identifier at $fieldPath",
    )

  companion object {
    const val SUPPORTED_SCHEMA_VERSION = 1
  }
}
