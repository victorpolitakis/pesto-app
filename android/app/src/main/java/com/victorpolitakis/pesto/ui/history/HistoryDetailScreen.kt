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
 * History detail (C8): one dictation, its text first. Skeleton of step 6: the data come in step 7.
 * Until then [id] is not used.
 * TODO(phase 6): layout and design.
 */
@Composable
fun HistoryDetailScreen(
    id: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenSkeleton(stringResource(R.string.title_dictation), modifier.testTag("screen_history_detail"), onBack = onBack)
}

@Preview
@Composable
private fun HistoryDetailPreview() {
    PestoTheme { HistoryDetailScreen(id = 1L, onBack = {}) }
}
