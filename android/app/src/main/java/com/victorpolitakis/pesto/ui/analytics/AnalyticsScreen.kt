package com.victorpolitakis.pesto.ui.analytics

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.data.DayCount
import com.victorpolitakis.pesto.data.KeyCount
import com.victorpolitakis.pesto.data.ProfilesCatalog
import com.victorpolitakis.pesto.data.StatsRepository
import com.victorpolitakis.pesto.data.Totals
import com.victorpolitakis.pesto.ui.components.AppIcon
import com.victorpolitakis.pesto.ui.components.EmptyState
import com.victorpolitakis.pesto.ui.components.PestoTopBar
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import com.victorpolitakis.pesto.util.RelativeDate
import java.time.LocalDate
import kotlin.math.roundToInt

/** One app in the counts of Analytics. */
data class AppCount(val app: AppDisplay, val dictations: Int)

/** [days]: the last [MONTH] days, oldest first; the week is the end of it. */
data class AnalyticsState(
    val totals: Totals = Totals(),
    val days: List<DayCount> = emptyList(),
    val apps: List<AppCount> = emptyList(),
    val profiles: List<KeyCount> = emptyList(),
    val fallbackReasons: List<KeyCount> = emptyList(),
    val endReasons: List<KeyCount> = emptyList(),
    val insertMethods: List<KeyCount> = emptyList(),
    val loading: Boolean = true,
) {
    companion object {
        const val WEEK = 7
        const val MONTH = 30
    }
}

@Composable
fun AnalyticsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Which bars are on screen: a choice of the screen only, kept across rotation.
    var days by rememberSaveable { mutableIntStateOf(AnalyticsState.WEEK) }
    AnalyticsScreen(state, days, onDaysChange = { days = it }, onBack = onBack, modifier = modifier)
}

/**
 * Analytics (C10): the totals of the History, words per day for 7 or 30 days,
 * per app, per profile, and «Αξιοπιστία» (fallback_reason, end_reason,
 * insert_method) for the data check of phase 4. Incognito counts nothing (Δ7).
 * TODO(phase 6): layout and design.
 */
@Composable
fun AnalyticsScreen(
    state: AnalyticsState,
    days: Int,
    onDaysChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().testTag("screen_analytics")) {
        PestoTopBar(stringResource(R.string.title_analytics), onBack)
        when {
            state.loading -> Unit
            state.totals.dictations == 0 -> EmptyState(stringResource(R.string.analytics_empty))
            else -> LazyColumn(Modifier.fillMaxSize().testTag("analytics")) {
                item(key = "totals") { TotalsSection(state.totals) }
                item(key = "days") { DaysSection(state.days.takeLast(days), days, onDaysChange) }
                item(key = "apps") { Heading(stringResource(R.string.analytics_per_app)) }
                items(state.apps, key = { "app_" + (it.app.packageName ?: it.app.name) }) { app ->
                    ListItem(
                        headlineContent = { Text(app.app.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("analytics_app_${app.app.packageName}"),
                        leadingContent = { AppIcon(app.app) },
                        trailingContent = { Text(stringResource(R.string.value_int, app.dictations)) },
                    )
                }
                counts("profiles", R.string.analytics_per_profile, state.profiles) { profileName(it) }
                item(key = "reliability") { Heading(stringResource(R.string.analytics_reliability)) }
                counts("fallback", R.string.detail_fallback, state.fallbackReasons) { it ?: stringResource(R.string.analytics_no_fallback) }
                counts("end", R.string.detail_end_reason, state.endReasons) { it ?: stringResource(R.string.value_none) }
                counts("insert", R.string.detail_insert_method, state.insertMethods) { it ?: stringResource(R.string.value_none) }
            }
        }
    }
}

@Composable
private fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** A subtitle and one row per value, with its dictations; [label] names a value (null: none). */
private fun LazyListScope.counts(key: String, @StringRes title: Int, counts: List<KeyCount>, label: @Composable (String?) -> String) {
    item(key = key) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
    }
    items(counts, key = { "${key}_${it.value}" }) { count ->
        CountRow(label(count.value), count.dictations, Modifier.testTag("count_${key}_${count.value}"))
    }
}

@Composable
private fun CountRow(label: String, value: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.value_int, value))
    }
}

@Composable
private fun profileName(id: String?): String {
    val profile = ProfilesCatalog.find(id) ?: return id ?: stringResource(R.string.value_none)
    return stringResource(R.string.value_with_id, stringResource(profile.name), profile.id)
}

@Composable
private fun TotalsSection(totals: Totals, modifier: Modifier = Modifier) {
    Column(modifier.testTag("analytics_totals")) {
        Heading(stringResource(R.string.analytics_totals))
        CountRow(stringResource(R.string.analytics_words), totals.words, Modifier.testTag("total_words"))
        CountRow(stringResource(R.string.analytics_dictations), totals.dictations, Modifier.testTag("total_dictations"))
        Total(stringResource(R.string.analytics_speech_minutes), stringResource(R.string.value_decimal, totals.speechMinutes), "total_speech")
        Total(
            stringResource(R.string.stat_minutes_saved),
            stringResource(R.string.value_decimal, totals.minutesSaved),
            "total_saved",
            note = stringResource(R.string.stat_minutes_saved_note, StatsRepository.TYPING_WPM),
        )
        Total(
            stringResource(R.string.stat_wpm),
            totals.wpm?.let { stringResource(R.string.value_int, it.roundToInt()) } ?: stringResource(R.string.value_none),
            "total_wpm",
        )
    }
}

