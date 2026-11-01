package com.victorpolitakis.pesto.keyboard

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import com.victorpolitakis.pesto.util.PestoLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject

/**
 * Builds the patched keyboard in a process of its own (`android:process=":patcher"`, large heap):
 * merging and patching a ~100 MB APK needs a lot of memory, and when it is done the process ends
 * and gives the memory back, so Pesto itself never carries it (like `:asr` for the model).
 *
 * Settings (phase 4) binds it, sends [MSG_BUILD] with the languages file, and gets [MSG_STEP]
 * progress and then [MSG_DONE] with the signed APK path or [MSG_FAILED]. The build runs on a
 * background thread; only this app can bind it (not exported).
 */
@AndroidEntryPoint
class KeyboardPatchService : Service() {
    @Inject lateinit var patcher: KeyboardPatcher
    @Inject lateinit var log: PestoLog

    private val thread = HandlerThread("patcher").apply { start() }
    private val messenger by lazy { Messenger(Handler(thread.looper, ::handle)) }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        super.onDestroy()
        thread.quitSafely()
        // Nothing binds it any more: end the process so its large heap is surely freed.
        Process.killProcess(Process.myPid())
    }

    private fun handle(message: Message): Boolean {
        if (message.what != MSG_BUILD) return false
        val reply = message.replyTo
        val languages = message.data?.getString(KEY_LANGUAGES)
        val workDir = File(cacheDir, "keyboard-patch").apply { deleteRecursively() }
        try {
            val apk = runBlocking {
                patcher.buildPatchedApk(workDir, languages) { step -> reply?.trySend(MSG_STEP, KEY_STEP, step.name) }
            }
            reply?.trySend(MSG_DONE, KEY_APK_PATH, apk.absolutePath)
        } catch (e: KeyboardPatchException) {
            log.event("keyboard patch failed at ${e.step}: ${e.message}")
            reply?.trySend(MSG_FAILED, KEY_STEP, e.step.name)
        } catch (e: Exception) {
            log.event("keyboard patch failed: ${e.javaClass.simpleName}")
            reply?.trySend(MSG_FAILED)
        }
        return true
    }

    companion object {
        const val MSG_BUILD = 1
        const val MSG_STEP = 10
        const val MSG_DONE = 11
        const val MSG_FAILED = 12

        const val KEY_LANGUAGES = "languages"
        const val KEY_STEP = "step"
        const val KEY_APK_PATH = "apk_path"
    }
}

/** Sends one bundle message, swallowing a dead peer. */
private fun Messenger.trySend(what: Int, key: String? = null, value: String? = null) {
    val message = Message.obtain(null, what)
    if (key != null) message.data = Bundle().apply { putString(key, value) }
    runCatching { send(message) }
}
