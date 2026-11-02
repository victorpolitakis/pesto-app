package com.victorpolitakis.pesto.permissions

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.permissionStore by preferencesDataStore("permissions")

/**
 * What C3 remembers: whether the user has already answered "Deny" for the
 * microphone, and whether the Xiaomi page was opened once (it cannot be checked).
 * For the microphone: Only an actual deny counts: a dialog closed without an
 * answer does not, so the second request still shows the system dialog
 * (found on the POCO, 28/10). After a deny, "no rationale" means the system
 * dialog no longer appears, and only App info is left.
 */
@Singleton
class PermissionPrefs @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val micDenied = booleanPreferencesKey("mic_denied")
    private val xiaomiShown = booleanPreferencesKey("xiaomi_shown")

    // The patched keyboard (phase 4): its own Xiaomi autostart flag (kept apart from Pesto's own,
    // so doing one does not mark the other), and the languages of the last successful build.
    private val keyboardAutostartShown = booleanPreferencesKey("keyboard_autostart_shown")
    private val keyboardLanguages = stringPreferencesKey("keyboard_languages")

    suspend fun wasMicDenied(): Boolean = context.permissionStore.data.map { it[micDenied] == true }.first()

    suspend fun setMicDenied() {
        context.permissionStore.edit { it[micDenied] = true }
    }

    suspend fun wasXiaomiShown(): Boolean = context.permissionStore.data.map { it[xiaomiShown] == true }.first()

    suspend fun setXiaomiShown() {
        context.permissionStore.edit { it[xiaomiShown] = true }
    }

    suspend fun wasKeyboardAutostartShown(): Boolean =
        context.permissionStore.data.map { it[keyboardAutostartShown] == true }.first()

    suspend fun setKeyboardAutostartShown() {
        context.permissionStore.edit { it[keyboardAutostartShown] = true }
    }

    /** The languages of the last successful build, or null; used when the stock keyboard is off. */
    suspend fun lastKeyboardLanguages(): String? =
        context.permissionStore.data.map { it[keyboardLanguages] }.first()

    suspend fun setLastKeyboardLanguages(languages: String) {
        context.permissionStore.edit { it[keyboardLanguages] = languages }
    }
}