@Composable
private fun Total(label: String, value: String, tag: String, modifier: Modifier = Modifier, note: String? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics(mergeDescendants = true) {}.testTag(tag)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(label, modifier = Modifier.weight(1f))
            Text(value)
        }
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The bars, and the choice between the week and the month. */
@Composable
private fun DaysSection(days: List<DayCount>, period: Int, onPeriodChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Heading(stringResource(R.string.analytics_per_day))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in listOf(AnalyticsState.WEEK, AnalyticsState.MONTH)) {
                FilterChip(
                    selected = period == option,
                    onClick = { onPeriodChange(option) },
                    label = { Text(pluralStringResource(R.plurals.days, option, option)) },
                    modifier = Modifier.testTag("days_$option"),
                )
            }
        }
        DayBars(days, showWeekdays = period == AnalyticsState.WEEK)
    }
}

/**
 * Words per day, as bars of the same width; each bar says its day, words and
 * dictations to TalkBack.
 * TODO(phase 6): bars of plain Box, without a chart library (Δ1), and without axes.
 */
@Composable
private fun DayBars(days: List<DayCount>, showWeekdays: Boolean, modifier: Modifier = Modifier) {
    val most = days.maxOfOrNull { it.words }?.takeIf { it > 0 } ?: 1
    Column(modifier.fillMaxWidth().padding(16.dp).testTag("day_bars")) {
        Row(
            Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (day in days) {
                val description = stringResource(
                    R.string.cd_day_bar,
                    RelativeDate.day(day.day),
                    pluralStringResource(R.plurals.words, day.words, day.words),
                    pluralStringResource(R.plurals.dictations, day.dictations, day.dictations),
                )
                Column(
                    Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = description }.testTag("day_bar"),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    // A day without words keeps a line, so the days stay countable.
                    val fraction = (day.words.toFloat() / most).coerceAtLeast(0.02f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        if (showWeekdays) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (day in days) {
                    Text(
                        RelativeDate.weekday(day.day),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private val sampleTotals = Totals(
    dictations = 128, dictationsToday = 6, words = 9_412, wordsToday = 310, wordsThisWeek = 1_984,
    speechMinutes = 71.5, minutesSaved = 163.8, wpm = 131.6,
)

private fun sampleDays() = List(AnalyticsState.MONTH) { i ->
    DayCount(LocalDate.of(2026, 10, 28).minusDays((AnalyticsState.MONTH - 1 - i).toLong()), dictations = i % 4, words = (i * 37) % 260)
}

private val sampleState = AnalyticsState(
    totals = sampleTotals,
    days = sampleDays(),
    apps = listOf(AppCount(AppDisplay("com.whatsapp", "WhatsApp", null), 54), AppCount(AppDisplay("com.google.android.gm", "Gmail", null), 21)),
    profiles = listOf(KeyCount("chatter", 54), KeyCount("mail", 21), KeyCount("raw", 3)),
    fallbackReasons = listOf(KeyCount(null, 120), KeyCount("guard:length", 5), KeyCount("timeout", 3)),
    endReasons = listOf(KeyCount("user", 110), KeyCount("silence", 15), KeyCount("connection_lost", 3)),
    insertMethods = listOf(KeyCount("COMMIT_TEXT", 100), KeyCount("SET_TEXT", 28)),
    loading = false,
)

@Composable
private fun ScreenPreview(state: AnalyticsState, days: Int = AnalyticsState.WEEK) {
    PestoTheme { AnalyticsScreen(state, days, onDaysChange = {}, onBack = {}) }
}

@Preview
@Composable
private fun AnalyticsEmptyPreview() {
    ScreenPreview(AnalyticsState(loading = false))
}

@Preview(heightDp = 1400)
@Composable
private fun AnalyticsPreview() {
    ScreenPreview(sampleState)
}

@Preview(heightDp = 1400, fontScale = 1.5f)
@Composable
private fun AnalyticsLongGreekPreview() {
    val long = AppDisplay("gr.example", "Εφαρμογή με πάρα πολύ μεγάλο ελληνικό όνομα που δεν χωράει", null)
    ScreenPreview(
        sampleState.copy(
            totals = sampleTotals.copy(words = 1_234_567, dictations = 98_765),
            apps = List(5) { i -> AppCount(long.copy(packageName = "gr.example.app$i"), 12_345) },
            fallbackReasons = listOf(KeyCount("ένας πολύ μεγάλος λόγος χωρίς κενά_και_με_πολλές_λέξεις_στη_σειρά", 1)),
        ),
        days = AnalyticsState.MONTH,
    )
}
