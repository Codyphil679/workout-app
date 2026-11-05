package com.codyphil.liftlog.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codyphil.liftlog.AppContainer
import com.codyphil.liftlog.LiftLogApp
import com.codyphil.liftlog.ui.history.HistoryScreen
import com.codyphil.liftlog.ui.history.SessionDetailScreen
import com.codyphil.liftlog.ui.programs.DayEditorScreen
import com.codyphil.liftlog.ui.programs.ProgramDetailScreen
import com.codyphil.liftlog.ui.programs.ProgramsScreen
import com.codyphil.liftlog.ui.progress.ExerciseProgressScreen
import com.codyphil.liftlog.ui.progress.ProgressScreen
import com.codyphil.liftlog.ui.settings.SettingsScreen
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.train.TrainScreen
import com.codyphil.liftlog.ui.workout.ActiveWorkoutScreen

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as LiftLogApp).container

object Routes {
    const val TRAIN = "train"
    const val PROGRAMS = "programs"
    const val HISTORY = "history"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val WORKOUT = "workout"
    fun program(id: Long) = "program/$id"
    fun day(id: Long) = "day/$id"
    fun session(id: Long) = "session/$id"
    fun exercise(id: Long) = "exercise/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TRAIN, "Train", Icons.Rounded.Bolt),
    Tab(Routes.PROGRAMS, "Programs", Icons.Rounded.ViewAgenda),
    Tab(Routes.HISTORY, "History", Icons.Rounded.EventNote),
    Tab(Routes.PROGRESS, "Progress", Icons.Rounded.Insights),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

@Composable
fun LiftLogNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        containerColor = Brand.Ink,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = Brand.Panel, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Brand.Ink,
                                indicatorColor = Brand.Volt,
                                selectedTextColor = Brand.Volt,
                                unselectedIconColor = Brand.TextLow,
                                unselectedTextColor = Brand.TextLow,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.TRAIN, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Routes.TRAIN) {
                TrainScreen(
                    onOpenWorkout = { nav.navigate(Routes.WORKOUT) { launchSingleTop = true } },
                    onOpenPrograms = { nav.navigateTab(Routes.PROGRAMS) },
                    onOpenSession = { nav.navigate(Routes.session(it)) },
                )
            }
            composable(Routes.PROGRAMS) {
                ProgramsScreen(onOpenProgram = { nav.navigate(Routes.program(it)) })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenSession = { nav.navigate(Routes.session(it)) })
            }
            composable(Routes.PROGRESS) {
                ProgressScreen(onOpenExercise = { nav.navigate(Routes.exercise(it)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.WORKOUT) {
                ActiveWorkoutScreen(
                    onBack = { nav.popBackStack() },
                    onFinished = { sessionId ->
                        nav.navigate(Routes.session(sessionId)) { popUpTo(Routes.TRAIN) }
                    },
                    onOpenExercise = { nav.navigate(Routes.exercise(it)) },
                )
            }
            composable(
                "program/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                ProgramDetailScreen(
                    programId = entry.arguments!!.getLong("id"),
                    onBack = { nav.popBackStack() },
                    onOpenDay = { nav.navigate(Routes.day(it)) },
                    onStartWorkout = { nav.navigate(Routes.WORKOUT) { launchSingleTop = true } },
                )
            }
            composable("day/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                DayEditorScreen(
                    dayId = entry.arguments!!.getLong("id"),
                    onBack = { nav.popBackStack() },
                    onStartWorkout = { nav.navigate(Routes.WORKOUT) { launchSingleTop = true } },
                )
            }
            composable("session/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                SessionDetailScreen(
                    sessionId = entry.arguments!!.getLong("id"),
                    onBack = { nav.popBackStack() },
                    onOpenExercise = { nav.navigate(Routes.exercise(it)) },
                )
            }
            composable("exercise/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                ExerciseProgressScreen(
                    exerciseId = entry.arguments!!.getLong("id"),
                    onBack = { nav.popBackStack() },
                    onOpenSession = { nav.navigate(Routes.session(it)) },
                )
            }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Convenience for screen-level ViewModels that need the app container. */
@Composable
inline fun <reified VM : androidx.lifecycle.ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = appContainer()
    return viewModel(key = key) { create(container) }
}
