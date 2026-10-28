package com.victorpolitakis.pesto.ui.analytics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.ui.components.ScreenSkeleton
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/**
 * Analytics (C10): the real stats. Skeleton of step 6: the data come in step 10.
 * TODO(phase 6): layout and design.
 */
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenSkeleton(stringResource(R.string.title_analytics), modifier.testTag("screen_analytics"), onBack = onBack)
}

@Preview
@Composable
private fun AnalyticsPreview() {
    PestoTheme { AnalyticsScreen(onBack = {}) }
}
