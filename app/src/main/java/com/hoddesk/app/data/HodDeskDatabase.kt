package com.hoddesk.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log
import com.hoddesk.app.privacy.DatabasePassphraseProvider
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(entities = [SourceEntity::class, TaskEntity::class, EventEntity::class, EvidenceEntity::class, SemanticEmbeddingEntity::class, SourceChunkEntity::class, SourceRelationEntity::class, ActionPointEntity::class, ReasoningRunEntity::class, AuditEntryEntity::class, LearningCorrectionEntity::class, CommitmentEntity::class], version = 15, exportSchema = false)
abstract class HodDeskDatabase : RoomDatabase() {
    abstract fun dao(): HodDeskDao

    companion object {
        @Volatile private var instance: HodDeskDatabase? = null
        fun get(context: Context): HodDeskDatabase = instance ?: synchronized(this) {
            SQLiteDatabase.loadLibs(context.applicationContext)
            instance ?: Room.databaseBuilder(context.applicationContext, HodDeskDatabase::class.java, "hod-desk.db")
                .openHelperFactory(SupportFactory(DatabasePassphraseProvider(context).get()))
                .addMigrations(MIGRATION_1_3, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15).build().also { Log.i("HOD_DB", "encrypted_database_opened"); instance = it }
        }

        /** Preserves existing sources/tasks while adding workflow and import processing fields. */
        private val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sources ADD COLUMN processingStatus TEXT NOT NULL DEFAULT 'ready'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN assignee TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceRule TEXT")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sources ADD COLUMN processingStatus TEXT NOT NULL DEFAULT 'ready'")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS semantic_embeddings (sourceId INTEGER NOT NULL PRIMARY KEY, modelVersion TEXT NOT NULL, dimension INTEGER NOT NULL, vector BLOB NOT NULL, indexedAt INTEGER NOT NULL)")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS source_chunks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceId INTEGER NOT NULL, ordinal INTEGER NOT NULL, modality TEXT NOT NULL, text TEXT NOT NULL, citation TEXT NOT NULL, page INTEGER, startMs INTEGER, endMs INTEGER, modelVersion TEXT NOT NULL, dimension INTEGER NOT NULL, vector BLOB NOT NULL, indexedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_source_chunks_sourceId ON source_chunks(sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_source_chunks_modality ON source_chunks(modality)")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS source_relations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceId INTEGER NOT NULL, relatedSourceId INTEGER NOT NULL, relationType TEXT NOT NULL, score REAL NOT NULL, rationale TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_source_relations_sourceId ON source_relations(sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_source_relations_relatedSourceId ON source_relations(relatedSourceId)")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS action_points (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceId INTEGER NOT NULL, action TEXT NOT NULL, category TEXT NOT NULL, dateText TEXT, timeText TEXT, locationText TEXT, peopleText TEXT, evidence TEXT NOT NULL, confidence REAL NOT NULL, status TEXT NOT NULL, ambiguity TEXT, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_action_points_sourceId ON action_points(sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_action_points_status ON action_points(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_action_points_confidence ON action_points(confidence)")
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS reasoning_runs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceId INTEGER NOT NULL, modelVersion TEXT NOT NULL, action TEXT NOT NULL, category TEXT NOT NULL, dateText TEXT, timeText TEXT, locationText TEXT, peopleJson TEXT NOT NULL, ambiguitiesJson TEXT NOT NULL, citationsJson TEXT NOT NULL, confidence REAL NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reasoning_runs_sourceId ON reasoning_runs(sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reasoning_runs_createdAt ON reasoning_runs(createdAt)")
            }
        }
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS audit_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, entityType TEXT NOT NULL, entityId INTEGER NOT NULL, action TEXT NOT NULL, detail TEXT, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_entries_entityType ON audit_entries(entityType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_entries_entityId ON audit_entries(entityId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_entries_createdAt ON audit_entries(createdAt)")
            }
        }
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sources ADD COLUMN triageBucket TEXT NOT NULL DEFAULT 'FYI'")
                db.execSQL("ALTER TABLE sources ADD COLUMN priorityScore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sources ADD COLUMN priorityReasons TEXT")
            }
        }
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS learning_corrections (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, actionPointId INTEGER NOT NULL, field TEXT NOT NULL, before TEXT, after TEXT, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_learning_corrections_actionPointId ON learning_corrections(actionPointId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_learning_corrections_createdAt ON learning_corrections(createdAt)")
            }
        }
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS commitments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceId INTEGER, taskId INTEGER, direction TEXT NOT NULL, owner TEXT, counterparty TEXT, summary TEXT NOT NULL, dueAt INTEGER, status TEXT NOT NULL, evidence TEXT NOT NULL, confidence REAL NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_commitments_sourceId ON commitments(sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_commitments_taskId ON commitments(taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_commitments_direction ON commitments(direction)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_commitments_status ON commitments(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_commitments_dueAt ON commitments(dueAt)")
            }
        }
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE action_points ADD COLUMN ownerText TEXT")
                db.execSQL("ALTER TABLE action_points ADD COLUMN referenceNumber TEXT")
                db.execSQL("ALTER TABLE action_points ADD COLUMN recurrenceRule TEXT")
            }
        }
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE source_chunks ADD COLUMN paragraphIndex INTEGER")
                db.execSQL("ALTER TABLE source_chunks ADD COLUMN messageIndex INTEGER")
            }
        }
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE semantic_embeddings ADD COLUMN modality TEXT NOT NULL DEFAULT 'text'")
                db.execSQL("ALTER TABLE semantic_embeddings ADD COLUMN evidenceLocator TEXT")
            }
        }
    }
}
