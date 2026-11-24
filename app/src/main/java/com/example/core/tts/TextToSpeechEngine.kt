package com.example.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.core.common.TtsAccent
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TtsVoiceOption(
  val name: String,
  val displayLabel: String,
  val localeTag: String,
  val accent: TtsAccent,
  val isNetworkRequired: Boolean,
)

data class TtsSpeechItem(
  val itemId: String,
  val text: String,
  val speakerId: String? = null,
)

sealed interface TtsStatus {
  data object Initializing : TtsStatus

  data object Ready : TtsStatus

  data class Speaking(
    val text: String,
    val activeItemId: String? = null,
    val isParagraphMode: Boolean = false,
  ) : TtsStatus

  data class Paused(
    val activeItemId: String? = null,
    val remainingCount: Int = 0,
  ) : TtsStatus

  data class Unavailable(val reasonFa: String) : TtsStatus
}

interface TextToSpeechEngine {
  val status: StateFlow<TtsStatus>
  val availableVoices: StateFlow<List<TtsVoiceOption>>

  fun speakWord(
    word: String,
    speechRate: Float = 1.0f,
    accent: TtsAccent = TtsAccent.AUTO,
    preferredVoiceName: String? = null,
  )

  fun speakSentence(
    text: String,
    itemId: String? = null,
    speechRate: Float = 1.0f,
    accent: TtsAccent = TtsAccent.AUTO,
    preferredVoiceName: String? = null,
  )

  fun speakParagraph(
    items: List<TtsSpeechItem>,
    speechRate: Float = 1.0f,
    accent: TtsAccent = TtsAccent.AUTO,
    speakerVoiceMap: Map<String, String> = emptyMap(),
    defaultVoiceName: String? = null,
  )

  fun pause()

  fun resume()

  fun stop()

  fun refreshVoices()

  fun shutdown()
}

class AndroidTextToSpeechEngine(context: Context) : TextToSpeechEngine {
  private val appContext = context.applicationContext
  private val _status = MutableStateFlow<TtsStatus>(TtsStatus.Initializing)
  override val status: StateFlow<TtsStatus> = _status.asStateFlow()

  private val _availableVoices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
  override val availableVoices: StateFlow<List<TtsVoiceOption>> = _availableVoices.asStateFlow()

  private var textToSpeech: TextToSpeech? = null
  private var isReady = false
  private var rawVoices: List<Voice> = emptyList()

  private var paragraphQueue: MutableList<TtsSpeechItem> = mutableListOf()
  private var paragraphRate: Float = 1.0f
  private var paragraphAccent: TtsAccent = TtsAccent.AUTO
  private var paragraphSpeakerMap: Map<String, String> = emptyMap()
  private var paragraphDefaultVoice: String? = null
  private var currentParagraphItem: TtsSpeechItem? = null
  private var currentUtteranceId: String? = null

