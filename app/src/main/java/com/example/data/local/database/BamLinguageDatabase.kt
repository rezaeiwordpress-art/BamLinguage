package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.ContentDao
import com.example.data.local.dao.LearningDao
import com.example.data.local.entity.ContentPackageEntity
import com.example.data.local.entity.GlossaryEntryEntity
import com.example.data.local.entity.GlossarySenseEntity
import com.example.data.local.entity.LearningProgressEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.local.entity.ReaderBookmarkEntity
import com.example.data.local.entity.SentenceEntity
import com.example.data.local.entity.SpeakerEntity
import com.example.data.local.entity.SrsCardEntity
import com.example.data.local.entity.SrsReviewLogEntity
import com.example.data.local.entity.UserVocabularyEntity
import com.example.data.local.entity.WordOccurrenceEntity

@Database(
  entities = [
    ContentPackageEntity::class,
    SpeakerEntity::class,
    SentenceEntity::class,
    GlossaryEntryEntity::class,
    GlossarySenseEntity::class,
    WordOccurrenceEntity::class,
    QuestionEntity::class,
    SrsCardEntity::class,
    SrsReviewLogEntity::class,
    LearningProgressEntity::class,
    UserVocabularyEntity::class,
    ReaderBookmarkEntity::class,
  ],
  version = 2,
  exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class BamLinguageDatabase : RoomDatabase() {
  abstract fun contentDao(): ContentDao

  abstract fun learningDao(): LearningDao

  companion object {
    const val DATABASE_NAME = "bamlinguage.db"

    val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE content_packages ADD COLUMN createdAt TEXT NOT NULL DEFAULT ''")
          db.execSQL("ALTER TABLE content_packages ADD COLUMN updatedAt TEXT NOT NULL DEFAULT ''")
          db.execSQL("ALTER TABLE content_packages ADD COLUMN wordCount INTEGER NOT NULL DEFAULT 0")
          db.execSQL("ALTER TABLE content_packages ADD COLUMN sentenceCountMeta INTEGER NOT NULL DEFAULT 0")
          db.execSQL("ALTER TABLE content_packages ADD COLUMN glossaryCountMeta INTEGER NOT NULL DEFAULT 0")

          db.execSQL("ALTER TABLE glossary_entries ADD COLUMN lemma TEXT NOT NULL DEFAULT ''")
          db.execSQL("ALTER TABLE glossary_entries ADD COLUMN forms TEXT NOT NULL DEFAULT '[]'")
          db.execSQL("ALTER TABLE glossary_entries ADD COLUMN cefr TEXT NOT NULL DEFAULT 'A1'")
          db.execSQL("ALTER TABLE glossary_entries ADD COLUMN pronunciationUk TEXT")
          db.execSQL("ALTER TABLE glossary_entries ADD COLUMN pronunciationUs TEXT")
          db.execSQL("ALTER TABLE glossary_entries ADD COLUMN synonyms TEXT NOT NULL DEFAULT '[]'")
          db.execSQL("ALTER TABLE glossary_entries ADD COLUMN antonyms TEXT NOT NULL DEFAULT '[]'")
          db.execSQL("ALTER TABLE glossary_entries ADD COLUMN sentenceIds TEXT NOT NULL DEFAULT '[]'")
          db.execSQL("UPDATE glossary_entries SET lemma = normalizedTerm WHERE lemma = ''")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_glossary_entries_lemma ON glossary_entries (lemma)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS glossary_senses (
              id TEXT NOT NULL,
              glossaryEntryId TEXT NOT NULL,
              packageId TEXT NOT NULL,
              sourceSenseId TEXT NOT NULL,
              orderIndex INTEGER NOT NULL,
              meaningFa TEXT NOT NULL,
              definitionEn TEXT NOT NULL,
              examplesJson TEXT NOT NULL,
              PRIMARY KEY(id),
              FOREIGN KEY(glossaryEntryId) REFERENCES glossary_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE,
              FOREIGN KEY(packageId) REFERENCES content_packages(packageId) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_glossary_senses_glossaryEntryId ON glossary_senses (glossaryEntryId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_glossary_senses_packageId ON glossary_senses (packageId)")

          db.execSQL("ALTER TABLE srs_cards ADD COLUMN cardType TEXT NOT NULL DEFAULT 'WORD'")
          db.execSQL("ALTER TABLE srs_cards ADD COLUMN contentId TEXT NOT NULL DEFAULT ''")
          db.execSQL("ALTER TABLE srs_cards ADD COLUMN englishDefinition TEXT")
          db.execSQL("ALTER TABLE srs_cards ADD COLUMN normalizedLemma TEXT NOT NULL DEFAULT ''")
          db.execSQL("ALTER TABLE srs_cards ADD COLUMN pos TEXT NOT NULL DEFAULT ''")
          db.execSQL("ALTER TABLE srs_cards ADD COLUMN senseId TEXT NOT NULL DEFAULT 'sense-1'")
          db.execSQL("ALTER TABLE srs_cards ADD COLUMN contextSentenceEn TEXT")
          db.execSQL("ALTER TABLE srs_cards ADD COLUMN contextSentenceFa TEXT")
          db.execSQL("UPDATE srs_cards SET contentId = cardId, normalizedLemma = lower(frontTerm) WHERE contentId = ''")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_srs_cards_contentId ON srs_cards (contentId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_srs_cards_cardType ON srs_cards (cardType)")
        }
      }

    fun create(context: Context): BamLinguageDatabase =
      Room.databaseBuilder(
        context.applicationContext,
        BamLinguageDatabase::class.java,
        DATABASE_NAME,
      )
        .addMigrations(MIGRATION_1_2)
        .build()

    fun createInMemory(context: Context): BamLinguageDatabase =
      Room.inMemoryDatabaseBuilder(
        context.applicationContext,
        BamLinguageDatabase::class.java,
      )
        .addMigrations(MIGRATION_1_2)
        .allowMainThreadQueries()
        .build()
  }
}
