package com.example.domain.model

enum class ImportValidationErrorCode {
  INVALID_JSON_SYNTAX,
  UNSUPPORTED_SCHEMA_VERSION,
  MISSING_REQUIRED_FIELD,
  INVALID_CONTENT_TYPE,
  INVALID_CEFR_LEVEL,
  INVALID_CONTENT_VERSION,
  DUPLICATE_INTERNAL_ID,
  INVALID_REFERENTIAL_INTEGRITY,
  INVALID_WORD_OFFSET,
  INVALID_QUESTION_OPTIONS,
  PACKAGE_VERSION_CONFLICT,
  DATABASE_FAILURE,
}

data class ImportValidationError(
  val code: ImportValidationErrorCode,
  val fieldPath: String,
  val messageFa: String,
  val messageEn: String,
)

sealed interface PackageImportResult {
  data class Success(
    val packageId: String,
    val title: String,
    val contentVersion: Int,
    val wasUpgrade: Boolean,
    val sentenceCount: Int,
    val glossaryCount: Int,
    val questionCount: Int,
  ) : PackageImportResult

  data class Failure(
    val errors: List<ImportValidationError>,
  ) : PackageImportResult
}
