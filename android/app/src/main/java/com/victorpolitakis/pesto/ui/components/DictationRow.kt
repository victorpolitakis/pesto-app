package com.victorpolitakis.pesto.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.data.AppInfo
import com.victorpolitakis.pesto.data.DictationItem
import com.victorpolitakis.pesto.data.Snippet
import com.victorpolitakis.pesto.ui.AppIcons
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import com.victorpolitakis.pesto.util.RelativeDate
import java.time.ZoneId

/** One dictation in a list: the app, when it ended, and the words said. */
data class DictationRowUi(val id: Long, val app: AppDisplay, val createdAt: Long, val words: Int)

suspend fun DictationItem.toRow(apps: AppInfo) = DictationRowUi(id, apps.of(packageName), createdAt, words)

/**
 * The row of History, Search and Home (the sketch: «Gmail · Χθες, 18:26 · 56
 * λέξεις»). [snippet]: in Search, the text around the words found, which are bold.
 * TODO(phase 6): the design of the row.
 */
@Composable
fun DictationRow(row: DictationRowUi, onClick: () -> Unit, modifier: Modifier = Modifier, snippet: Snippet? = null) {
    ListItem(
        headlineContent = { Text(row.app.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.clickable(onClick = onClick).testTag("dictation_${row.id}"),
        supportingContent = {
            Column {
                Text(relativeTime(row.createdAt), maxLines = 1)
                if (snippet != null) HighlightedText(snippet)
            }
        },
        leadingContent = { AppIcon(row.app) },
        trailingContent = { Text(pluralStringResource(R.plurals.words, row.words, row.words)) },
    )
}

/** The icon of an app, or the generic one. Decorative: its name is always next to it. TODO(phase 6): the shape. */
@Composable
fun AppIcon(app: AppDisplay, modifier: Modifier = Modifier) {
    val icon = app.icon
    Box(modifier.size(dimensionResource(R.dimen.app_icon))) {
        if (icon != null) {
            val image = remember(icon) { icon.asImageBitmap() }
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(AppIcons.App), contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun HighlightedText(snippet: Snippet, modifier: Modifier = Modifier) {
    val text = remember(snippet) {
        buildAnnotatedString {
            append(snippet.text)
            for (found in snippet.highlights) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), found.first, minOf(found.last + 1, snippet.text.length))
            }
        }
    }
    Text(
        text,
        modifier = modifier.testTag("snippet"),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** [RelativeDate] with the phone's 12/24 hours, its language and the words of strings.xml. */
@Composable
fun relativeTime(time: Long): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val today = stringResource(R.string.time_today)
    val yesterday = stringResource(R.string.time_yesterday)
    return remember(time, today, yesterday, locale) {
        RelativeDate.format(time, System.currentTimeMillis(), ZoneId.systemDefault(), DateFormat.is24HourFormat(context), today, yesterday, locale)
    }
}

/** The exact time, for the History detail. */
@Composable
fun exactTime(time: Long): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(time, locale) { RelativeDate.exact(time, ZoneId.systemDefault(), DateFormat.is24HourFormat(context), locale) }
}

private val previewApp = AppDisplay("com.google.android.gm", "Gmail", null)

@Preview
@Composable
private fun DictationRowPreview() {
    PestoTheme { DictationRow(DictationRowUi(1, previewApp, System.currentTimeMillis() - 86_400_000, 56), onClick = {}) }
}

@Preview
@Composable
private fun DictationRowSnippetPreview() {
    PestoTheme {
        DictationRow(
            DictationRowUi(2, previewApp, System.currentTimeMillis(), 12),
            onClick = {},
            snippet = Snippet("…και το meeting πάει για την Πέμπτη στις δέκα", listOf(7..13)),
        )
    }
}

@Preview
@Composable
private fun DictationRowLongGreekPreview() {
    PestoTheme {
        DictationRow(
            DictationRowUi(3, AppDisplay("gr.example", "Εφαρμογή με πάρα πολύ μεγάλο ελληνικό όνομα για δοκιμή", null), 0, 12_345),
            onClick = {},
            snippet = Snippet("Καλημέρα σε όλους, ".repeat(10), listOf(0..7)),
        )
    }
}
