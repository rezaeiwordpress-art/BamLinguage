package com.example.data.repository

import com.example.core.common.DispatcherProvider
import com.example.data.local.dao.ContentDao
import com.example.data.mapper.toDomain
import com.example.data.mapper.toEntity
import com.example.domain.model.ContentPackage
import com.example.domain.model.ContentPackageBundle
import com.example.domain.model.ContentPackageSummary
import com.example.domain.model.GlossaryEntry
import com.example.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomContentRepository(
  private val contentDao: ContentDao,
  private val dispatchers: DispatcherProvider,
) : ContentRepository {

  override fun observePackageSummaries(): Flow<List<ContentPackageSummary>> =
    contentDao.observePackageSummaries().map { rows -> rows.map { it.toDomain() } }

  override fun observePackageBundle(packageId: String): Flow<ContentPackageBundle?> {
    val headFlow =
      combine(
        contentDao.observePackageById(packageId),
        contentDao.observeSpeakersForPackage(packageId),
        contentDao.observeSentencesForPackage(packageId),
      ) { pkg, speakers, sentences ->
        Triple(pkg, speakers, sentences)
      }

    val glossaryFlow =
      combine(
        contentDao.observeGlossaryForPackage(packageId),
        contentDao.observeSensesForPackage(packageId),
        contentDao.observeWordOccurrencesForPackage(packageId),
        contentDao.observeQuestionsForPackage(packageId),
      ) { glossary, senses, occurrences, questions ->
        listOf(glossary, senses, occurrences, questions)
      }

    return combine(headFlow, glossaryFlow) { (pkg, speakers, sentences), rest ->
      if (pkg == null) return@combine null
      @Suppress("UNCHECKED_CAST")
      val glossaryEntities = rest[0] as List<com.example.data.local.entity.GlossaryEntryEntity>
      @Suppress("UNCHECKED_CAST")
      val senseEntities = rest[1] as List<com.example.data.local.entity.GlossarySenseEntity>
      @Suppress("UNCHECKED_CAST")
      val occurrenceEntities = rest[2] as List<com.example.data.local.entity.WordOccurrenceEntity>
      @Suppress("UNCHECKED_CAST")
      val questionEntities = rest[3] as List<com.example.data.local.entity.QuestionEntity>

      val sensesByEntry = senseEntities.groupBy { it.glossaryEntryId }
      ContentPackageBundle(
        contentPackage = pkg.toDomain(),
        speakers = speakers.map { it.toDomain() },
        sentences = sentences.map { it.toDomain() },
        glossaryEntries =
          glossaryEntities.map { entry ->
            entry.toDomain(sensesByEntry[entry.id].orEmpty().map { it.toDomain() })
          },
        wordOccurrences = occurrenceEntities.map { it.toDomain() },
        questions = questionEntities.map { it.toDomain() },
      )
    }
  }

  override suspend fun getPackageById(packageId: String): ContentPackage? =
    withContext(dispatchers.io) { contentDao.getPackageById(packageId)?.toDomain() }

  override suspend fun getPackageBundle(packageId: String): ContentPackageBundle? =
    withContext(dispatchers.io) {
      val pkg = contentDao.getPackageById(packageId)?.toDomain() ?: return@withContext null
      val sensesByEntry =
        contentDao.getSensesForPackage(packageId).groupBy { it.glossaryEntryId }
      ContentPackageBundle(
        contentPackage = pkg,
        speakers = contentDao.getSpeakersForPackage(packageId).map { it.toDomain() },
        sentences = contentDao.getSentencesForPackage(packageId).map { it.toDomain() },
        glossaryEntries =
          contentDao.getGlossaryForPackage(packageId).map { entry ->
            entry.toDomain(sensesByEntry[entry.id].orEmpty().map { it.toDomain() })
          },
        wordOccurrences = contentDao.getWordOccurrencesForPackage(packageId).map { it.toDomain() },
        questions = contentDao.getQuestionsForPackage(packageId).map { it.toDomain() },
      )
    }

  override suspend fun getAllGlossaryEntries(): List<GlossaryEntry> =
    withContext(dispatchers.io) {
      val sensesByEntry = contentDao.getAllGlossarySenses().groupBy { it.glossaryEntryId }
      contentDao.getAllGlossaryEntries().map { entry ->
        entry.toDomain(sensesByEntry[entry.id].orEmpty().map { it.toDomain() })
      }
    }

  override suspend fun replacePackageTransactionally(bundle: ContentPackageBundle) =
    withContext(dispatchers.io) {
      val pkgId = bundle.contentPackage.packageId
      val senseEntities =
        bundle.glossaryEntries.flatMap { entry ->
          entry.senses.map { sense -> sense.toEntity(packageId = pkgId) }
        }
      contentDao.replacePackageTransactionally(
        contentPackage = bundle.contentPackage.toEntity(),
        speakers = bundle.speakers.map { it.toEntity() },
        sentences = bundle.sentences.map { it.toEntity() },
        glossaryEntries = bundle.glossaryEntries.map { it.toEntity() },
        glossarySenses = senseEntities,
        wordOccurrences = bundle.wordOccurrences.map { it.toEntity() },
        questions = bundle.questions.map { it.toEntity() },
      )
    }

  override suspend fun deletePackage(packageId: String) =
    withContext(dispatchers.io) { contentDao.deletePackageById(packageId) }

  override suspend fun searchGlossary(query: String): List<GlossaryEntry> =
    withContext(dispatchers.io) {
      val entries = contentDao.searchGlossary(query.trim().lowercase())
      if (entries.isEmpty()) return@withContext emptyList()
      val sensesByEntry =
        entries
          .map { it.id }
          .chunked(500)
          .flatMap { chunk -> contentDao.getSensesForEntryIds(chunk) }
          .groupBy { it.glossaryEntryId }
      entries.map { entry ->
        entry.toDomain(sensesByEntry[entry.id].orEmpty().map { it.toDomain() })
      }
    }

  override suspend fun getPackageCount(): Int =
    withContext(dispatchers.io) { contentDao.getPackageCount() }
}
