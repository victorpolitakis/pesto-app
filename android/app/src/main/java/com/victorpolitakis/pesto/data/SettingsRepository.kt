package com.victorpolitakis.pesto.data

import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.di.AppScope
import com.victorpolitakis.pesto.overlay.OverlayMethod
import com.victorpolitakis.pesto.overlay.OverlayMethodProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** The settings of C11. The defaults are what phase 2 sent: Greek and English, not strict, raw, method 1. */
data class AppSettings(
    val primaryLanguage: String = "el",
    /** Null: no second language. */
    val secondaryLanguage: String? = "en",
    val strictLanguages: Boolean = false,
    val defaultProfile: String = ProfilesCatalog.RAW,
    val overlayMethod: OverlayMethod = OverlayMethod.ACCESSIBILITY,
) {
    /** The `language_hints` of the start message: the primary first, then the second if it differs. */
    val languageHints: List<String> get() = listOfNotNull(primaryLanguage, secondaryLanguage).distinct()
}

/** A language of the list of Settings (Δ14): the code the speech recognition gets, and its name. */
data class Language(val code: String, @param:StringRes val name: Int)

/**
 * The languages to choose from (Δ14): a short list to start with. ISO 639-1 codes,
 * which the speech recognition takes as language hints.
 */
object Languages {
    val all = listOf(
        Language("el", R.string.language_el),
        Language("en", R.string.language_en),
        Language("de", R.string.language_de),
        Language("fr", R.string.language_fr),
        Language("es", R.string.language_es),
        Language("it", R.string.language_it),
    )

    fun find(code: String?): Language? = all.firstOrNull { it.code == code }
}

/**
 * One place for the settings (C11), in the DataStore file `settings`. The
 * overlay method moved here from the debug-only SharedPreferences (Δ12), so
 * the release build has it too.
 * TODO(X11): the theme, next to these, for a Themes section in Settings and PestoTheme.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    @AppScope scope: CoroutineScope,
) : OverlayMethodProvider {
    /** What the file says. The first value waits for the file: the StartBuilder reads this one. */
    val stored: Flow<AppSettings> = store.data.map { read(it) }.distinctUntilChanged()

    /**
     * The same, in memory, for the accessibility service, which reads the overlay
     * method synchronously ([current]). Until the file is read: the defaults.
     */
    val settings: StateFlow<AppSettings> = stored.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    override fun current(): OverlayMethod = settings.value.overlayMethod

    suspend fun setPrimaryLanguage(code: String) = edit { it[PRIMARY] = code }

    /** [code] null: no second language. */
    suspend fun setSecondaryLanguage(code: String?) = edit { it[SECONDARY] = code ?: NONE }

    suspend fun setStrictLanguages(strict: Boolean) = edit { it[STRICT] = strict }

    suspend fun setDefaultProfile(id: String) = edit { it[DEFAULT_PROFILE] = id }

    suspend fun setOverlayMethod(method: OverlayMethod) = edit { it[OVERLAY] = method.name }

    private suspend fun edit(change: (MutablePreferences) -> Unit) {
        store.edit { change(it) }
    }

    private fun read(p: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            primaryLanguage = p[PRIMARY] ?: defaults.primaryLanguage,
            secondaryLanguage = when (val second = p[SECONDARY]) {
                null -> defaults.secondaryLanguage
                NONE -> null
                else -> second
            },
            strictLanguages = p[STRICT] ?: defaults.strictLanguages,
            defaultProfile = p[DEFAULT_PROFILE] ?: defaults.defaultProfile,
            overlayMethod = OverlayMethod.entries.firstOrNull { it.name == p[OVERLAY] } ?: defaults.overlayMethod,
        )
    }

    private companion object {
        val PRIMARY = stringPreferencesKey("primary_language")
        val SECONDARY = stringPreferencesKey("secondary_language")
        val STRICT = booleanPreferencesKey("strict_languages")
        val DEFAULT_PROFILE = stringPreferencesKey("default_profile")
        val OVERLAY = stringPreferencesKey("overlay_method")

        /** Stored for «no second language», which is not the same as «never chosen» (English). */
        const val NONE = ""
    }
}
