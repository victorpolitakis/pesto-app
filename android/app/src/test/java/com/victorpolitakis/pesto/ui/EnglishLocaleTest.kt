package com.victorpolitakis.pesto.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.data.AppDisplay
import com.victorpolitakis.pesto.data.DayCount
import com.victorpolitakis.pesto.data.DictationDetail
import com.victorpolitakis.pesto.data.DictionaryRepository
import com.victorpolitakis.pesto.data.KeyCount
import com.victorpolitakis.pesto.data.ProfilesCatalog
import com.victorpolitakis.pesto.data.Snippet
import com.victorpolitakis.pesto.data.Term
import com.victorpolitakis.pesto.data.Totals
import com.victorpolitakis.pesto.nav.SettingsSection
import com.victorpolitakis.pesto.overlay.ListeningPanel
import com.victorpolitakis.pesto.permissions.Requirement
import com.victorpolitakis.pesto.ui.analytics.AnalyticsScreen
import com.victorpolitakis.pesto.ui.analytics.AnalyticsState
import com.victorpolitakis.pesto.ui.analytics.AppCount
import com.victorpolitakis.pesto.ui.apps.AppRow
import com.victorpolitakis.pesto.ui.apps.AppsScreen
import com.victorpolitakis.pesto.ui.apps.AppsState
import com.victorpolitakis.pesto.ui.components.DictationRowUi
import com.victorpolitakis.pesto.ui.dictionary.DictionaryScreen
import com.victorpolitakis.pesto.ui.dictionary.DictionaryState
import com.victorpolitakis.pesto.ui.history.HistoryDetailScreen
import com.victorpolitakis.pesto.ui.history.HistoryDetailState
import com.victorpolitakis.pesto.ui.history.HistoryScreen
import com.victorpolitakis.pesto.ui.history.HistoryState
import com.victorpolitakis.pesto.ui.home.HomeScreen
import com.victorpolitakis.pesto.ui.home.HomeState
import com.victorpolitakis.pesto.ui.profiles.ProfileAppUi
import com.victorpolitakis.pesto.ui.profiles.ProfileDetailScreen
import com.victorpolitakis.pesto.ui.profiles.ProfileDetailState
import com.victorpolitakis.pesto.ui.profiles.ProfileRow
import com.victorpolitakis.pesto.ui.profiles.ProfilesScreen
import com.victorpolitakis.pesto.ui.profiles.ProfilesState
import com.victorpolitakis.pesto.ui.search.SearchHit
import com.victorpolitakis.pesto.ui.search.SearchScreen
import com.victorpolitakis.pesto.ui.search.SearchState
import com.victorpolitakis.pesto.ui.settings.BackendStatus
import com.victorpolitakis.pesto.ui.settings.PairingStatus
import com.victorpolitakis.pesto.ui.settings.RequirementStatus
import com.victorpolitakis.pesto.ui.settings.SettingsActions
import com.victorpolitakis.pesto.ui.settings.SettingsScreen
import com.victorpolitakis.pesto.ui.settings.SettingsState
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The phone in English (robolectric.properties runs the rest of the suite in Greek): a few
 * English texts on the main screens, and the layout and accessibility checks of levels 2 and 3
 * on every screen, because English texts are not as long as Greek ones, and not as short either.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en")
class EnglishLocaleTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val whatsapp = AppDisplay("com.whatsapp", "WhatsApp", null)
    private val long = AppDisplay("example.long", "An app with a very, very long name that does not fit in one line", null)

    private fun show(content: @Composable () -> Unit) {
        rule.setContent { PestoTheme { content() } }
    }

    private fun checks() {
        rule.assertLayout()
        rule.assertAccessible()
    }

    /**
     * For a short text centered in a box wider than it («Nothing found»): assertLayout reads the right
     * edge of its line in the width of the box and compares it with the width of the text, and says
     * "wider than its box" for it in Greek too. So these run level 3 only.
     */
    private fun accessibleOnly() {
        rule.assertAccessible()
    }

    // Home

    private val homeState = HomeState(
        totals = Totals(
            dictations = 12, dictationsToday = 2, words = 900, wordsToday = 80, wordsThisWeek = 400,
            speechMinutes = 6.0, minutesSaved = 16.5, wpm = 150.0,
        ),
        topApps = listOf(whatsapp),
        recent = List(5) { i -> DictationRowUi(i + 1L, whatsapp, System.currentTimeMillis(), 10) },
        loading = false,
    )

    private fun home(s: HomeState) = show {
        HomeScreen(s, {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun `Home speaks English`() {
        home(homeState)
        rule.onNodeWithTag("home_page_minutes_saved")
            .assertIsDisplayed()
            .assertTextContains("Minutes saved")
            .assertTextContains("compared with typing at 40 words/minute")
        rule.onNodeWithTag("home_dots").assertContentDescriptionEquals("Page 1 of 4")
        rule.onNodeWithTag("home_apps").assertTextContains("Apps")
        rule.onNodeWithTag("home_recent").performScrollToNode(hasTestTag("dictation_1")).assertExists()
    }

    @Test
    fun `Home passes levels 2 and 3 in English, with long names and large numbers`() {
        home(
            homeState.copy(
                totals = homeState.totals.copy(minutesSaved = 54_321.0, wordsThisWeek = 1_234_567, dictations = 98_765),
                topApps = List(3) { long },
                recent = List(5) { i -> DictationRowUi(i + 1L, long, System.currentTimeMillis(), 12_345) },
            ),
        )
        checks()
        rule.assertCenteredOnScreen("home_value")
    }

    @Test
    fun `an empty Home passes levels 2 and 3 in English`() {
        home(HomeState(loading = false))
        checks()
    }

    // Settings

    private val settingsState = SettingsState(
        permissions = listOf(
            RequirementStatus(Requirement.MICROPHONE, granted = true),
            RequirementStatus(Requirement.ACCESSIBILITY, granted = false),
            RequirementStatus(Requirement.BATTERY, granted = true),
        ),
        backendUrl = "ws://127.0.0.1:8000/v1/dictate",
        backend = BackendStatus.ANSWERS,
        paired = false,
        dictations = 3,
        version = "0.3.0 (1)",
        buildType = "debug",
        loading = false,
    )

    private val noActions = SettingsActions()

    private fun settings(s: SettingsState, section: SettingsSection? = null, code: String = "") = show {
        SettingsScreen(s, section, code, onCodeChange = {}, actions = noActions, onBack = {})
    }

    private fun scrollTo(tag: String) = rule.onNodeWithTag("settings").performScrollToNode(hasTestTag(tag))

    @Test
    fun `Settings speaks English`() {
        settings(settingsState)
        rule.onNodeWithTag("primary_language").assertTextContains("Greek")
        rule.onNodeWithTag("secondary_language").assertTextContains("English")
        scrollTo("permission_accessibility")
        rule.onNodeWithTag("permission_accessibility").assertTextContains("Missing")
        rule.onNodeWithTag("permission_microphone").assertTextContains("OK")
        scrollTo("overlay_application")
        rule.onNodeWithTag("overlay_application").assertTextContains("permanent notification", substring = true)
        scrollTo("delete_all")
        rule.onNodeWithTag("history_size").assertTextContains("3 dictations")
        rule.onNodeWithText("Delete all History").assertExists()
    }

    @Test
    fun `the backend section speaks English`() {
        settings(settingsState, section = SettingsSection.BACKEND)
        rule.onNodeWithTag("backend_status").assertTextContains("Responding")
        rule.onNodeWithTag("paired").assertTextContains("No")
    }

    @Test
    fun `Settings passes levels 2 and 3 in English, at the top, the pairing and the end`() {
        settings(settingsState.copy(isXiaomi = true, pairing = PairingStatus.WRONG_CODE), code = "1234")
        checks()
        rule.assertLeftAligned("primary_language", "secondary_language")
        scrollTo("pair")
        checks()
        scrollTo("version")
        checks()
    }

    @Test
    fun `a paired phone passes levels 2 and 3 in English`() {
        settings(settingsState.copy(paired = true, backend = BackendStatus.NO_ANSWER), section = SettingsSection.BACKEND)
        checks()
        scrollTo("unpair")
        checks()
    }

    // Profiles

    private val profileRows = ProfilesCatalog.all.map { p ->
        if (p.id == "mail") ProfileRow(p, isDefault = true, apps = 2, dictations = 14) else ProfileRow(p, false, apps = 0, dictations = 1)
    }

    @Test
    fun `Profiles speaks English and passes levels 2 and 3`() {
        show { ProfilesScreen(ProfilesState(profileRows, loading = false), onOpenProfile = {}) }
        rule.onNodeWithTag("profile_raw")
            .assertTextContains("No changes")
            .assertTextContains("The text as the speech recognition wrote it, without any processing.")
            .assertTextContains("0 apps · 1 dictation")
        rule.onNodeWithTag("profile_list").performScrollToNode(hasTestTag("profile_mail"))
        rule.onNodeWithTag("profile_mail")
            .assertTextContains("Default")
            .assertTextContains("2 apps · 14 dictations")
        checks()
    }

    @Test
    fun `Profiles with large numbers passes levels 2 and 3 in English`() {
        show {
            ProfilesScreen(ProfilesState(profileRows.map { it.copy(apps = 1234, dictations = 56789) }, loading = false), onOpenProfile = {})
        }
        checks()
        rule.assertLeftAligned("profile_raw", "profile_note")
    }

    @Test
    fun `Profile detail speaks English and passes levels 2 and 3`() {
        val chatter = ProfilesCatalog.find("chatter")!!
        val apps = List(6) { i -> ProfileAppUi(AppDisplay("example.app$i", "${long.name} $i", null), i % 2 == 0) }
        show { ProfileDetailScreen(ProfileDetailState(chatter, dictations = 3, apps = apps, loading = false), onBack = {}, onSetDefault = {}) }
        rule.onNodeWithTag("profile_dictations").assertTextContains("3 dictations with this profile")
        rule.onNodeWithTag("set_default").assertTextContains("Set as default")
        checks()
    }

    @Test
    fun `Profile detail without apps passes levels 2 and 3 in English`() {
        val chatter = ProfilesCatalog.find("chatter")!!
        show { ProfileDetailScreen(ProfileDetailState(chatter, isDefault = true, dictations = 1, loading = false), onBack = {}, onSetDefault = {}) }
        rule.onNodeWithTag("is_default").assertTextContains("This is the default")
        rule.onNodeWithTag("no_apps").assertIsDisplayed()
        checks()
    }

    // Dictionary

    @Test
    fun `Dictionary speaks English and passes levels 2 and 3`() {
        val terms = listOf("Pesto", "Politakis", "A very long term with many words that does not fit in one line at all").mapIndexed { i, t -> Term(i + 1L, t) }
        show { dictionary(DictionaryState(terms, DictionaryRepository.remaining(terms.map { it.term }), loading = false)) }
        rule.onNodeWithTag("term_count").assertTextContains("3 terms")
        rule.onNodeWithTag("term_remaining").assertTextContains("characters left", substring = true)
        checks()
        rule.assertLeftAligned("term_1", "term_2")
    }

    @Test
    fun `an empty Dictionary passes levels 2 and 3 in English`() {
        show { dictionary(DictionaryState(loading = false)) }
        rule.onNodeWithText("No terms yet", substring = true).assertIsDisplayed()
        checks()
    }

    @Composable
    private fun dictionary(state: DictionaryState) =
        DictionaryScreen(state, onAdd = {}, onEdit = {}, onDelete = {}, onType = {}, onSave = {}, onDismiss = {})

    // History, its detail, Search

    private fun rows(count: Int, app: AppDisplay) =
        List(count) { i -> DictationRowUi(i + 1L, app, System.currentTimeMillis() - i * 3_600_000L, 56) }

    @Test
    fun `History speaks English and passes levels 2 and 3`() {
        show { HistoryScreen(HistoryState(rows(6, long), total = 6, loading = false), onOpenDictation = {}, onLoadMore = {}) }
        rule.onNodeWithText("6 dictations").assertIsDisplayed()
        rule.onNodeWithTag("dictation_1").assertTextContains("Today", substring = true).assertTextContains("56 words")
        checks()
        rule.assertLeftAligned("dictation_1", "dictation_2", "dictation_3")
    }

    @Test
    fun `an empty History passes levels 2 and 3 in English`() {
        show { HistoryScreen(HistoryState(loading = false), onOpenDictation = {}, onLoadMore = {}) }
        rule.onNodeWithText("No dictations yet", substring = true).assertIsDisplayed()
        checks()
    }

    private val detail = DictationDetail(
        id = 1, text = "Hello George, the meeting is on Thursday at 10:00.", rawText = "hello george the meeting is on thursday at ten",
        packageName = "com.google.android.gm", inputType = "text", profile = "mail", startedAt = 1_000_000, createdAt = 1_007_500,
        audioS = 6.0, words = 3, outputWords = 2, fallbackReason = null, endReason = "user", insertMethod = "COMMIT_TEXT",
        sttMs = 152, llmMs = 1496, totalMs = 1648,
    )

    @Test
    fun `History detail speaks English and passes levels 2 and 3`() {
        show {
            HistoryDetailScreen(
                HistoryDetailState(detail.copy(text = "Hello everyone, thank you for your patience. ".repeat(20).trim()), AppDisplay("com.google.android.gm", "Gmail", null), loading = false),
                onBack = {},
                onCopy = {},
                onDelete = {},
            )
        }
        rule.onNodeWithTag("copy").assertTextContains("Copy")
        rule.onNodeWithTag("delete").assertTextContains("Delete")
        checks()
        rule.assertLeftAligned("dictation_text", "record")
        rule.onNodeWithTag("field_profile").performScrollTo().assertTextContains("Email (mail)")
    }

    @Test
    fun `a deleted dictation passes levels 2 and 3 in English`() {
        show { HistoryDetailScreen(HistoryDetailState(null, null, loading = false), onBack = {}, onCopy = {}, onDelete = {}) }
        rule.onNodeWithText("This dictation no longer exists").assertIsDisplayed()
        accessibleOnly()
    }

    private val hit = SearchHit(
        DictationRowUi(4, whatsapp, System.currentTimeMillis(), 9),
        Snippet("Hello George, the meeting is on Thursday", listOf(0..4)),
    )

    @Test
    fun `Search speaks English and passes levels 2 and 3`() {
        val longHit = SearchHit(DictationRowUi(5, long, System.currentTimeMillis(), 12_345), Snippet("Hello everyone, ".repeat(12), listOf(0..4)))
        show { SearchScreen("hello", SearchState("hello", listOf(hit, longHit)), onQueryChange = {}, onBack = {}, onOpenDictation = {}) }
        rule.onNodeWithText("2 results").assertIsDisplayed()
        checks()
    }

    @Test
    fun `Search hints pass levels 2 and 3 in English`() {
        show { SearchScreen("h", SearchState(), onQueryChange = {}, onBack = {}, onOpenDictation = {}) }
        rule.onNodeWithText("Type at least 2 letters").assertIsDisplayed()
        checks()
    }

    @Test
    fun `Search with nothing found passes levels 2 and 3 in English`() {
        show { SearchScreen("tomorrow", SearchState("tomorrow"), onQueryChange = {}, onBack = {}, onOpenDictation = {}) }
        rule.onNodeWithText("Nothing found").assertIsDisplayed()
        accessibleOnly()
    }

    // Apps, Analytics, the listening panel

    @Test
    fun `Apps speaks English and passes levels 2 and 3`() {
        val rows = List(6) { i ->
            AppRow(long.copy(packageName = "example.app$i", name = "${long.name} $i"), 12_345, System.currentTimeMillis(), if (i % 2 == 0) null else "professional")
        }
        show { AppsScreen(AppsState(listOf(AppRow(whatsapp, 1, System.currentTimeMillis(), "chatter")) + rows, defaultProfile = "mail", loading = false), onBack = {}, onChoose = {}, onSetProfile = {}, onDismiss = {}) }
        rule.onNodeWithTag("app_com.whatsapp").assertTextContains("1 dictation · last: Today", substring = true).assertTextContains("Chat")
        rule.onNodeWithTag("app_example.app0").assertTextContains("Automatic (Email)")
        checks()
    }

    @Test
    fun `no apps passes levels 2 and 3 in English`() {
        show { AppsScreen(AppsState(loading = false), onBack = {}, onChoose = {}, onSetProfile = {}, onDismiss = {}) }
        rule.onNodeWithText("No apps yet", substring = true).assertIsDisplayed()
        checks()
    }

    // Wednesday 28/10/2026 last, with 120 words in 3 dictations.
    private val days = List(AnalyticsState.MONTH) { i ->
        val last = i == AnalyticsState.MONTH - 1
        DayCount(LocalDate.of(2026, 10, 28).minusDays((AnalyticsState.MONTH - 1 - i).toLong()), if (last) 3 else 1, if (last) 120 else 10)
    }

    private val analyticsState = AnalyticsState(
        totals = Totals(dictations = 12, words = 900, speechMinutes = 6.0, minutesSaved = 16.5, wpm = 150.0),
        days = days,
        apps = listOf(AppCount(long, 12_345)),
        profiles = listOf(KeyCount("mail", 12)),
        fallbackReasons = listOf(KeyCount(null, 10), KeyCount("timeout", 2)),
        endReasons = listOf(KeyCount("user", 11), KeyCount("connection_lost", 1)),
        insertMethods = listOf(KeyCount("COMMIT_TEXT", 12)),
        loading = false,
    )

    @Test
    fun `Analytics speaks English with the English names of the days`() {
        show { AnalyticsScreen(analyticsState, AnalyticsState.WEEK, onDaysChange = {}, onBack = {}) }
        rule.onNodeWithTag("total_saved").assertTextContains("compared with typing at 40 words/minute")
        rule.onNodeWithTag("analytics").performScrollToNode(hasTestTag("day_bars"))
        rule.onAllNodesWithTag("day_bar")[6].assertContentDescriptionEquals("Wednesday 28 Oct: 120 words, 3 dictations")
        rule.onNodeWithTag("analytics").performScrollToNode(hasTestTag("count_fallback_null"))
        rule.onNodeWithTag("count_fallback_null").assertTextContains("None: the profile was applied")
    }

    @Test
    fun `Analytics passes levels 2 and 3 in English, the week and the month`() {
        show { AnalyticsScreen(analyticsState, AnalyticsState.WEEK, onDaysChange = {}, onBack = {}) }
        checks()
    }

    @Test
    fun `Analytics of the month passes levels 2 and 3 in English`() {
        show { AnalyticsScreen(analyticsState, AnalyticsState.MONTH, onDaysChange = {}, onBack = {}) }
        checks()
    }

    @Test
    fun `an empty Analytics passes levels 2 and 3 in English`() {
        show { AnalyticsScreen(AnalyticsState(loading = false), AnalyticsState.WEEK, onDaysChange = {}, onBack = {}) }
        rule.onNodeWithTag("empty").assertIsDisplayed()
        checks()
    }

    @Test
    fun `the listening panel passes levels 2 and 3 in English`() {
        show {
            ListeningPanel(
                status = "Listening…",
                profile = "Professional",
                text = "Hello everyone, thank you for your patience. ".repeat(10),
                levels = List(24) { (it % 7) / 7f },
                paused = false,
                onCancel = {},
                onPauseResume = {},
                onFinish = {},
                modifier = Modifier.height(300.dp),
            )
        }
        checks()
    }
}