  init {
    try {
      textToSpeech =
        TextToSpeech(appContext) { initStatus ->
          if (initStatus == TextToSpeech.SUCCESS) {
            val tts = textToSpeech
            val usResult = tts?.setLanguage(Locale.US)
            val ukResult =
              if (usResult == TextToSpeech.LANG_MISSING_DATA ||
                usResult == TextToSpeech.LANG_NOT_SUPPORTED
              ) {
                tts.setLanguage(Locale.UK)
              } else {
                usResult
              }

            if (ukResult == TextToSpeech.LANG_MISSING_DATA ||
              ukResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
              _status.value =
                TtsStatus.Unavailable(
                  "داده‌های صوتی زبان انگلیسی روی دستگاه یافت نشد؛ مطالعه متنی بدون صوت در دسترس است."
                )
            } else {
              isReady = true
              loadAvailableEnglishVoices(tts)
              tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                  override fun onStart(utteranceId: String?) = Unit

                  override fun onDone(utteranceId: String?) {
                    if (!isReady) return
                    if (utteranceId == currentUtteranceId && paragraphQueue.isNotEmpty()) {
                      playNextInQueue()
                    } else if (utteranceId == currentUtteranceId) {
                      currentParagraphItem = null
                      _status.value = TtsStatus.Ready
                    }
                  }

                  @Deprecated("Deprecated in Java")
                  override fun onError(utteranceId: String?) {
                    handleUtteranceError()
                  }

                  override fun onError(utteranceId: String?, errorCode: Int) {
                    handleUtteranceError()
                  }
                }
              )
              _status.value = TtsStatus.Ready
            }
          } else {
            _status.value =
              TtsStatus.Unavailable(
                "موتور تلفظ اندروید (TTS) در این دستگاه در دسترس نیست؛ خواندن متن بدون صدا فعال است."
              )
          }
        }
    } catch (t: Throwable) {
      _status.value =
        TtsStatus.Unavailable(
          "راه‌اندازی موتور تلفظ امکان‌پذیر نشد: ${t.message ?: "خطای ناشناخته"}"
        )
    }
  }

  private fun handleUtteranceError() {
    if (!isReady) return
    if (paragraphQueue.isNotEmpty()) {
      playNextInQueue()
    } else {
      currentParagraphItem = null
      _status.value = TtsStatus.Ready
    }
  }

  private fun loadAvailableEnglishVoices(tts: TextToSpeech?) {
    val voices =
      runCatching {
        tts?.voices
          ?.filter { it.locale?.language.equals("en", ignoreCase = true) }
          ?.sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.locale.toLanguageTag() }, { it.name }))
          .orEmpty()
      }.getOrDefault(emptyList())

    rawVoices = voices
    _availableVoices.value =
      voices.mapIndexed { idx, voice ->
        val tag = voice.locale?.toLanguageTag().orEmpty()
        val accent =
          when {
            tag.contains("GB", ignoreCase = true) -> TtsAccent.UK
            tag.contains("US", ignoreCase = true) -> TtsAccent.US
            else -> TtsAccent.AUTO
          }
        val offlineLabel = if (voice.isNetworkConnectionRequired) "آنلاین" else "آفلاین"
        TtsVoiceOption(
          name = voice.name,
          displayLabel = "${accent.persianLabel} • صدا ${idx + 1} ($offlineLabel)",
          localeTag = tag.ifBlank { "en" },
          accent = accent,
          isNetworkRequired = voice.isNetworkConnectionRequired,
        )
      }
  }

  override fun speakWord(
    word: String,
    speechRate: Float,
    accent: TtsAccent,
    preferredVoiceName: String?,
  ) {
    speakSentence(
      text = word,
      itemId = null,
      speechRate = speechRate,
      accent = accent,
      preferredVoiceName = preferredVoiceName,
    )
  }

  override fun speakSentence(
    text: String,
    itemId: String?,
    speechRate: Float,
    accent: TtsAccent,
    preferredVoiceName: String?,
  ) {
    val sanitized = text.trim()
    if (sanitized.isEmpty() || !isReady) return

    paragraphQueue.clear()
    currentParagraphItem = null
    speakInternal(
      text = sanitized,
      itemId = itemId,
      isParagraphMode = false,
      speechRate = speechRate,
      accent = accent,
      preferredVoiceName = preferredVoiceName,
    )
  }

  override fun speakParagraph(
    items: List<TtsSpeechItem>,
    speechRate: Float,
    accent: TtsAccent,
    speakerVoiceMap: Map<String, String>,
    defaultVoiceName: String?,
  ) {
    val nonEmpty = items.filter { it.text.isNotBlank() }
    if (nonEmpty.isEmpty() || !isReady) return

    paragraphQueue = nonEmpty.toMutableList()
    paragraphRate = speechRate
    paragraphAccent = accent
    paragraphSpeakerMap = speakerVoiceMap
    paragraphDefaultVoice = defaultVoiceName
    playNextInQueue()
  }

  private fun playNextInQueue() {
    if (paragraphQueue.isEmpty()) {
      currentParagraphItem = null
      _status.value = TtsStatus.Ready
      return
    }
    val next = paragraphQueue.removeAt(0)
    currentParagraphItem = next
    val speakerVoice = next.speakerId?.let { paragraphSpeakerMap[it] } ?: paragraphDefaultVoice
    speakInternal(
      text = next.text.trim(),
      itemId = next.itemId,
      isParagraphMode = true,
      speechRate = paragraphRate,
      accent = paragraphAccent,
      preferredVoiceName = speakerVoice,
    )
  }

  override fun pause() {
    if (!isReady) return
    val current = currentParagraphItem
    textToSpeech?.stop()
    if (current != null) {
      paragraphQueue.add(0, current)
      currentParagraphItem = null
      _status.value =
        TtsStatus.Paused(
          activeItemId = current.itemId,
          remainingCount = paragraphQueue.size,
        )
    } else {
      _status.value = TtsStatus.Ready
    }
  }

  override fun resume() {
    if (!isReady) return
    if (paragraphQueue.isNotEmpty()) {
      playNextInQueue()
    } else {
      _status.value = TtsStatus.Ready
    }
  }

  override fun stop() {
    paragraphQueue.clear()
    currentParagraphItem = null
    currentUtteranceId = null
    textToSpeech?.stop()
    if (isReady) {
      _status.value = TtsStatus.Ready
    }
  }

  override fun refreshVoices() {
    if (isReady) {
      loadAvailableEnglishVoices(textToSpeech)
    }
  }

  override fun shutdown() {
    isReady = false
    paragraphQueue.clear()
    currentParagraphItem = null
    currentUtteranceId = null
    textToSpeech?.stop()
    textToSpeech?.shutdown()
    textToSpeech = null
  }

  private fun speakInternal(
    text: String,
    itemId: String?,
    isParagraphMode: Boolean,
    speechRate: Float,
    accent: TtsAccent,
    preferredVoiceName: String?,
  ) {
    val tts = textToSpeech ?: return
    applyVoiceOrAccent(tts, accent, preferredVoiceName)
    tts.setSpeechRate(speechRate.coerceIn(0.6f, 1.5f))
    val utteranceId = UUID.randomUUID().toString()
    currentUtteranceId = utteranceId
    _status.value =
      TtsStatus.Speaking(
        text = text,
        activeItemId = itemId,
        isParagraphMode = isParagraphMode,
      )
    val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    if (result == TextToSpeech.ERROR) {
      _status.value =
        TtsStatus.Unavailable("پخش صدا با خطا مواجه شد؛ متن همچنان قابل مطالعه است.")
    }
  }

  private fun applyVoiceOrAccent(
    tts: TextToSpeech,
    accent: TtsAccent,
    preferredVoiceName: String?,
  ) {
    val exactVoice =
      if (!preferredVoiceName.isNullOrBlank()) {
        rawVoices.firstOrNull { it.name == preferredVoiceName }
      } else {
        null
      }

    if (exactVoice != null) {
      runCatching { tts.voice = exactVoice }
      return
    }

    val targetLocale =
      when (accent) {
        TtsAccent.UK -> Locale.UK
        TtsAccent.US -> Locale.US
        TtsAccent.AUTO -> Locale.US
      }

    val accentVoice =
      rawVoices.firstOrNull {
        !it.isNetworkConnectionRequired &&
          it.locale?.country.equals(targetLocale.country, ignoreCase = true)
      } ?: rawVoices.firstOrNull { !it.isNetworkConnectionRequired }

    if (accentVoice != null) {
      runCatching { tts.voice = accentVoice }
    } else {
      val langStatus = runCatching { tts.setLanguage(targetLocale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
      if (langStatus == TextToSpeech.LANG_MISSING_DATA || langStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
        runCatching { tts.setLanguage(Locale.US) }
      }
    }
  }
}
