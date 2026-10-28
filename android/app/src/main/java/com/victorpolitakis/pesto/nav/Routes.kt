package com.victorpolitakis.pesto.nav

import kotlinx.serialization.Serializable

/**
 * The routes of the 10 screens (Δ10: Navigation Compose, type-safe). Every
 * screen has one, so the deep links of later features (X3, X7, notifications)
 * go on them without changing the screens.
 * TODO(later): a status page (a missing permission, a backend that does not answer), with data from
 * RequirementChecks and BackendApi.health, as Settings show them.
 */
object Routes {
    // The four tabs of the bottom bar (Δ20).
    @Serializable data object Home

    @Serializable data object Profiles

    @Serializable data object Dictionary

    @Serializable data object History

    // Without the bottom bar, with «back». [section]: a SettingsSection name, or null for the top.
    @Serializable data class Settings(val section: String? = null)

    @Serializable data object Search

    @Serializable data object Analytics

    @Serializable data object Apps

    @Serializable data class ProfileDetail(val id: String)

    @Serializable data class HistoryDetail(val id: Long)
}

/** The sections of Settings, in their order (C11). Home's Languages and C3's pairing open one directly. */
enum class SettingsSection { LANGUAGES, PROFILE, OVERLAY, PERMISSIONS, XIAOMI, BACKEND, PRIVACY, ABOUT }
