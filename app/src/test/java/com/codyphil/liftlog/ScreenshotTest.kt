package com.codyphil.liftlog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.codyphil.liftlog.data.Templates
import com.codyphil.liftlog.timer.RestTimer
import com.codyphil.liftlog.ui.LiftLogNav
import com.codyphil.liftlog.ui.history.HistoryScreen
import com.codyphil.liftlog.ui.history.SessionDetailScreen
import com.codyphil.liftlog.ui.programs.DayEditorScreen
import com.codyphil.liftlog.ui.progress.ExerciseProgressScreen
import com.codyphil.liftlog.ui.settings.SettingsScreen
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.theme.LiftLogTheme
import com.codyphil.liftlog.ui.workout.ActiveWorkoutScreen
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders real app screens with realistic sample data and saves PNGs to app/build/outputs/roborazzi/.
 * Run: ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h860dp-xhdpi", application = LiftLogApp::class)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<LiftLogApp>()
    private val repo get() = app.container.repository
    private var squatId = 0L
    private var lastSessionId = 0L
    private var pushDayId = 0L

    @Before fun seed() = runBlocking {
        RestTimer.skip()
        repo.seedIfEmpty()
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        repo.createFromTemplate(Templates.all[1]) // Push / Pull / Legs
        val days = repo.observeAllDays().first()
        pushDayId = days.first { it.name == "Push" }.id
        val legs = days.first { it.name == "Legs" }.id
        squatId = repo.getOrCreateExercise("Back Squat")

        // Six weeks of leg days with steady progress, finished in the past.
        val squatTop = listOf(185.0, 195.0, 205.0, 205.0, 215.0, 225.0)
        squatTop.forEachIndexed { i, lb ->
            val sessionStart = now - (42 - i * 7) * day
            val clock = FakeClock(sessionStart)
            val r = com.codyphil.liftlog.data.WorkoutRepository(app.container.database, clock.fn)
            val s = r.startFromDay(legs)
            val detail = r.observeSessionDetail(s).first()!!
            detail.exercises.forEachIndexed { exIdx, ex ->
                ex.sets.forEach { set ->
                    clock.advanceSeconds(if (exIdx == 0) 175 else 95)
                    val kg = when (exIdx) {
                        0 -> lb * 0.45359237
                        1 -> (lb - 40) * 0.45359237
                        2 -> (lb + 135) * 0.45359237
                        else -> 90 * 0.45359237
                    }
                    val reps = if (exIdx == 0 && set.setNumber == 4 && i % 2 == 0) 5 else (ex.row.targetReps ?: 8)
                    r.completeSet(set.id, kg, reps)
                }
            }
            clock.advanceSeconds(300)
            r.finishSession(s)
            lastSessionId = s
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        LiftLogTheme { Box(Modifier.fillMaxSize().background(Brand.Ink)) { content() } }
    }

    @Test fun home() {
        compose.setContent { LiftLogTheme { LiftLogNav() } }
        waitForText("Up next")
        shot("01_home")
    }

    @Test fun activeWorkout() {
        runBlocking {
            val s = repo.startFromDay(pushDayId)
            val ex = repo.observeSessionDetail(s).first()!!.exercises
            val kg = 185 * 0.45359237
            repo.completeSet(ex[0].sets[0].id, kg, 6)
            repo.completeSet(ex[0].sets[1].id, kg, 6)
            repo.updateSetValues(ex[0].sets[2].id, kg, 6)
            repo.updateSetValues(ex[0].sets[3].id, kg, 6)
            repo.updateSetValues(ex[1].sets[0].id, 115 * 0.45359237, 8)
        }
        RestTimer.start(app, 150, "Next: Bench Press · set 3")
        compose.setContent { Frame { ActiveWorkoutScreen(onBack = {}, onFinished = {}, onOpenExercise = {}) } }
        waitForText("Bench Press")
        shot("02_active_workout")
        RestTimer.skip()
    }

    @Test fun programDay() {
        compose.setContent { Frame { DayEditorScreen(pushDayId, onBack = {}, onStartWorkout = {}) } }
        waitForText("Incline")
        shot("03_program_day")
    }

    @Test fun history() {
        compose.setContent { Frame { HistoryScreen(onOpenSession = {}) } }
        waitForText("Legs")
        shot("04_history")
    }

    @Test fun sessionDetail() {
        compose.setContent { Frame { SessionDetailScreen(lastSessionId, onBack = {}, onOpenExercise = {}) } }
        waitForText("Back Squat")
        shot("05_workout_detail")
    }

    @Test fun exerciseProgress() {
        compose.setContent { Frame { ExerciseProgressScreen(squatId, onBack = {}, onOpenSession = {}) } }
        waitForText("Personal records")
        shot("06_exercise_progress")
    }

    @Test fun settings() {
        compose.setContent { Frame { SettingsScreen() } }
        waitForText("Weight unit")
        shot("07_settings")
    }
}
