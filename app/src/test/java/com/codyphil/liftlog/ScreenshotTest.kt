package com.codyphil.liftlog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import com.codyphil.liftlog.ui.components.PlateCalculatorDialog
import com.codyphil.liftlog.ui.progress.BodyScreen
import com.codyphil.liftlog.ui.progress.ProgressScreen
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
import com.github.takahirom.roborazzi.captureScreenRoboImage
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
        repo.createFromTemplate(Templates.all.first { it.name == "Push / Pull / Legs" })
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
                    if (set.setType == com.codyphil.liftlog.data.SetType.WARMUP) {
                        r.completeSet(set.id, set.weightKg, set.reps); return@forEach
                    }
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
        // One previous push day so the logger shows "Last time" and "Prev".
        run {
            val clock = FakeClock(now - 3 * day)
            val r = com.codyphil.liftlog.data.WorkoutRepository(app.container.database, clock.fn)
            val s = r.startFromDay(pushDayId)
            val weightsLb = listOf(180.0, 105.0, 50.0, 20.0, 50.0)
            r.observeSessionDetail(s).first()!!.exercises.forEachIndexed { idx, ex ->
                ex.sets.filter { it.setType != com.codyphil.liftlog.data.SetType.WARMUP }.forEach { set ->
                    clock.advanceSeconds(120)
                    r.completeSet(set.id, weightsLb[idx] * 0.45359237, if (idx >= 3) 12 else ex.row.targetReps ?: 8)
                }
            }
            r.finishSession(s)
        }
        // Body weight over the same six weeks.
        listOf(186.4, 185.8, 185.0, 184.6, 183.2, 183.6, 182.4).forEachIndexed { i, lbs ->
            repo.addBodyEntry(com.codyphil.liftlog.data.BodyMetric.BODY_WEIGHT, lbs * 0.45359237, now - (42 - i * 7) * day)
        }
        Unit
    }

    private fun waitForText(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
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
        waitForText("Recent")
        shot("01_home")
    }

    private fun startPushWorkout() = runBlocking {
        app.container.settings.update { it.copy(trackRpe = true) }
        val s = app.container.startFromDay(pushDayId)
        val ex = repo.observeSessionDetail(s).first()!!.exercises
        val bench = ex[0].sets
        bench.filter { it.setType == com.codyphil.liftlog.data.SetType.WARMUP }.forEach { repo.completeSet(it.id, it.weightKg, it.reps) }
        val firstWorking = bench.first { it.setType == com.codyphil.liftlog.data.SetType.NORMAL }
        repo.completeSet(firstWorking.id, firstWorking.weightKg, 6)
        repo.setSetRpe(firstWorking.id, 8.0)
        s
    }

    @Test fun activeWorkout() {
        startPushWorkout()
        RestTimer.start(app, 150, "Next: Bench Press · set 2")
        compose.setContent { Frame { ActiveWorkoutScreen(onBack = {}, onFinished = {}, onOpenExercise = {}) } }
        waitForText("Last time")
        compose.waitUntil(30_000) { compose.onAllNodesWithText("you hit every rep", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shot("02_active_workout")
        RestTimer.skip()
    }

    @Test fun supersetInWorkout() {
        startPushWorkout()
        compose.setContent { Frame { ActiveWorkoutScreen(onBack = {}, onFinished = {}, onOpenExercise = {}) } }
        waitForText("Last time")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        waitForText("Superset A")
        shot("08_superset_workout")
    }

    @Test fun progressOverview() {
        runBlocking { startPushWorkout() }
        compose.setContent { Frame { ProgressScreen(onOpenExercise = {}, onOpenBody = {}) } }
        waitForText("Working sets per muscle")
        waitForText("182.4 lb")
        shot("10_progress")
    }

    @Test fun body() {
        compose.setContent { Frame { BodyScreen(onBack = {}) } }
        waitForText("History")
        shot("11_body")
    }

    @Test fun programDay() {
        compose.setContent { Frame { DayEditorScreen(pushDayId, onBack = {}, onStartWorkout = {}) } }
        waitForText("Linear")
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
