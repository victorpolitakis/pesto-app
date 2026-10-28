package com.victorpolitakis.pesto.ui.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.data.ProfilesCatalog
import com.victorpolitakis.pesto.data.ReadyProfile
import com.victorpolitakis.pesto.ui.components.AppIcon
import com.victorpolitakis.pesto.ui.components.EmptyState
import com.victorpolitakis.pesto.ui.components.PestoTopBar
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/** One app that gets the profile. [automatic]: through the default, with no profile of its own. */
data class ProfileAppUi(val app: AppDisplay, val automatic: Boolean)

/** [profile] null once loaded: an id the catalog does not have (any more). */
data class ProfileDetailState(
    val profile: ReadyProfile? = null,
    val isDefault: Boolean = false,
    val apps: List<ProfileAppUi> = emptyList(),
    val dictations: Int = 0,
    val loading: Boolean = true,
)

@Composable
fun ProfileDetailRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileDetailScreen(state, onBack, onSetDefault = viewModel::setDefault, modifier = modifier)
}

/**
 * Profile detail (C9): one ready-made profile, without editing and without a
 * playground (Δ4). Its sentence of strings.xml (never the prompt), «Ορισμός ως
 * προεπιλογή», the apps that get it, and the dictations made with it.
 * TODO(phase 6): layout and design.
 */
@Composable
fun ProfileDetailScreen(
    state: ProfileDetailState,
    onBack: () -> Unit,
    onSetDefault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = state.profile
    Column(modifier.fillMaxSize().testTag("screen_profile_detail")) {
        PestoTopBar(if (profile == null) stringResource(R.string.title_profiles) else stringResource(profile.name), onBack)
        when {
            state.loading -> Unit
            profile == null -> EmptyState(stringResource(R.string.profile_gone))
            else -> LazyColumn(Modifier.fillMaxSize().testTag("profile_detail")) {
                item(key = "about") { About(profile, state.isDefault, state.dictations, onSetDefault) }
                item(key = "apps") {
                    Text(
                        stringResource(R.string.profile_apps),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp).semantics { heading() },
                    )
                }
                if (state.apps.isEmpty()) {
                    item(key = "no_apps") {
                        Text(
                            stringResource(R.string.profile_no_apps),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp).testTag("no_apps"),
                        )
                    }
                }
                items(state.apps, key = { it.app.packageName ?: it.app.name }) { app ->
                    ListItem(
                        headlineContent = { Text(app.app.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("profile_app_${app.app.packageName}"),
                        supportingContent = if (app.automatic) {
                            { Text(stringResource(R.string.profile_app_automatic)) }
                        } else {
                            null
                        },
                        leadingContent = { AppIcon(app.app) },
                    )
                }
            }
        }
    }
}

/** The sentence of the profile, the default, and its dictations. */
@Composable
private fun About(profile: ReadyProfile, isDefault: Boolean, dictations: Int, onSetDefault: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp)) {
        Text(stringResource(profile.description), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("profile_description"))
        Text(
            pluralStringResource(R.plurals.profile_dictations, dictations, dictations),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 16.dp).testTag("profile_dictations"),
        )
        if (isDefault) {
            Text(
                stringResource(R.string.profile_is_default),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp).testTag("is_default"),
            )
        } else {
            Button(onClick = onSetDefault, modifier = Modifier.padding(top = 16.dp).testTag("set_default")) {
                Text(stringResource(R.string.profile_set_default))
            }
        }
        Text(
            stringResource(R.string.profile_default_explained),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private val mail = ProfilesCatalog.find("mail")!!

@Preview
@Composable
private fun ProfileDetailEmptyPreview() {
    PestoTheme { ProfileDetailScreen(ProfileDetailState(mail, loading = false), onBack = {}, onSetDefault = {}) }
}

@Preview
@Composable
private fun ProfileDetailPreview() {
    val apps = listOf(
        ProfileAppUi(AppDisplay("com.google.android.gm", "Gmail", null), automatic = false),
        ProfileAppUi(AppDisplay("com.microsoft.office.outlook", "Outlook", null), automatic = true),
    )
    PestoTheme { ProfileDetailScreen(ProfileDetailState(mail, isDefault = true, apps, 42, loading = false), onBack = {}, onSetDefault = {}) }
}

@Preview(fontScale = 1.5f)
@Composable
private fun ProfileDetailLongGreekPreview() {
    val long = ReadyProfile("long", R.string.profile_default_explained, R.string.profiles_intro)
    val apps = List(12) { i -> ProfileAppUi(AppDisplay("gr.example.app$i", "Εφαρμογή με πάρα πολύ μεγάλο ελληνικό όνομα $i", null), i % 2 == 0) }
    PestoTheme { ProfileDetailScreen(ProfileDetailState(long, isDefault = false, apps, 56789, loading = false), onBack = {}, onSetDefault = {}) }
}
