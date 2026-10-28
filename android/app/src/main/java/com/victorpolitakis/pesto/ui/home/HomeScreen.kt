package com.victorpolitakis.pesto.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.victorpolitakis.pesto.data.StatsRepository
import com.victorpolitakis.pesto.data.Totals
import com.victorpolitakis.pesto.ui.AppIcons
import com.victorpolitakis.pesto.ui.components.AppIcon
import com.victorpolitakis.pesto.ui.components.DictationRow
import com.victorpolitakis.pesto.ui.components.DictationRowUi
import com.victorpolitakis.pesto.ui.components.EmptyState
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import kotlin.math.roundToInt

/** The four pages of the big number (Δ21), in their order: the minutes saved first, as in the sketch. */
enum class HomePage { MINUTES_SAVED, WORDS_THIS_WEEK, DICTATIONS, WPM }

/** What Home shows: the totals of the History, its top 3 apps, its last 5 dictations. */
data class HomeState(
    val totals: Totals = Totals(),
    val topApps: List<AppDisplay> = emptyList(),
    val recent: List<DictationRowUi> = emptyList(),
    val loading: Boolean = true,
)

@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenLanguages: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDictation: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state,
        onOpenSettings = onOpenSettings,
        onOpenSearch = onOpenSearch,
        onOpenAnalytics = onOpenAnalytics,
        onOpenApps = onOpenApps,
        onOpenLanguages = onOpenLanguages,
        onOpenHistory = onOpenHistory,
        onOpenDictation = onOpenDictation,
        modifier = modifier,
    )
}

/**
 * Home (C10), from the top down as in the sketch («σαν Revolut»): the top row
 * (avatar, search, Analytics, chatbot), the big number with its four pages
 * (Δ21), «Εφαρμογές» with the top 3, the page dots, four buttons, and the last
 * dictations (Δ22). No warnings here: C3 asks for what is missing.
 * TODO(phase 6): layout and design, after the sketch.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenLanguages: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDictation: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState { HomePage.entries.size }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("screen_home"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopRow(onOpenSettings, onOpenSearch, onOpenAnalytics)
        HorizontalPager(pager, Modifier.fillMaxWidth().testTag("home_pager")) { page ->
            StatPage(HomePage.entries[page], state.totals)
        }
        AppsButton(state.topApps, onOpenApps)
        PageDots(pager.currentPage, HomePage.entries.size)
        Actions(onOpenLanguages)
        Recent(state.recent, showEmpty = !state.loading, onOpenHistory, onOpenDictation)
    }
}

/** Avatar → Settings, the search bar → Search, and two circles of the same size: Analytics and the chatbot. */
@Composable
private fun TopRow(onOpenSettings: () -> Unit, onOpenSearch: () -> Unit, onOpenAnalytics: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("home_top"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // TODO(phase 6): the avatar is a placeholder icon.
        FilledTonalIconButton(onClick = onOpenSettings, modifier = Modifier.testTag("home_avatar")) {
            Icon(painterResource(AppIcons.Avatar), contentDescription = stringResource(R.string.title_settings))
        }
        Surface(
            onClick = onOpenSearch,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("home_search"),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(AppIcons.Search), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.search_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FilledTonalIconButton(onClick = onOpenAnalytics, modifier = Modifier.testTag("home_analytics")) {
            Icon(painterResource(AppIcons.Analytics), contentDescription = stringResource(R.string.title_analytics))
        }
        // TODO(X3): the assistant opens here. No action until then.
        FilledTonalIconButton(onClick = {}, modifier = Modifier.testTag("home_chatbot")) {
            Icon(painterResource(AppIcons.Chatbot), contentDescription = stringResource(R.string.cd_chatbot))
        }
    }
}

/** One page of the big number: its title above, and a line below where the page has one. */
@Composable
private fun StatPage(page: HomePage, totals: Totals, modifier: Modifier = Modifier) {
    val (title, value, note) = when (page) {
        HomePage.MINUTES_SAVED -> Triple(
            stringResource(R.string.stat_minutes_saved),
            minutes(totals.minutesSaved),
            stringResource(R.string.stat_minutes_saved_note, StatsRepository.TYPING_WPM),
        )
        HomePage.WORDS_THIS_WEEK -> Triple(
            stringResource(R.string.stat_words_week),
            number(totals.wordsThisWeek),
            stringResource(R.string.stat_today, number(totals.wordsToday)),
        )
        HomePage.DICTATIONS -> Triple(
            stringResource(R.string.stat_dictations),
            number(totals.dictations),
            stringResource(R.string.stat_today, number(totals.dictationsToday)),
        )
        HomePage.WPM -> Triple(
            stringResource(R.string.stat_wpm),
            totals.wpm?.let { number(it.roundToInt()) } ?: stringResource(R.string.value_none),
            null,
        )
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("home_page_${page.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(value, style = MaterialTheme.typography.displayLarge, maxLines = 1, modifier = Modifier.testTag("home_value"))
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun number(value: Int): String = stringResource(R.string.value_int, value)

/** One decimal while they are few («1,5»), whole minutes after. */
@Composable
private fun minutes(value: Double): String =
    if (value < 10) stringResource(R.string.value_decimal, value) else number(value.roundToInt())

/** «Εφαρμογές», with the icons of the top 3 apps in it (Δ21). */
@Composable
private fun AppsButton(topApps: List<AppDisplay>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.testTag("home_apps")) {
        for (app in topApps) {
            AppIcon(app, Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
        }
        if (topApps.isNotEmpty()) Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.title_apps))
    }
}

/** Which page of the big number is on screen. */
@Composable
private fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_page, current + 1, count)
    Row(
        modifier.padding(8.dp).semantics { contentDescription = description }.testTag("home_dots"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { i ->
            val color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        }
    }
}

/** The four buttons of the sketch. Only Languages does something today; More opens a menu of placeholders. */
@Composable
private fun Actions(onOpenLanguages: () -> Unit, modifier: Modifier = Modifier) {
    var more by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp).testTag("home_actions")) {
        // TODO(X13): styles of my own (Δ4, experimental). No action until then.
        Action(AppIcons.AddStyle, R.string.home_add_styles, onClick = {}, Modifier.weight(1f).testTag("home_add_styles"))
        // TODO(X13): editing the profiles (Δ4, experimental). No action until then.
        Action(AppIcons.Edit, R.string.home_edit, onClick = {}, Modifier.weight(1f).testTag("home_edit"))
        Action(AppIcons.Languages, R.string.home_languages, onClick = onOpenLanguages, Modifier.weight(1f).testTag("home_languages"))
        Box(Modifier.weight(1f)) {
            Action(AppIcons.More, R.string.home_more, onClick = { more = true }, Modifier.fillMaxWidth().testTag("home_more"))
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                // TODO(later): the options of More, when Victor decides them. One placeholder until then.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_more_soon)) },
                    onClick = { more = false },
                    modifier = Modifier.testTag("home_more_item"),
                )
            }
        }
    }
}

