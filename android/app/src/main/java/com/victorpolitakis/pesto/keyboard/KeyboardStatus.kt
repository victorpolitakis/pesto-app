package com.victorpolitakis.pesto.keyboard

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.victorpolitakis.pesto.permissions.KeyboardActive
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The step the user is on with the patched keyboard, in order. Each is the next thing to do; the
 * steps before it are done (PLAN_patch-keyboard-impl.md, phase 4). [stockStillEnabled] and the
 * patch/update choice are shown alongside, not as separate steps.
 */
enum class KeyboardStage {
    /** Not built yet: offer to patch. */
    NOT_INSTALLED,

    /** Installed, but not enabled in the system keyboard list: send the user there. */
    INSTALLED_NOT_ENABLED,

    /** Enabled, but not the chosen keyboard: tell the user to pick it with the globe. */
    ENABLED_NOT_CURRENT,

    /** Current, but on Xiaomi its autostart has not been allowed yet (S2). */
    XIAOMI_AUTOSTART,

    /** Installed, enabled and current: dictation goes through the keyboard. */
    ACTIVE,
}

object KeyboardStageRules {
    /**
     * The step to show. Each check assumes the ones before it are done.
     *
     * @param xiaomiAutostartShown the autostart page cannot be read, so it counts as done once opened
     */
    fun stage(
        installed: Boolean,
        enabled: Boolean,
        current: Boolean,
        isXiaomi: Boolean,
        xiaomiAutostartShown: Boolean,
    ): KeyboardStage = when {
        !installed -> KeyboardStage.NOT_INSTALLED
        !enabled -> KeyboardStage.INSTALLED_NOT_ENABLED
        !current -> KeyboardStage.ENABLED_NOT_CURRENT
        isXiaomi && !xiaomiAutostartShown -> KeyboardStage.XIAOMI_AUTOSTART
        else -> KeyboardStage.ACTIVE
    }
}

/**
 * Reads where the patched keyboard stands on the phone: installed, enabled in the system keyboard
 * list, the current keyboard, and whether the stock Gboard is still enabled (an advisory, so the
 * user can disable it). Device-only; the step logic is the pure [KeyboardStageRules].
 *
 * Also tells the permission flow whether the keyboard is active, so the accessibility requirement
 * leaves the list once dictation goes through the keyboard ([KeyboardActive]).
 */
@Singleton
class KeyboardStatus @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : KeyboardActive {
    private val resolver get() = context.contentResolver

    fun installed(): Boolean =
        runCatching { context.packageManager.getPackageInfo(KeyboardPatch.PATCHED_PACKAGE, 0) }.isSuccess

    /** The clone's input method is in the system's enabled list. */
    fun enabled(): Boolean = enabledImeIds().any { it.startsWith("${KeyboardPatch.PATCHED_PACKAGE}/") }

    /** The clone is the currently selected keyboard. */
    fun current(): Boolean =
        Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("${KeyboardPatch.PATCHED_PACKAGE}/") == true

    /** The stock Gboard is still enabled; the user is advised to disable it. */
    fun stockStillEnabled(): Boolean = enabledImeIds().any { it.startsWith("${KeyboardPatch.GBOARD_PACKAGE}/") }

    override fun isActive(): Boolean = installed() && enabled() && current()

    fun stage(isXiaomi: Boolean, xiaomiAutostartShown: Boolean): KeyboardStage =
        KeyboardStageRules.stage(installed(), enabled(), current(), isXiaomi, xiaomiAutostartShown)

    private fun enabledImeIds(): List<String> {
        // ENABLED_INPUT_METHODS is a colon-separated list of IME ids (pkg/.Service). It can be null
        // before the first keyboard is set, so fall back to the live list the IMM reports.
        val fromSettings = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_INPUT_METHODS)
        if (!fromSettings.isNullOrEmpty()) return fromSettings.split(':')
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return emptyList()
        return imm.enabledInputMethodList.map { it.id }
    }
}
