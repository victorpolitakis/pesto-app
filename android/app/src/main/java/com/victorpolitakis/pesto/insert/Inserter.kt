package com.victorpolitakis.pesto.insert

/** How the text reached the user. Recorded in the History, to find apps where insertion fails (X1). */
enum class InsertMethod { COMMIT_TEXT, SET_TEXT, CLIPBOARD }

/**
 * Puts dictated text into the field with focus (C5). One implementation today
 * (accessibility); X1 extends it. Never presses send: it only writes text.
 */
interface Inserter {
    /** [terms]: the Dictionary terms of the dictation, which keep their case (TextJoiner). */
    suspend fun insert(text: String, terms: Set<String>): InsertMethod
}
