package com.victorpolitakis.pesto.ui.apps

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.ui.components.ScreenSkeleton
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/**
 * Apps (C10): the apps of the History, and the profile of each (C9). Skeleton of step 6: the data come in step 10.
 * TODO(phase 6): layout and design.
 */
@Composable
fun AppsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenSkeleton(stringResource(R.string.title_apps), modifier.testTag("screen_apps"), onBack = onBack)
}

@Preview
@Composable
private fun AppsPreview() {
    PestoTheme { AppsScreen(onBack = {}) }
}