/** A round icon with its label below, one touch target for both. */
@Composable
private fun Action(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The last dictations (Δ22), as rows of the History, and «Όλες» → History. */
@Composable
private fun Recent(
    rows: List<DictationRowUi>,
    showEmpty: Boolean,
    onOpenHistory: () -> Unit,
    onOpenDictation: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("home_recent")) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_recent),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onOpenHistory, modifier = Modifier.testTag("home_all")) { Text(stringResource(R.string.home_all)) }
        }
        if (showEmpty && rows.isEmpty()) EmptyState(stringResource(R.string.history_empty))
        for (row in rows) {
            key(row.id) { DictationRow(row, onClick = { onOpenDictation(row.id) }) }
        }
    }
}

@Composable
private fun ScreenPreview(state: HomeState) {
    PestoTheme {
        HomeScreen(
            state,
            onOpenSettings = {},
            onOpenSearch = {},
            onOpenAnalytics = {},
            onOpenApps = {},
            onOpenLanguages = {},
            onOpenHistory = {},
            onOpenDictation = {},
        )
    }
}

private val sampleTotals = Totals(
    dictations = 128, dictationsToday = 6, words = 9_412, wordsToday = 310, wordsThisWeek = 1_984,
    speechMinutes = 71.5, minutesSaved = 163.8, wpm = 131.6,
)

private val sampleApps = listOf("WhatsApp", "Gmail", "Slack").mapIndexed { i, name -> AppDisplay("app.$i", name, null) }

@Preview
@Composable
private fun HomeEmptyPreview() {
    ScreenPreview(HomeState(loading = false))
}

@Preview(heightDp = 900)
@Composable
private fun HomePreview() {
    val recent = List(5) { i -> DictationRowUi(i + 1L, sampleApps[i % 3], System.currentTimeMillis() - i * 7_200_000L, 20 + i * 9) }
    ScreenPreview(HomeState(sampleTotals, sampleApps, recent, loading = false))
}

@Preview(heightDp = 900, fontScale = 1.5f)
@Composable
private fun HomeLongGreekPreview() {
    val long = AppDisplay("gr.example", "Εφαρμογή με πάρα πολύ μεγάλο ελληνικό όνομα που δεν χωράει", null)
    val recent = List(5) { i -> DictationRowUi(i + 1L, long, System.currentTimeMillis() - i * 86_400_000L, 12_345) }
    val huge = sampleTotals.copy(wordsThisWeek = 1_234_567, dictations = 98_765, minutesSaved = 54_321.0)
    ScreenPreview(HomeState(huge, List(3) { long }, recent, loading = false))
}
