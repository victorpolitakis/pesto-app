package com.victorpolitakis.pesto.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.data.ProfilesCatalog
import com.victorpolitakis.pesto.ui.components.AppIcon
import com.victorpolitakis.pesto.ui.components.EmptyState
import com.victorpolitakis.pesto.ui.components.PestoTopBar
import com.victorpolitakis.pesto.ui.components.relativeTime
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/** One app of the History. [profile] null: automatic, it follows the default. */
data class AppRow(val app: AppDisplay, val dictations: Int, val lastUsed: Long, val profile: String?)

/** [choosing]: the package whose profile dialog is open. */
data class AppsState(
    val rows: List<AppRow> = emptyList(),
    val defaultProfile: String = ProfilesCatalog.RAW,
    val choosing: String? = null,
    val loading: Boolean = true,
)

@Composable
fun AppsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppsScreen(
        state,
        onBack = onBack,
        onChoose = viewModel::choose,
        onSetProfile = viewModel::setProfile,
        onDismiss = viewModel::dismiss,
        modifier = modifier,
    )
}

/**
 * Apps (C10): the apps of the History, the most used first, each with its
 * dictations, when it was last used and its profile. A row opens the choice of
 * the profile (C9): «Αυτόματο» (the default) or one of the ready-made ones.
 * TODO(phase 6): layout and design.
 */
@Composable
fun AppsScreen(
    state: AppsState,
    onBack: () -> Unit,
    onChoose: (String) -> Unit,
    onSetProfile: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().testTag("screen_apps")) {
        PestoTopBar(stringResource(R.string.title_apps), onBack)
        when {
            state.loading -> Unit
            state.rows.isEmpty() -> EmptyState(stringResource(R.string.apps_empty))
            else -> LazyColumn(Modifier.fillMaxSize().testTag("app_list")) {
                items(state.rows, key = { it.app.packageName ?: it.app.name }) { row ->
                    AppListItem(row, state.defaultProfile, onClick = { row.app.packageName?.let(onChoose) })
                }
            }
        }
        val choosing = state.rows.firstOrNull { it.app.packageName != null && it.app.packageName == state.choosing }
        if (choosing != null) ProfileDialog(choosing, state.defaultProfile, onSetProfile, onDismiss)
    }
}

@Composable
private fun AppListItem(row: AppRow, defaultProfile: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(row.app.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.clickable(onClick = onClick).testTag("app_${row.app.packageName}"),
        supportingContent = {
            Column {
                Text(
                    stringResource(
                        R.string.apps_usage,
                        pluralStringResource(R.plurals.dictations, row.dictations, row.dictations),
                        relativeTime(row.lastUsed),
                    ),
                )
                Text(profileLabel(row.profile, defaultProfile), color = MaterialTheme.colorScheme.primary)
            }
        },
        leadingContent = { AppIcon(row.app) },
    )
}

/** «Email», or «Αυτόματο (Email)» for an app that follows the default. */
@Composable
private fun profileLabel(profile: String?, defaultProfile: String): String =
    if (profile == null) stringResource(R.string.apps_automatic, profileName(defaultProfile)) else profileName(profile)

@Composable
private fun profileName(id: String): String = ProfilesCatalog.find(id)?.let { stringResource(it.name) } ?: id

/** One choice and it is saved: «Αυτόματο», then the ready-made profiles in the order of the catalog. */
@Composable
private fun ProfileDialog(
    row: AppRow,
    defaultProfile: String,
    onSetProfile: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("profile_cancel")) { Text(stringResource(R.string.cancel)) } },
        modifier = modifier.testTag("profile_dialog"),
        title = { Text(row.app.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                Choice(
                    stringResource(R.string.apps_automatic_option, profileName(defaultProfile)),
                    selected = row.profile == null,
                    onClick = { onSetProfile(null) },
                    Modifier.testTag("choice_automatic"),
                )
                for (profile in ProfilesCatalog.all) {
                    Choice(
                        stringResource(profile.name),
                        selected = row.profile == profile.id,
                        onClick = { onSetProfile(profile.id) },
                        Modifier.testTag("choice_${profile.id}"),
                    )
                }
            }
        },
    )
}

/** A radio row: the whole row is the touch target, as TalkBack reads it. */
@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

private val sampleRows = listOf(
    AppRow(AppDisplay("com.whatsapp", "WhatsApp", null), 54, System.currentTimeMillis() - 3_600_000, "chatter"),
    AppRow(AppDisplay("com.google.android.gm", "Gmail", null), 21, System.currentTimeMillis() - 86_400_000, null),
    AppRow(AppDisplay("com.slack", "Slack", null), 3, System.currentTimeMillis() - 5 * 86_400_000L, "professional"),
)

@Composable
private fun ScreenPreview(state: AppsState) {
    PestoTheme { AppsScreen(state, onBack = {}, onChoose = {}, onSetProfile = {}, onDismiss = {}) }
}

@Preview
@Composable
private fun AppsEmptyPreview() {
    ScreenPreview(AppsState(loading = false))
}

@Preview
@Composable
private fun AppsPreview() {
    ScreenPreview(AppsState(sampleRows, defaultProfile = "mail", loading = false))
}

@Preview
@Composable
private fun AppsDialogPreview() {
    ScreenPreview(AppsState(sampleRows, defaultProfile = "mail", choosing = "com.whatsapp", loading = false))
}

@Preview(fontScale = 1.5f)
@Composable
private fun AppsLongGreekPreview() {
    val rows = List(15) { i ->
        AppRow(AppDisplay("gr.example.app$i", "Εφαρμογή με πάρα πολύ μεγάλο ελληνικό όνομα αριθμός $i", null), 12_345, 0, null)
    }
    ScreenPreview(AppsState(rows, defaultProfile = "professional", loading = false))
}
