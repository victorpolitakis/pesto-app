package com.victorpolitakis.pesto.util

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only way the app logs. Events and numbers only: never the text of a
 * field or a dictation. Behind an interface so JVM tests can use a fake.
 */
interface PestoLog {
    /** Why the mic button is (not) shown, e.g. `hidden reason=password`. Tag `Pesto/Button`. */
    fun button(message: String)

    /** One timing in milliseconds. Tag `Pesto/Perf`. */
    fun perf(name: String, ms: Long)

    /** Anything else worth knowing when debugging. Tag `Pesto`. */
    fun event(message: String)
}

@Singleton
class AndroidLog @Inject constructor() : PestoLog {
    override fun button(message: String) {
        Log.i("Pesto/Button", message)
    }

    override fun perf(name: String, ms: Long) {
        Log.i("Pesto/Perf", "$name=${ms}ms")
    }

    override fun event(message: String) {
        Log.i("Pesto", message)
    }
}
