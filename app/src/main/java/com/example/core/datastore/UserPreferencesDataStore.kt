package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.core.ai.AiConfiguration
import com.example.core.ai.AiProviderType
import com.example.core.common.ThemeMode
import com.example.core.common.TtsAccent
import java.io.IOException
import java.util.WeakHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class UserPreferences(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val ttsSpeed: Float = 0.95f,
  val ttsAccent: TtsAccent = TtsAccent.AUTO,
  val preferredVoiceName: String? = null,
  val dailyGoalCards: Int = 15,
  val newCardsPerDay: Int = 10,
  val showTranslationsInReader: Boolean = true,
  val showIpaInReader: Boolean = true,
  val showGrammarNotesInReader: Boolean = true,
  val autoPlaySrsAudio: Boolean = false,
  val aiEnabled: Boolean = false,
  val aiProviderType: AiProviderType = AiProviderType.NONE,
  val aiEndpointUrl: String = "",
  val sampleSeedAttempted: Boolean = false,
  val selectedPackageId: String? = null,
) {
  val aiConfiguration: AiConfiguration
    get() =
      AiConfiguration(
        enabled = aiEnabled,
        providerType = aiProviderType,
        endpointUrl = aiEndpointUrl,
      )
}

class UserPreferencesDataStore(private val dataStore: DataStore<Preferences>) {
  constructor(context: Context) : this(getOrCreateDataStore(context.applicationContext))

