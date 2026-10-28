package com.victorpolitakis.pesto.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Spike P1 and step 2: the dictations of v1 survive the migration, and the search finds them. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PestoDatabase::class.java)

    private val raw1 = "καλημέρα γιώργο το meeting πάει για την πέμπτη"
    private val text1 = "Καλημέρα Γιώργο, το meeting πάει για την Πέμπτη."

    private fun SupportSQLiteDatabase.insertV1(id: Long, rawText: String, text: String, profile: String, createdAt: Long, audioS: Double?) {
        execSQL(
            "INSERT INTO dictations ($V1_COLUMNS) " +
                "VALUES (?, ?, 'com.whatsapp', 'text', 'Μήνυμα', 0, ?, ?, ?, NULL, 'user', ?, 4, 150, NULL, 160, 'COMMIT_TEXT')",
            arrayOf<Any?>(id, createdAt, rawText, text, profile, audioS),
        )
    }

    /** A v1 file with three Greek dictations, like the POCO's after phase 2. Returns its rows. */
    private fun v1(): List<List<String?>> {
        val db = helper.createDatabase(DB, 1)
        db.insertV1(1, raw1, text1, "raw", 1_000, 4.5)
        db.insertV1(2, "στείλε μου την προϋπόθεση", "Στείλε μου την προϋπόθεση.", "mail", 2_000, 2.0)
        db.insertV1(3, "ΟΔΟΣ ΣΤΑΔΙΟΥ", "Οδός Σταδίου", "raw", 3_000, null)
        val rows = db.rows("SELECT $V1_COLUMNS FROM dictations ORDER BY id")
        db.close()
        return rows
    }

    private fun migrated(): SupportSQLiteDatabase = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

    private fun SupportSQLiteDatabase.rows(sql: String): List<List<String?>> = query(sql).use { c ->
        buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
    }

    private fun SupportSQLiteDatabase.search(match: String): List<Long> = query(
        "SELECT id FROM dictations WHERE id IN (SELECT rowid FROM dictations_fts WHERE dictations_fts MATCH ?) ORDER BY id",
        arrayOf<Any?>(match),
    ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }

    @Test
    fun `1 to 2 keeps every dictation as it was`() {
        val before = v1()
        assertEquals(before, migrated().rows("SELECT $V1_COLUMNS FROM dictations ORDER BY id"))
    }

    @Test
    fun `the new columns are filled for the old rows`() {
        v1()
        assertEquals(
            listOf(
                listOf("1", SearchText.of(raw1, text1), "8", "1000"),
                listOf("2", "στειλε μου την προυποθεση στειλε μου την προυποθεση.", "4", "2000"),
                listOf("3", "οδοσ σταδιου οδοσ σταδιου", "2", "3000"),
            ),
            migrated().rows("SELECT id, search_text, raw_words, started_at FROM dictations ORDER BY id"),
        )
    }

    @Test
    fun `the search finds the old rows without accents`() {
        v1()
        val db = migrated()
        assertEquals(listOf(1L), db.search(SearchText.ftsQuery("καλημερα")!!))
        assertEquals(listOf(2L), db.search(SearchText.ftsQuery("ΠΡΟΫΠΟΘΕΣΗ")!!))
        assertEquals(listOf(3L), db.search(SearchText.ftsQuery("οδος σταδιου")!!))
        assertEquals(listOf(1L), db.search(SearchText.ftsQuery("Meeting")!!))
        assertEquals(listOf(1L), db.search(SearchText.ftsQuery("καλημ πεμπ")!!))
        assertEquals(emptyList<Long>(), db.search(SearchText.ftsQuery("αύριο")!!))
    }

    @Test
    fun `after the migration the triggers keep the index in sync`() {
        v1()
        val db = migrated()
        db.execSQL(
            "INSERT INTO dictations ($V1_COLUMNS, search_text, raw_words, started_at) " +
                "VALUES (4, 4000, NULL, NULL, NULL, NULL, 'νέα', 'Νέα.', 'raw', NULL, 'user', 1.0, 1, 1, NULL, 1, 'COMMIT_TEXT', ?, 1, 3900)",
            arrayOf<Any?>(SearchText.of("νέα", "Νέα.")),
        )
        assertEquals(listOf(4L), db.search("νεα*"))

        db.execSQL("UPDATE dictations SET search_text = 'αλλο κειμενο' WHERE id = 2")
        assertEquals(emptyList<Long>(), db.search("προυποθεσ*"))
        assertEquals(listOf(2L), db.search("αλλο*"))

        db.execSQL("DELETE FROM dictations WHERE id = 1")
        assertEquals(emptyList<Long>(), db.search("καλημερα*"))
    }

    @Test
    fun `room opens a v1 file with the migration and accepts the schema`() {
        v1()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.databaseBuilder(context, PestoDatabase::class.java, DB).addMigrations(MIGRATION_1_2).build()
        try {
            assertEquals(3, runBlocking { room.dictations().count() })
        } finally {
            room.close()
        }
    }

    private companion object {
        const val DB = "migration-test.db"
        const val V1_COLUMNS = "id, created_at, package, input_type, hint, single_line, raw_text, text, profile, " +
            "fallback_reason, end_reason, audio_s, words, stt_ms, llm_ms, total_ms, insert_method"
    }
}
