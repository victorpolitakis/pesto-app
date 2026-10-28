package com.victorpolitakis.pesto.permissions

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.permissionStore by preferencesDataStore("permissions")

/**
 * The only thing C3 remembers: whether the microphone was asked for before,
 * to tell the second "Deny" (the system dialog no longer appears) from the first.
 */
@Singleton
class PermissionPrefs @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val micAsked = booleanPreferencesKey("mic_asked")

    suspend fun wasMicAsked(): Boolean = context.permissionStore.data.map { it[micAsked] == true }.first()

    suspend fun setMicAsked() {
        context.permissionStore.edit { it[micAsked] = true }
    }
}