  companion object {
    private val lock = Any()
    private val storesByAppContext = WeakHashMap<Context, DataStore<Preferences>>()

    private fun getOrCreateDataStore(appContext: Context): DataStore<Preferences> =
      synchronized(lock) {
        storesByAppContext.getOrPut(appContext) {
          PreferenceDataStoreFactory.create(
            produceFile = { appContext.preferencesDataStoreFile("bamlinguage_user_prefs") },
          )
        }
      }
  }

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val TTS_SPEED = floatPreferencesKey("tts_speed")
    val TTS_ACCENT = stringPreferencesKey("tts_accent")
    val PREFERRED_VOICE_NAME = stringPreferencesKey("preferred_voice_name")
    val DAILY_GOAL_CARDS = intPreferencesKey("daily_goal_cards")
    val NEW_CARDS_PER_DAY = intPreferencesKey("new_cards_per_day")
    val SHOW_TRANSLATIONS = booleanPreferencesKey("show_translations_in_reader")
    val SHOW_IPA_IN_READER = booleanPreferencesKey("show_ipa_in_reader")
    val SHOW_GRAMMAR_NOTES = booleanPreferencesKey("show_grammar_notes_in_reader")
    val AUTO_PLAY_SRS_AUDIO = booleanPreferencesKey("auto_play_srs_audio")
    val AI_ENABLED = booleanPreferencesKey("optional_ai_enabled")
    val AI_PROVIDER_TYPE = stringPreferencesKey("optional_ai_provider_type")
    val AI_ENDPOINT_URL = stringPreferencesKey("optional_ai_endpoint_url")
    val SAMPLE_SEED_ATTEMPTED = booleanPreferencesKey("sample_seed_attempted")
    val SELECTED_PACKAGE_ID = stringPreferencesKey("selected_package_id")
  }

  val preferencesFlow: Flow<UserPreferences> =
    dataStore.data
      .catch { exception ->
        if (exception is IOException) {
          emit(emptyPreferences())
        } else {
          throw exception
        }
      }
      .map { prefs ->
        UserPreferences(
          themeMode = ThemeMode.fromWireValue(prefs[Keys.THEME_MODE]),
          ttsSpeed = (prefs[Keys.TTS_SPEED] ?: 0.95f).coerceIn(0.6f, 1.5f),
          ttsAccent = TtsAccent.fromWireValue(prefs[Keys.TTS_ACCENT]),
          preferredVoiceName = prefs[Keys.PREFERRED_VOICE_NAME],
          dailyGoalCards = (prefs[Keys.DAILY_GOAL_CARDS] ?: 15).coerceIn(5, 100),
          newCardsPerDay = (prefs[Keys.NEW_CARDS_PER_DAY] ?: 10).coerceIn(1, 50),
          showTranslationsInReader = prefs[Keys.SHOW_TRANSLATIONS] ?: true,
          showIpaInReader = prefs[Keys.SHOW_IPA_IN_READER] ?: true,
          showGrammarNotesInReader = prefs[Keys.SHOW_GRAMMAR_NOTES] ?: true,
          autoPlaySrsAudio = prefs[Keys.AUTO_PLAY_SRS_AUDIO] ?: false,
          aiEnabled = prefs[Keys.AI_ENABLED] ?: false,
          aiProviderType = AiProviderType.fromWireValue(prefs[Keys.AI_PROVIDER_TYPE]),
          aiEndpointUrl = prefs[Keys.AI_ENDPOINT_URL] ?: "",
          sampleSeedAttempted = prefs[Keys.SAMPLE_SEED_ATTEMPTED] ?: false,
          selectedPackageId = prefs[Keys.SELECTED_PACKAGE_ID],
        )
      }

  suspend fun setThemeMode(themeMode: ThemeMode) {
    dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = themeMode.wireValue }
  }

  suspend fun setTtsSpeed(speed: Float) {
    dataStore.edit { prefs -> prefs[Keys.TTS_SPEED] = speed.coerceIn(0.6f, 1.5f) }
  }

  suspend fun setTtsAccent(accent: TtsAccent) {
    dataStore.edit { prefs -> prefs[Keys.TTS_ACCENT] = accent.wireValue }
  }

  suspend fun setPreferredVoiceName(voiceName: String?) {
    dataStore.edit { prefs ->
      if (voiceName.isNullOrBlank()) {
        prefs.remove(Keys.PREFERRED_VOICE_NAME)
      } else {
        prefs[Keys.PREFERRED_VOICE_NAME] = voiceName
      }
    }
  }

  suspend fun setDailyGoalCards(goal: Int) {
    dataStore.edit { prefs -> prefs[Keys.DAILY_GOAL_CARDS] = goal.coerceIn(5, 100) }
  }

  suspend fun setNewCardsPerDay(count: Int) {
    dataStore.edit { prefs -> prefs[Keys.NEW_CARDS_PER_DAY] = count.coerceIn(1, 50) }
  }

  suspend fun setShowTranslationsInReader(show: Boolean) {
    dataStore.edit { prefs -> prefs[Keys.SHOW_TRANSLATIONS] = show }
  }

  suspend fun setShowIpaInReader(show: Boolean) {
    dataStore.edit { prefs -> prefs[Keys.SHOW_IPA_IN_READER] = show }
  }

  suspend fun setShowGrammarNotesInReader(show: Boolean) {
    dataStore.edit { prefs -> prefs[Keys.SHOW_GRAMMAR_NOTES] = show }
  }

  suspend fun setAutoPlaySrsAudio(autoPlay: Boolean) {
    dataStore.edit { prefs -> prefs[Keys.AUTO_PLAY_SRS_AUDIO] = autoPlay }
  }

  suspend fun setAiConfiguration(
    enabled: Boolean,
    providerType: AiProviderType,
    endpointUrl: String,
  ) {
    dataStore.edit { prefs ->
      prefs[Keys.AI_ENABLED] = enabled
      prefs[Keys.AI_PROVIDER_TYPE] = providerType.wireValue
      prefs[Keys.AI_ENDPOINT_URL] = endpointUrl.trim()
    }
  }

  suspend fun markSampleSeedAttempted(attempted: Boolean = true) {
    dataStore.edit { prefs -> prefs[Keys.SAMPLE_SEED_ATTEMPTED] = attempted }
  }

  suspend fun setSelectedPackageId(packageId: String?) {
    dataStore.edit { prefs ->
      if (packageId == null) {
        prefs.remove(Keys.SELECTED_PACKAGE_ID)
      } else {
        prefs[Keys.SELECTED_PACKAGE_ID] = packageId
      }
    }
  }
}
