package com.example.core.ai

enum class AiProviderType(val wireValue: String, val persianLabel: String) {
  NONE("none", "غیرفعال (حالت تمام‌آفلاین)"),
  CUSTOM_ENDPOINT("custom_endpoint", "سرویس‌دهنده خارجی قابل پیکربندی (آینده)");

  companion object {
    fun fromWireValue(value: String?): AiProviderType =
      entries.firstOrNull { it.wireValue == value } ?: NONE
  }
}

data class AiConfiguration(
  val enabled: Boolean = false,
  val providerType: AiProviderType = AiProviderType.NONE,
  val endpointUrl: String = "",
)

sealed interface AiAvailability {
  data object DisabledByUser : AiAvailability

  data object NotConfigured : AiAvailability

  data class Ready(val providerName: String) : AiAvailability

  data class OfflineUnavailable(val reasonFa: String) : AiAvailability
}

sealed interface AiResult {
  data class Success(val explanation: String) : AiResult

  data class Unavailable(val reasonFa: String) : AiResult
}

interface AiProvider {
  val id: String
  val displayName: String

  fun checkAvailability(configuration: AiConfiguration): AiAvailability

  suspend fun requestExplanation(
    prompt: String,
    configuration: AiConfiguration,
  ): AiResult
}

/**
 * Default strictly offline provider: never performs automatic network calls,
 * never embeds API secrets, and never blocks core reading or SRS functionality.
 */
class DisabledOfflineAiProvider : AiProvider {
  override val id: String = "offline_none"
  override val displayName: String = "بام‌لینگویج آفلاین"

  override fun checkAvailability(configuration: AiConfiguration): AiAvailability =
    when {
      !configuration.enabled || configuration.providerType == AiProviderType.NONE ->
        AiAvailability.DisabledByUser
      configuration.endpointUrl.isBlank() ->
        AiAvailability.NotConfigured
      else ->
        AiAvailability.OfflineUnavailable(
          "اتصال شبکه در نسخه آفلاین بام‌لینگویج غیرفعال است؛ یادگیری متنی، واژه‌نامه و مرور SRS بدون هوش مصنوعی فعال هستند."
        )
    }

  override suspend fun requestExplanation(
    prompt: String,
    configuration: AiConfiguration,
  ): AiResult =
    AiResult.Unavailable(
      "سرویس هوش مصنوعی اختیاری غیرفعال یا پیکربندی‌نشده است. همه قابلیت‌های اصلی برنامه به صورت آفلاین کار می‌کنند."
    )
}

/**
 * Isolated coordinator for optional, explicitly user-initiated AI requests.
 * All exceptions are trapped so AI status never interrupts Reader or SRS flows.
 */
class AiService(
  private val provider: AiProvider = DisabledOfflineAiProvider(),
) {
  fun checkAvailability(configuration: AiConfiguration): AiAvailability =
    runCatching { provider.checkAvailability(configuration) }
      .getOrElse {
        AiAvailability.OfflineUnavailable("بررسی وضعیت سرویس اختیاری هوش مصنوعی با خطا مواجه شد.")
      }

  suspend fun requestUserInitiatedExplanation(
    prompt: String,
    configuration: AiConfiguration,
  ): AiResult {
    if (!configuration.enabled || configuration.providerType == AiProviderType.NONE) {
      return AiResult.Unavailable("هوش مصنوعی اختیاری در تنظیمات خاموش است.")
    }
    if (prompt.isBlank()) {
      return AiResult.Unavailable("متن ورودی برای توضیح خالی است.")
    }
    return runCatching {
      when (val availability = checkAvailability(configuration)) {
        is AiAvailability.Ready -> provider.requestExplanation(prompt, configuration)
        AiAvailability.DisabledByUser ->
          AiResult.Unavailable("هوش مصنوعی اختیاری غیرفعال است.")
        AiAvailability.NotConfigured ->
          AiResult.Unavailable("نشانی سرویس‌دهنده اختیاری پیکربندی نشده است.")
        is AiAvailability.OfflineUnavailable ->
          AiResult.Unavailable(availability.reasonFa)
      }
    }.getOrElse {
      AiResult.Unavailable("خطا در سرویس اختیاری هوش مصنوعی؛ تجربه یادگیری آفلاین بدون وقفه ادامه دارد.")
    }
  }
}
