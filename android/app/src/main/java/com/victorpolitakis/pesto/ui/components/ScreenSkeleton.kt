package com.victorpolitakis.pesto.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/**
 * A screen before its data: the title (with «back» when [onBack] is given) and a
 * line. Only until every screen has its own content.
 * TODO(phase 6): nothing to design here, it goes before phase 6.
 */
@Composable
fun ScreenSkeleton(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    Column(modifier.fillMaxSize()) {
        if (onBack != null) PestoTopBar(title, onBack) else ScreenTitle(title)
        EmptyState(stringResource(R.string.skeleton))
    }
}

@Preview
@Composable
private fun ScreenSkeletonPreview() {
    PestoTheme { ScreenSkeleton("Στατιστικά", onBack = {}) }
}
