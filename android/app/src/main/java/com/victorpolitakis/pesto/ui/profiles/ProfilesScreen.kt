package com.victorpolitakis.pesto.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.data.ProfilesCatalog
import com.victorpolitakis.pesto.data.ReadyProfile
import com.victorpolitakis.pesto.ui.components.ScreenTitle
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/** One ready-made profile: whether it is the default, how many apps get it, how many dictations were made with it. */
data class ProfileRow(val profile: ReadyProfile, val isDefault: Boolean, val apps: Int, val dictations: Int)

data class ProfilesState(val rows: List<ProfileRow> = emptyList(), val loading: Boolean = true)

@Composable
fun ProfilesRoute(
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfilesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfilesScreen(state, onOpenProfile, modifier)
}

/**
 * Profiles (C9): the ready-made profiles of the backend (Δ3), in the order of
 * the catalog. Only the names and one sentence each: the prompts stay in the
 * backend. A row opens the profile.
 * TODO(phase 6): layout and design.
 */
@Composable
fun ProfilesScreen(
    state: ProfilesState,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().testTag("screen_profiles")) {
        ScreenTitle(stringResource(R.string.title_profiles))
        if (!state.loading) {
            Text(
                stringResource(R.string.profiles_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(Modifier.fillMaxSize().testTag("profile_list")) {
                items(state.rows, key = { it.profile.id }) { row ->
                    ProfileListItem(row, onClick = { onOpenProfile(row.profile.id) })
                }
            }
        }
    }
}

@Composable
private fun ProfileListItem(row: ProfileRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(stringResource(row.profile.name)) },
        modifier = modifier.clickable(onClick = onClick).testTag("profile_${row.profile.id}"),
        supportingContent = {
            Column {
                Text(stringResource(row.profile.description))
                Text(
                    stringResource(
                        R.string.profile_usage,
                        pluralStringResource(R.plurals.apps, row.apps, row.apps),
                        pluralStringResource(R.plurals.dictations, row.dictations, row.dictations),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        trailingContent = {
            if (row.isDefault) {
                Text(
                    stringResource(R.string.profile_default),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

private fun sampleRows(default: String = "mail") = ProfilesCatalog.all.mapIndexed { i, p ->
    ProfileRow(p, p.id == default, apps = i, dictations = i * 7)
}

@Preview
@Composable
private fun ProfilesLoadingPreview() {
    PestoTheme { ProfilesScreen(ProfilesState(), onOpenProfile = {}) }
}

@Preview
@Composable
private fun ProfilesPreview() {
    PestoTheme { ProfilesScreen(ProfilesState(sampleRows(), loading = false), onOpenProfile = {}) }
}

@Preview(fontScale = 1.5f)
@Composable
private fun ProfilesLongGreekPreview() {
    // The texts come from strings.xml: a made-up row with two long ones, and large numbers.
    val long = ProfileRow(ReadyProfile("long", R.string.profile_default_explained, R.string.profiles_intro), true, 1234, 56789)
    PestoTheme { ProfilesScreen(ProfilesState(listOf(long) + sampleRows(), loading = false), onOpenProfile = {}) }
}
