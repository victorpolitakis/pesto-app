package com.victorpolitakis.pesto.overlay

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.ui.AppIcons
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/**
 * The panel that covers the keyboard while dictating. Stateless: everything
 * comes in as parameters. [profile]: the name of the profile this dictation
 * asked for, next to the status, so it is clear what will happen (C9).
 * TODO(phase 6): wave, buttons and states get the real design.
 * TODO(phase 6): change the profile from the panel (C9), with the design.
 */
@Composable
fun ListeningPanel(
    status: String,
    profile: String?,
    text: String,
    levels: List<Float>,
    paused: Boolean,
    onCancel: () -> Unit,
    onPauseResume: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(text) { scroll.animateScrollTo(scroll.maxValue) }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(dimensionResource(R.dimen.panel_padding))
            .testTag("listening_panel"),
    ) {
        Wave(levels, Modifier.fillMaxWidth().height(40.dp).testTag("wave"))
        Spacer(Modifier.height(8.dp))
        Text(
            if (profile == null) status else stringResource(R.string.panel_status_profile, status, profile),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.testTag("status"),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).testTag("live_text"),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Action(AppIcons.Cancel, R.string.cd_cancel, "cancel", onCancel)
            Action(if (paused) AppIcons.Resume else AppIcons.Pause, if (paused) R.string.cd_resume else R.string.cd_pause, "pause", onPauseResume)
            Action(AppIcons.Finish, R.string.cd_finish, "finish", onFinish)
        }
    }
}

@Composable
private fun Action(@DrawableRes icon: Int, description: Int, tag: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(dimensionResource(R.dimen.panel_action)).testTag(tag),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Icon(painterResource(icon), contentDescription = stringResource(description))
    }
}

@Composable
private fun Wave(levels: List<Float>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (levels.isEmpty()) return@Canvas
        val step = size.width / levels.size
        val bar = step * 0.6f
        levels.forEachIndexed { i, level ->
            val h = (size.height * (0.08f + 0.92f * level)).coerceAtMost(size.height)
            drawRoundRect(
                color,
                topLeft = Offset(i * step + (step - bar) / 2, (size.height - h) / 2),
                size = Size(bar, h),
                cornerRadius = CornerRadius(bar / 2),
            )
        }
    }
}

@Preview(heightDp = 300)
@Composable
private fun PanelEmptyPreview() {
    PestoTheme { ListeningPanel("Σύνδεση…", null, "", emptyList(), false, {}, {}, {}) }
}

@Preview(heightDp = 300)
@Composable
private fun PanelPreview() {
    PestoTheme {
        ListeningPanel("Ακούω…", "Email", "Καλημέρα Γιώργο, το meeting πάει", List(24) { (it % 7) / 7f }, false, {}, {}, {})
    }
}

@Preview(heightDp = 300)
@Composable
private fun PanelLongGreekPreview() {
    PestoTheme {
        ListeningPanel(
            "Παύση",
            "Επαγγελματικό",
            "Καλημέρα σε όλους, ".repeat(40) + "και ευχαριστώ για την υπομονή σας.",
            List(24) { 0f },
            true, {}, {}, {},
        )
    }
}
