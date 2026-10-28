package com.victorpolitakis.pesto.ui

import androidx.annotation.DrawableRes
import com.victorpolitakis.pesto.R

/**
 * Every icon of the app comes from here. TODO(phase 6): these are temporary
 * vector icons (Material paths); they are replaced by Victor's icons in one place.
 * TODO(phase 6): placeholders for the avatar and the chatbot (X3) of Home.
 */
object AppIcons {
    // The listening panel (phase 2).
    @DrawableRes val Mic = R.drawable.ic_mic
    @DrawableRes val Cancel = R.drawable.ic_close
    @DrawableRes val Pause = R.drawable.ic_pause
    @DrawableRes val Resume = R.drawable.ic_play
    @DrawableRes val Finish = R.drawable.ic_check

    // The bottom bar (Δ20).
    @DrawableRes val Home = R.drawable.ic_home
    @DrawableRes val Profiles = R.drawable.ic_profiles
    @DrawableRes val Dictionary = R.drawable.ic_dictionary
    @DrawableRes val History = R.drawable.ic_history

    // The top of Home, after the sketch.
    @DrawableRes val Avatar = R.drawable.ic_person
    @DrawableRes val Search = R.drawable.ic_search
    @DrawableRes val Analytics = R.drawable.ic_analytics
    @DrawableRes val Chatbot = R.drawable.ic_chat

    // The four buttons of Home.
    @DrawableRes val AddStyle = R.drawable.ic_add
    @DrawableRes val Edit = R.drawable.ic_edit
    @DrawableRes val Languages = R.drawable.ic_language
    @DrawableRes val More = R.drawable.ic_more

    @DrawableRes val Back = R.drawable.ic_back
    @DrawableRes val Add = R.drawable.ic_add
    @DrawableRes val Copy = R.drawable.ic_copy
    @DrawableRes val Delete = R.drawable.ic_delete

    /** An app that is not installed, or not visible to Pesto. */
    @DrawableRes val App = R.drawable.ic_app
}
