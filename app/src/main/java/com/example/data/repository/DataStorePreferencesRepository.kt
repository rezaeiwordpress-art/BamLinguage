package com.example.data.repository

import com.example.core.ai.AiProviderType
import com.example.core.common.ThemeMode
import com.example.core.common.TtsAccent
import com.example.core.datastore.UserPreferences
import com.example.core.datastore.UserPreferencesDataStore
import com.example.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow

class DataStorePreferencesRepository(
  private val dataStore: UserPreferencesDataStore,
) : PreferencesRepository {
  override val userPreferences: Flow<UserPreferences> = dataStore.preferencesFlow

  override suspend fun setThemeMode(mode: ThemeMode) = dataStore.setThemeMode(mode)

  override suspend fun setTtsSpeed(speed: Float) = dataStore.setTtsSpeed(speed)

  override suspend fun setTtsAccent(accent: TtsAccent) = dataStore.setTtsAccent(accent)

  override suspend fun setPreferredVoiceName(voiceName: String?) =
    dataStore.setPreferredVoiceName(voiceName)

  override suspend fun setDailyGoalCards(goal: Int) = dataStore.setDailyGoalCards(goal)

  override suspend fun setNewCardsPerDay(count: Int) = dataStore.setNewCardsPerDay(count)

  override suspend fun setShowTranslationsInReader(show: Boolean) =
    dataStore.setShowTranslationsInReader(show)

  override suspend fun setShowIpaInReader(show: Boolean) = dataStore.setShowIpaInReader(show)

  override suspend fun setShowGrammarNotesInReader(show: Boolean) =
    dataStore.setShowGrammarNotesInReader(show)

  override suspend fun setAutoPlaySrsAudio(autoPlay: Boolean) =
    dataStore.setAutoPlaySrsAudio(autoPlay)

  override suspend fun setAiConfiguration(
    enabled: Boolean,
    providerType: AiProviderType,
    endpointUrl: String,
  ) = dataStore.setAiConfiguration(enabled, providerType, endpointUrl)

  override suspend fun markSampleSeedAttempted(attempted: Boolean) =
    dataStore.markSampleSeedAttempted(attempted)

  override suspend fun setSelectedPackageId(packageId: String?) =
    dataStore.setSelectedPackageId(packageId)
}
