package com.suhwak.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Deck::class, Word::class], version = 9, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN pos TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN box INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE words ADD COLUMN dueAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN intervalMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE words ADD COLUMN excluded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE words ADD COLUMN flagged INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE decks ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE decks ADD COLUMN emoji TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE decks ADD COLUMN lastStudiedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE decks ADD COLUMN isCollection INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE decks ADD COLUMN collectionId INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN example TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN bookmarked INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 수확중 씨앗(수학 문제) 칸들. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (col in listOf("qtype", "choices", "hint", "steps", "level", "sourceId")) {
                    db.execSQL("ALTER TABLE words ADD COLUMN $col TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        /** Closes the database so a backup can replace its file. */
        fun close() = synchronized(this) {
            instance?.close()
            instance = null
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "suhwak.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build().also { instance = it }
            }
    }
}
