package com.victorpolitakis.pesto.ui.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.victorpolitakis.pesto.ui.components.ScreenSkeleton
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/**
 * Profile detail (C9): one ready-made profile, without editing (Δ4). Skeleton of step 6: the data come in step 9.
 * TODO(phase 6): layout and design.
 */
@Composable
fun ProfileDetailScreen(
    id: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenSkeleton(id, modifier.testTag("screen_profile_detail"), onBack = onBack)
}

@Preview
@Composable
private fun ProfileDetailPreview() {
    PestoTheme { ProfileDetailScreen(id = "mail", onBack = {}) }
}
