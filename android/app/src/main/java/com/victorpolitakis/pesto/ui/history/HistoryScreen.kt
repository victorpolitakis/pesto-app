package com.victorpolitakis.pesto.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.ui.components.ScreenSkeleton
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/**
 * History (C8): every dictation, newest first. Skeleton of step 6: the data come in step 7.
 * Until then [onOpenDictation] is not used.
 * TODO(phase 6): layout and design.
 */
@Composable
fun HistoryScreen(
    onOpenDictation: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenSkeleton(stringResource(R.string.title_history), modifier.testTag("screen_history"))
}

@Preview
@Composable
private fun HistoryPreview() {
    PestoTheme { HistoryScreen(onOpenDictation = {}) }
}
