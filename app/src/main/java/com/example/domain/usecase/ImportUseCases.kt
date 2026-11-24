package com.example.domain.usecase

import android.content.Context
import com.example.content.importer.JsonContentPackageImporter
import com.example.content.importer.PackageImportPreview
import com.example.domain.model.PackageImportResult
import com.example.domain.repository.ContentRepository
import com.example.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.first

class ImportContentPackageUseCase(
  private val importer: JsonContentPackageImporter,
) {
  suspend fun preview(
    rawJson: String,
    allowSameVersionOverwrite: Boolean = false,
  ): PackageImportPreview =
    importer.previewFromJson(
      rawJson = rawJson,
      allowSameVersionOverwrite = allowSameVersionOverwrite,
    )

  suspend operator fun invoke(
    rawJson: String,
    allowSameVersionOverwrite: Boolean = false,
  ): PackageImportResult =
    importer.importFromJson(
      rawJson = rawJson,
      isBundledSample = false,
      allowSameVersionOverwrite = allowSameVersionOverwrite,
    )
}

class EnsureBundledSamplePackageUseCase(
  private val appContext: Context,
  private val contentRepository: ContentRepository,
  private val preferencesRepository: PreferencesRepository,
  private val importer: JsonContentPackageImporter,
) {
  suspend operator fun invoke(forceReload: Boolean = false): PackageImportResult? {
    val prefs = preferencesRepository.userPreferences.first()
    val existingCount = contentRepository.getPackageCount()
    if (!forceReload && (existingCount >= SAMPLE_ASSET_PATHS.size || prefs.sampleSeedAttempted)) {
      return null
    }

    var lastSuccess: PackageImportResult? = null
    for (assetPath in SAMPLE_ASSET_PATHS) {
      val rawJson = appContext.assets.open(assetPath).bufferedReader().use { it.readText() }
      val result =
        importer.importFromJson(
          rawJson = rawJson,
          isBundledSample = true,
          allowSameVersionOverwrite = true,
        )
      if (result is PackageImportResult.Success) {
        lastSuccess = result
        preferencesRepository.setSelectedPackageId(result.packageId)
      } else if (result is PackageImportResult.Failure && lastSuccess == null) {
        lastSuccess = result
      }
    }
    preferencesRepository.markSampleSeedAttempted(true)
    return lastSuccess
  }

  companion object {
    const val SAMPLE_ASSET_PATH = "sample_packages/bamiyan_morning_dialogue.json"
    const val STORY_SAMPLE_ASSET_PATH = "sample_packages/a_late_train_story.json"
    val SAMPLE_ASSET_PATHS = listOf(SAMPLE_ASSET_PATH, STORY_SAMPLE_ASSET_PATH)
  }
}
