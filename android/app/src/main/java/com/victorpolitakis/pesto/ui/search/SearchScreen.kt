package com.victorpolitakis.pesto.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.ui.components.ScreenSkeleton
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/**
 * Search (C8): the History through the FTS index. Skeleton of step 6: the data come in step 7.
 * Until then [onOpenDictation] is not used.
 * TODO(phase 6): layout and design.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenDictation: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenSkeleton(stringResource(R.string.title_search), modifier.testTag("screen_search"), onBack = onBack)
}

@Preview
@Composable
private fun SearchPreview() {
    PestoTheme { SearchScreen(onBack = {}, onOpenDictation = {}) }
}
