package com.victorpolitakis.pesto.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 1 → 2 (phase 3): search, Dictionary terms and a profile per app, without
 * losing a dictation. By hand: an AutoMigration would leave the old rows out
 * of the index, and SQLite cannot remove Greek accents, so `search_text` is
 * filled here in Kotlin, row by row, before the index is built.
 *
 * The SQL of the new tables is what Room 2.8.5 writes for the entities: the
 * same text as in app/schemas/…/2.json, and MigrationTest checks it.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `dictations` ADD COLUMN `search_text` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `dictations` ADD COLUMN `raw_words` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `dictations` ADD COLUMN `started_at` INTEGER NOT NULL DEFAULT 0")
        // v1 did not record the tap: the end is the closest there is.
        db.execSQL("UPDATE `dictations` SET `started_at` = `created_at`")
        db.query("SELECT `id`, `raw_text`, `text` FROM `dictations`").use { rows ->
            while (rows.moveToNext()) {
                val rawText = rows.getString(1)
                db.execSQL(
                    "UPDATE `dictations` SET `search_text` = ?, `raw_words` = ? WHERE `id` = ?",
                    arrayOf<Any>(SearchText.of(rawText, rows.getString(2)), SearchText.words(rawText), rows.getLong(0)),
                )
            }
        }

        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `dictations_fts` USING FTS4(`search_text` TEXT NOT NULL, tokenize=unicode61, content=`dictations`)",
        )
        FTS_TRIGGERS.forEach { db.execSQL(it) }
        // The old rows into the index, now that search_text is filled.
        db.execSQL("INSERT INTO `dictations_fts`(`dictations_fts`) VALUES('rebuild')")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `terms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `term` TEXT NOT NULL, `term_key` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_terms_term_key` ON `terms` (`term_key`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_profiles` (`package` TEXT NOT NULL, `profile_id` TEXT NOT NULL, PRIMARY KEY(`package`))",
        )
    }
}

/** The content sync triggers Room creates for DictationFts on a new install (its `contentSyncTriggers`). */
private val FTS_TRIGGERS = listOf(
    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictations_fts_BEFORE_UPDATE BEFORE UPDATE ON `dictations` BEGIN DELETE FROM `dictations_fts` WHERE `docid`=OLD.`rowid`; END",
    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictations_fts_BEFORE_DELETE BEFORE DELETE ON `dictations` BEGIN DELETE FROM `dictations_fts` WHERE `docid`=OLD.`rowid`; END",
    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictations_fts_AFTER_UPDATE AFTER UPDATE ON `dictations` BEGIN INSERT INTO `dictations_fts`(`docid`, `search_text`) VALUES (NEW.`rowid`, NEW.`search_text`); END",
    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictations_fts_AFTER_INSERT AFTER INSERT ON `dictations` BEGIN INSERT INTO `dictations_fts`(`docid`, `search_text`) VALUES (NEW.`rowid`, NEW.`search_text`); END",
)
