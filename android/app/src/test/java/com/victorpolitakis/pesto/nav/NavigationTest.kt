package com.victorpolitakis.pesto.nav

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorpolitakis.pesto.ui.theme.PestoTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The bottom bar, the routes (Δ20) and the undo snackbar (Δ15), with placeholders in place of the screens (no Hilt here). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController
    private var restored = 0

    private fun show(openSettings: Flow<SettingsSection> = emptyFlow()) {
        rule.setContent {
            PestoTheme {
                nav = rememberNavController()
                PestoNavHost(openSettings, onRequest = {}, navController = nav) { controller, undo -> placeholders(controller, undo) }
            }
        }
    }

    @Composable
    private fun Placeholder(tag: String, onClick: () -> Unit = {}) {
        Text(tag, Modifier.testTag(tag).clickable(onClick = onClick))
    }

    private fun NavGraphBuilder.placeholders(controller: NavHostController, undo: UndoSnackbar) {
        composable<Routes.Home> { Placeholder("route_home") { controller.navigate(Routes.Analytics) } }
        composable<Routes.Profiles> { Placeholder("route_profiles") { controller.navigate(Routes.ProfileDetail("mail")) } }
        composable<Routes.Dictionary> { Placeholder("route_dictionary") }
        composable<Routes.History> { Placeholder("route_history") { controller.navigate(Routes.HistoryDetail(7)) } }
        composable<Routes.Settings> { Placeholder("route_settings") }
        composable<Routes.Search> { Placeholder("route_search") }
        composable<Routes.Analytics> { Placeholder("route_analytics") }
        composable<Routes.Apps> { Placeholder("route_apps") }
        composable<Routes.ProfileDetail> { Placeholder("route_profile_detail") }
        composable<Routes.HistoryDetail> {
            // As the real screen: it closes, and the snackbar of the app offers the undo.
            Placeholder("route_history_detail") {
                controller.popBackStack()
                undo.show("deleted") { restored++ }
            }
        }
    }

    @Test
    fun `the app starts on Home, with the bottom bar`() {
        show()
        rule.onNodeWithTag("route_home").assertIsDisplayed()
        rule.onNodeWithTag("bottom_bar").assertIsDisplayed()
        rule.onNodeWithTag("tab_home").assertIsSelected()
    }

    @Test
    fun `the four tabs open their screens`() {
        show()
        for (tab in listOf("profiles", "dictionary", "history", "home")) {
            rule.onNodeWithTag("tab_$tab").performClick()
            rule.onNodeWithTag("route_$tab").assertIsDisplayed()
            rule.onNodeWithTag("tab_$tab").assertIsSelected()
        }
    }

    @Test
    fun `the screens off the tabs have no bottom bar, and back returns`() {
        show()
        rule.onNodeWithTag("route_home").performClick()
        rule.onNodeWithTag("route_analytics").assertIsDisplayed()
        rule.onNodeWithTag("bottom_bar").assertDoesNotExist()

        rule.runOnUiThread { nav.popBackStack() }
        rule.onNodeWithTag("route_home").assertIsDisplayed()
        rule.onNodeWithTag("bottom_bar").assertIsDisplayed()
    }

    @Test
    fun `a detail opens from its tab, with its argument`() {
        show()
        rule.onNodeWithTag("tab_history").performClick()
        rule.onNodeWithTag("route_history").performClick()
        rule.onNodeWithTag("route_history_detail").assertIsDisplayed()
        rule.runOnIdle { assertEquals(7L, nav.currentBackStackEntry!!.toRoute<Routes.HistoryDetail>().id) }
        rule.onNodeWithTag("bottom_bar").assertDoesNotExist()
    }

    @Test
    fun `a deletion is undone from the snackbar, after its screen closed`() {
        show()
        rule.onNodeWithTag("tab_history").performClick()
        rule.onNodeWithTag("route_history").performClick()
        rule.onNodeWithTag("route_history_detail").performClick()
        rule.onNodeWithTag("route_history").assertIsDisplayed()
        rule.onNodeWithText("deleted").assertIsDisplayed()
        rule.onNodeWithText("Αναίρεση").performClick()
        rule.runOnIdle { assertEquals(1, restored) }
        rule.onNodeWithText("deleted").assertDoesNotExist()
    }

    @Test
    fun `without the undo, the deletion stays`() {
        show()
        rule.onNodeWithTag("tab_history").performClick()
        rule.onNodeWithTag("route_history").performClick()
        rule.onNodeWithTag("route_history_detail").performClick()
        rule.mainClock.advanceTimeBy(10_000)
        rule.onNodeWithText("deleted").assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, restored) }
    }

    @Test
    fun `C3 opens the backend section of Settings for the pairing`() {
        show(openSettings = flowOf(SettingsSection.BACKEND))
        rule.onNodeWithTag("route_settings").assertIsDisplayed()
        rule.runOnIdle {
            assertTrue(nav.currentDestination!!.hasRoute<Routes.Settings>())
            assertEquals("BACKEND", nav.currentBackStackEntry!!.toRoute<Routes.Settings>().section)
        }
    }
}
