package com.victorpolitakis.pesto.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import com.victorpolitakis.pesto.R

/** Minimal theme from res/values/colors.xml. TODO(phase 6): the real design. */
@Composable
fun PestoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = colorResource(R.color.pesto_primary),
            onPrimary = colorResource(R.color.pesto_on_primary),
            surface = colorResource(R.color.pesto_panel),
            onSurface = colorResource(R.color.pesto_on_panel),
        ),
        content = content,
    )
}
