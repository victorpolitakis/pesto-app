package com.victorpolitakis.pesto.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.ui.components.ScreenTitle
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/**
 * Home (C10), in place of the status page of phase 2. Skeleton of step 6, with
 * a way to every screen: the layout of the sketch comes in step 10.
 * TODO(phase 6): layout and design.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().testTag("screen_home")) {
        ScreenTitle(stringResource(R.string.app_name))
        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.title_settings)) }
        TextButton(onClick = onOpenSearch) { Text(stringResource(R.string.title_search)) }
        TextButton(onClick = onOpenAnalytics) { Text(stringResource(R.string.title_analytics)) }
        TextButton(onClick = onOpenApps) { Text(stringResource(R.string.title_apps)) }
    }
}

@Preview
@Composable
private fun HomePreview() {
    PestoTheme { HomeScreen(onOpenSettings = {}, onOpenSearch = {}, onOpenAnalytics = {}, onOpenApps = {}) }
}
