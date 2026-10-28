package com.victorpolitakis.pesto.data

import androidx.annotation.StringRes
import com.victorpolitakis.pesto.R

/** A ready-made profile of the backend: its id, and the texts the screens show for it. */
data class ReadyProfile(
    val id: String,
    @param:StringRes val name: Int,
    @param:StringRes val description: Int,
)

/**
 * The ready-made profiles of the backend (C9, Δ3): ids and the texts of the
 * screens, nothing else. The prompts and the limits stay in the backend and
 * never reach the APK. ProfilesCatalogTest checks the ids against
 * backend/profiles/, so a new profile there fails the build until it is here.
 */
object ProfilesCatalog {
    const val RAW = "raw"

    val all = listOf(
        ReadyProfile(RAW, R.string.profile_raw, R.string.profile_raw_description),
        ReadyProfile("note", R.string.profile_note, R.string.profile_note_description),
        ReadyProfile("chatter", R.string.profile_chatter, R.string.profile_chatter_description),
        ReadyProfile("mail", R.string.profile_mail, R.string.profile_mail_description),
        ReadyProfile("professional", R.string.profile_professional, R.string.profile_professional_description),
    )

    fun find(id: String?): ReadyProfile? = all.firstOrNull { it.id == id }
}
