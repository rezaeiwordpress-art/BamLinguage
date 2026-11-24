package com.example.core.common

enum class CefrLevel(val code: String, val persianLabel: String) {
  A1("A1", "مبتدی (A1)"),
  A2("A2", "مقدماتی (A2)"),
  B1("B1", "متوسط (B1)"),
  B2("B2", "فرامتوسط (B2)"),
  C1("C1", "پیشرفته (C1)"),
  C2("C2", "تسلط کامل (C2)");

  companion object {
    fun fromCode(code: String): CefrLevel? =
      entries.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
  }
}

enum class ContentType(val wireValue: String, val persianLabel: String) {
  STORY("story", "داستان"),
  DIALOGUE("dialogue", "گفت‌وگو");

  companion object {
    fun fromWireValue(value: String): ContentType? =
      entries.firstOrNull { it.wireValue.equals(value.trim(), ignoreCase = true) }
  }
}

enum class ThemeMode(val wireValue: String) {
  SYSTEM("system"),
  LIGHT("light"),
  DARK("dark");

  companion object {
    fun fromWireValue(value: String?): ThemeMode =
      entries.firstOrNull { it.wireValue == value } ?: SYSTEM
  }
}

enum class TtsAccent(val wireValue: String, val persianLabel: String) {
  AUTO("auto", "پیش‌فرض انگلیسی"),
  US("en-US", "لهجه آمریکایی (US)"),
  UK("en-GB", "لهجه بریتانیایی (UK)");

  companion object {
    fun fromWireValue(value: String?): TtsAccent =
      entries.firstOrNull { it.wireValue == value } ?: AUTO
  }
}

sealed interface AppResult<out T> {
  data class Success<T>(val data: T) : AppResult<T>

  data class Error(val message: String, val cause: Throwable? = null) : AppResult<Nothing>
}
