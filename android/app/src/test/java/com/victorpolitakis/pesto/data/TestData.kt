package com.victorpolitakis.pesto.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.victorpolitakis.pesto.protocol.FieldContext
import com.victorpolitakis.pesto.protocol.Final
import com.victorpolitakis.pesto.protocol.Stats
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Wednesday 28/10/2026, 12:00 UTC: the «now» of the data tests (the week starts on Monday 26/10). */
val NOW: Long = LocalDateTime.of(2026, 10, 28, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

const val DAY = 86_400_000L

/** An empty database in memory (Robolectric), with the real schema and its triggers. */
fun inMemoryDatabase(): PestoDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PestoDatabase::class.java).build()

/** [n] words. */
fun words(n: Int): String = List(n) { "λέξη" }.joinToString(" ")

/** A finished dictation as the delivery records it. [audioS] null: the connection broke. */
fun newDictation(
    rawText: String,
    text: String = rawText,
    packageName: String? = "com.whatsapp",
    profile: String = "raw",
    createdAt: Long = NOW,
    audioS: Double? = 2.0,
    fallbackReason: String? = null,
    insertMethod: String = "COMMIT_TEXT",
) = NewDictation(
    createdAt = createdAt,
    startedAt = createdAt - 3_000,
    context = FieldContext(packageName = packageName, inputType = "text"),
    requestedProfile = profile,
    final = audioS?.let { Final(rawText, text, profile, fallbackReason, "user", Stats(it, SearchText.words(text), 150, null, 160)) },
    lostText = if (audioS == null) text else null,
    insertMethod = insertMethod,
)
