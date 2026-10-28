package com.victorpolitakis.pesto.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.ui.components.ScreenSkeleton
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/**
 * Settings (C11). Skeleton of step 6: the data come in step 11.
 * Until then [section] is not used.
 * TODO(phase 6): layout and design.
 */
@Composable
fun SettingsScreen(
    section: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenSkeleton(stringResource(R.string.title_settings), modifier.testTag("screen_settings"), onBack = onBack)
}

@Preview
@Composable
private fun SettingsPreview() {
    PestoTheme { SettingsScreen(section = null, onBack = {}) }
}
