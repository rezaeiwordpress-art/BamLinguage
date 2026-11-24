package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.ContentPackageEntity
import com.example.data.local.entity.GlossaryEntryEntity
import com.example.data.local.entity.GlossarySenseEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.local.entity.SentenceEntity
import com.example.data.local.entity.SpeakerEntity
import com.example.data.local.entity.WordOccurrenceEntity
import kotlinx.coroutines.flow.Flow

data class ContentPackageSummaryRow(
  @Embedded val contentPackage: ContentPackageEntity,
  val sentenceCount: Int,
  val glossaryCount: Int,
  val questionCount: Int,
  val speakerCount: Int,
)

@Dao
interface ContentDao {
  @Query(
    """
    SELECT cp.*,
      (SELECT COUNT(*) FROM sentences s WHERE s.packageId = cp.packageId) AS sentenceCount,
      (SELECT COUNT(*) FROM glossary_entries g WHERE g.packageId = cp.packageId) AS glossaryCount,
      (SELECT COUNT(*) FROM questions q WHERE q.packageId = cp.packageId) AS questionCount,
      (SELECT COUNT(*) FROM speakers sp WHERE sp.packageId = cp.packageId) AS speakerCount
    FROM content_packages cp
    ORDER BY cp.importedAtEpochMs DESC
    """
  )
  fun observePackageSummaries(): Flow<List<ContentPackageSummaryRow>>

  @Query("SELECT * FROM content_packages WHERE packageId = :packageId LIMIT 1")
  fun observePackageById(packageId: String): Flow<ContentPackageEntity?>

  @Query("SELECT * FROM speakers WHERE packageId = :packageId")
  fun observeSpeakersForPackage(packageId: String): Flow<List<SpeakerEntity>>

  @Query("SELECT * FROM sentences WHERE packageId = :packageId ORDER BY orderIndex ASC")
  fun observeSentencesForPackage(packageId: String): Flow<List<SentenceEntity>>

  @Query("SELECT * FROM glossary_entries WHERE packageId = :packageId ORDER BY normalizedTerm ASC")
  fun observeGlossaryForPackage(packageId: String): Flow<List<GlossaryEntryEntity>>

  @Query("SELECT * FROM glossary_senses WHERE packageId = :packageId ORDER BY orderIndex ASC")
  fun observeSensesForPackage(packageId: String): Flow<List<GlossarySenseEntity>>

  @Query("SELECT * FROM word_occurrences WHERE packageId = :packageId")
  fun observeWordOccurrencesForPackage(packageId: String): Flow<List<WordOccurrenceEntity>>

  @Query("SELECT * FROM questions WHERE packageId = :packageId ORDER BY orderIndex ASC")
  fun observeQuestionsForPackage(packageId: String): Flow<List<QuestionEntity>>

  @Query("SELECT * FROM content_packages WHERE packageId = :packageId LIMIT 1")
  suspend fun getPackageById(packageId: String): ContentPackageEntity?

  @Query("SELECT COUNT(*) FROM content_packages")
  suspend fun getPackageCount(): Int

  @Query("SELECT * FROM speakers WHERE packageId = :packageId")
  suspend fun getSpeakersForPackage(packageId: String): List<SpeakerEntity>

  @Query("SELECT * FROM sentences WHERE packageId = :packageId ORDER BY orderIndex ASC")
  suspend fun getSentencesForPackage(packageId: String): List<SentenceEntity>

  @Query("SELECT * FROM glossary_entries WHERE packageId = :packageId ORDER BY normalizedTerm ASC")
  suspend fun getGlossaryForPackage(packageId: String): List<GlossaryEntryEntity>

  @Query("SELECT * FROM glossary_senses WHERE packageId = :packageId ORDER BY orderIndex ASC")
  suspend fun getSensesForPackage(packageId: String): List<GlossarySenseEntity>

  @Query("SELECT * FROM glossary_entries ORDER BY normalizedTerm ASC")
  suspend fun getAllGlossaryEntries(): List<GlossaryEntryEntity>

  @Query("SELECT * FROM glossary_senses ORDER BY orderIndex ASC")
  suspend fun getAllGlossarySenses(): List<GlossarySenseEntity>

  @Query("SELECT * FROM word_occurrences WHERE packageId = :packageId")
  suspend fun getWordOccurrencesForPackage(packageId: String): List<WordOccurrenceEntity>

  @Query("SELECT * FROM questions WHERE packageId = :packageId ORDER BY orderIndex ASC")
  suspend fun getQuestionsForPackage(packageId: String): List<QuestionEntity>

  @Query(
    """
    SELECT * FROM glossary_entries
    WHERE normalizedTerm LIKE '%' || :query || '%'
       OR term LIKE '%' || :query || '%'
       OR lemma LIKE '%' || :query || '%'
       OR forms LIKE '%' || :query || '%'
       OR persianMeaning LIKE '%' || :query || '%'
       OR id IN (SELECT glossaryEntryId FROM glossary_senses WHERE meaningFa LIKE '%' || :query || '%')
    ORDER BY normalizedTerm ASC
    LIMIT 60
    """
  )
  suspend fun searchGlossary(query: String): List<GlossaryEntryEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPackage(entity: ContentPackageEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSpeakers(entities: List<SpeakerEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSentences(entities: List<SentenceEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGlossaryEntries(entities: List<GlossaryEntryEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGlossarySenses(entities: List<GlossarySenseEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertWordOccurrences(entities: List<WordOccurrenceEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertQuestions(entities: List<QuestionEntity>)

  @Query("DELETE FROM content_packages WHERE packageId = :packageId")
  suspend fun deletePackageById(packageId: String)

  @Transaction
  suspend fun replacePackageTransactionally(
    contentPackage: ContentPackageEntity,
    speakers: List<SpeakerEntity>,
    sentences: List<SentenceEntity>,
    glossaryEntries: List<GlossaryEntryEntity>,
    glossarySenses: List<GlossarySenseEntity>,
    wordOccurrences: List<WordOccurrenceEntity>,
    questions: List<QuestionEntity>,
  ) {
    deletePackageById(contentPackage.packageId)
    insertPackage(contentPackage)
    if (speakers.isNotEmpty()) insertSpeakers(speakers)
    if (sentences.isNotEmpty()) insertSentences(sentences)
    if (glossaryEntries.isNotEmpty()) insertGlossaryEntries(glossaryEntries)
    if (glossarySenses.isNotEmpty()) insertGlossarySenses(glossarySenses)
    if (wordOccurrences.isNotEmpty()) insertWordOccurrences(wordOccurrences)
    if (questions.isNotEmpty()) insertQuestions(questions)
  }
}
