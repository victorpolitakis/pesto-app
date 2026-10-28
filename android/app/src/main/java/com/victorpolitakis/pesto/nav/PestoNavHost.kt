package com.victorpolitakis.pesto.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.permissions.Requirement
import com.victorpolitakis.pesto.ui.AppIcons
import com.victorpolitakis.pesto.ui.analytics.AnalyticsScreen
import com.victorpolitakis.pesto.ui.apps.AppsScreen
import com.victorpolitakis.pesto.ui.dictionary.DictionaryScreen
import com.victorpolitakis.pesto.ui.history.HistoryDetailRoute
import com.victorpolitakis.pesto.ui.history.HistoryRoute
import com.victorpolitakis.pesto.ui.home.HomeScreen
import com.victorpolitakis.pesto.ui.profiles.ProfileDetailScreen
import com.victorpolitakis.pesto.ui.profiles.ProfilesScreen
import com.victorpolitakis.pesto.ui.search.SearchRoute
import com.victorpolitakis.pesto.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** The undo of a deletion, in the snackbar of the app (Δ15): it stays when the screen closes. */
fun interface UndoSnackbar {
    fun show(message: String, undo: suspend () -> Unit)
}

/** The four tabs of the bottom bar, in the order of the sketch (Δ20). */
enum class Tab(val route: Any, @param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    HOME(Routes.Home, R.string.tab_home, AppIcons.Home),
    PROFILES(Routes.Profiles, R.string.tab_profiles, AppIcons.Profiles),
    DICTIONARY(Routes.Dictionary, R.string.tab_dictionary, AppIcons.Dictionary),
    HISTORY(Routes.History, R.string.tab_history, AppIcons.History),
}

/**
 * The navigation of the app (Δ10, Δ20): the bottom bar only on the four tabs;
 * the other screens open without it, with «back». [openSettings]: C3 opens a
 * section of Settings (the pairing, Δ24). [destinations]: what each route
 * shows; the JVM tests give placeholders, since they have no Hilt.
 * TODO(X7): the fingerprint (BiometricPrompt, androidx.biometric) before History, HistoryDetail and Search.
 */
@Composable
fun PestoNavHost(
    openSettings: Flow<SettingsSection>,
    onRequest: (Requirement) -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    destinations: NavGraphBuilder.(NavHostController, UndoSnackbar) -> Unit = { nav, undo ->
        pestoDestinations(nav, undo, onRequest)
    },
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.undo)
    val undo = remember(snackbar, scope, undoLabel) {
        UndoSnackbar { message, action ->
            scope.launch {
                val result = snackbar.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) action()
            }
        }
    }
    LaunchedEffect(openSettings, navController) {
        openSettings.collect { section ->
            navController.navigate(Routes.Settings(section.name)) { launchSingleTop = true }
        }
    }
    val entry by navController.currentBackStackEntryAsState()
    val current = Tab.entries.firstOrNull { tab ->
        entry?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
    }
    Scaffold(
        modifier = modifier,
        bottomBar = { if (current != null) BottomBar(current, onSelect = navController::openTab) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        NavHost(navController, startDestination = Routes.Home, modifier = Modifier.padding(padding)) {
            destinations(navController, undo)
        }
    }
}

/** The real screens, each with its own state. */
fun NavGraphBuilder.pestoDestinations(nav: NavHostController, undo: UndoSnackbar, onRequest: (Requirement) -> Unit) {
    composable<Routes.Home> {
        HomeScreen(
            onOpenSettings = { nav.navigate(Routes.Settings()) },
            onOpenSearch = { nav.navigate(Routes.Search) },
            onOpenAnalytics = { nav.navigate(Routes.Analytics) },
            onOpenApps = { nav.navigate(Routes.Apps) },
        )
    }
    composable<Routes.Profiles> { ProfilesScreen(onOpenProfile = { id -> nav.navigate(Routes.ProfileDetail(id)) }) }
    composable<Routes.Dictionary> { DictionaryScreen() }
    composable<Routes.History> { HistoryRoute(onOpenDictation = { id -> nav.navigate(Routes.HistoryDetail(id)) }) }
    composable<Routes.Settings> { entry ->
        SettingsScreen(section = entry.toRoute<Routes.Settings>().section, onBack = { nav.popBackStack() })
    }
    composable<Routes.Search> {
        SearchRoute(onBack = { nav.popBackStack() }, onOpenDictation = { id -> nav.navigate(Routes.HistoryDetail(id)) })
    }
    composable<Routes.Analytics> { AnalyticsScreen(onBack = { nav.popBackStack() }) }
    composable<Routes.Apps> { AppsScreen(onBack = { nav.popBackStack() }) }
    composable<Routes.ProfileDetail> { entry ->
        ProfileDetailScreen(id = entry.toRoute<Routes.ProfileDetail>().id, onBack = { nav.popBackStack() })
    }
    composable<Routes.HistoryDetail> {
        val deleted = stringResource(R.string.history_deleted)
        HistoryDetailRoute(
            onBack = { nav.popBackStack() },
            onDeleted = { restore ->
                nav.popBackStack()
                undo.show(deleted, restore)
            },
        )
    }
}

/** A tab keeps its own back stack, and the start (Home) stays under the others. */
fun NavHostController.openTab(tab: Tab) = navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier.testTag("bottom_bar")) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                modifier = Modifier.testTag("tab_${tab.name.lowercase()}"),
            )
        }
    }
}
