package com.victorpolitakis.pesto.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One place for «which profile» (C9): the catalog of the ready-made ones, the
 * default, and the profile of each app (Apps screen). The phone only sends the
 * id in the start message; the backend knows the rest.
 * TODO(X13): my own profiles next to the ready-made ones; their prompts are set in the backend, never in the APK.
 */
@Singleton
class ProfilesRepository @Inject constructor(
    private val dao: AppProfileDao,
    private val settings: SettingsRepository,
) {
    val catalog: List<ReadyProfile> get() = ProfilesCatalog.all

    /** The default profile (Profiles, Settings), always one of the catalog. */
    val defaultProfile: Flow<String> = settings.stored.map { known(it.defaultProfile) }

    suspend fun setDefault(id: String) = settings.setDefaultProfile(known(id))

    /** Package → profile id, for the apps with their own profile. An id the catalog lost is left out. */
    val appProfiles: Flow<Map<String, String>> = dao.all().map { rows ->
        rows.filter { ProfilesCatalog.find(it.profileId) != null }.associate { it.packageName to it.profileId }
    }

    /** [profileId] null: automatic, the app follows the default. */
    suspend fun setAppProfile(packageName: String, profileId: String?) {
        if (profileId == null) dao.remove(packageName) else dao.set(AppProfileEntity(packageName, known(profileId)))
    }

    companion object {
        /** Δ5: the profile of the app, else the default, else raw. */
        fun choose(packageName: String?, appProfiles: Map<String, String>, default: String): String =
            packageName?.let { appProfiles[it] } ?: known(default)

        private fun known(id: String): String = ProfilesCatalog.find(id)?.id ?: ProfilesCatalog.RAW
    }
}
