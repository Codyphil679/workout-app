package com.codyphil.liftlog

import com.codyphil.liftlog.data.AppDatabase
import com.codyphil.liftlog.data.ExerciseSetRow
import com.codyphil.liftlog.data.Progression
import com.codyphil.liftlog.data.SetType
import com.codyphil.liftlog.data.WorkoutRepository
import com.codyphil.liftlog.domain.ProgressionConfig
import com.codyphil.liftlog.domain.Progressions
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.Warmups
import com.codyphil.liftlog.domain.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** One test per bug found in the full-app review, written to fail before the fix. */
@RunWith(RobolectricTestRunner::class)
class BugRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: WorkoutRepository
    private val clock = FakeClock()
    private val lb = WeightUnit.LB
    private fun kg(lbs: Double) = Units.toKg(lbs, lb)
    private fun show(kg: Double) = Units.format(kg, lb)

    @Before fun setUp() { db = inMemoryDb(); repo = WorkoutRepository(db, clock.fn) }
    @After fun tearDown() = db.close()

    private suspend fun percentDay(ex: Long): Long {
        val d = repo.addDay(repo.createProgram("P"), "A")
        repo.addDayExercise(d, ex, 3, 5, 0.0, 180, Progression.PERCENT, 0.0, null, false, percentScheme = "75x5,80x5,85x5+")
        return d
    }

    private suspend fun completeAll(s: Long, lastReps: Int) {
        repo.observeSessionDetail(s).first()!!.exercises.forEach { e ->
            e.sets.forEachIndexed { i, set -> clock.advanceSeconds(60); repo.completeSet(set.id, set.weightKg, if (i == e.sets.lastIndex) lastReps else set.reps) }
        }
    }

    @Test fun finishingTwice_doesNotBumpTrainingMaxTwice() = runTest {
        val squat = repo.getOrCreateExercise("Back Squat")
        repo.setTrainingMax(squat, kg(300.0))
        val s = repo.startFromDay(percentDay(squat), lb, 45.0)
        completeAll(s, lastReps = 7)
        assertTrue(repo.finishSession(s, lb))
        repo.finishSession(s, lb)
        assertEquals("310", show(repo.observeExercise(squat).first()!!.trainingMaxKg!!))
    }

    @Test fun trainingMaxIsNeverSeededAsZero() = runTest {
        val bench = repo.getOrCreateExercise("Bench Press")
        val q = repo.startEmpty()
        repo.addExerciseToSession(q, bench, 90)
        val set = repo.observeSessionDetail(q).first()!!.exercises.single().sets.single()
        repo.completeSet(set.id, 0.0, 5) // logged without a weight
        repo.finishSession(q)
        assertNull(repo.suggestedTrainingMax(bench, lb))
        repo.startFromDay(percentDay(bench), lb, 45.0)
        assertNull(repo.observeExercise(bench).first()!!.trainingMaxKg)
    }

    @Test fun sameExerciseTwice_updatesTrainingMaxOnce() = runTest {
        val bench = repo.getOrCreateExercise("Bench Press")
        repo.setTrainingMax(bench, kg(200.0))
        val d = repo.addDay(repo.createProgram("P"), "A")
        repeat(2) { repo.addDayExercise(d, bench, 3, 5, 0.0, 180, Progression.PERCENT, 0.0, null, false, percentScheme = "75x5,80x5,85x5+") }
        val s = repo.startFromDay(d, lb, 45.0)
        completeAll(s, lastReps = 6)
        repo.finishSession(s, lb)
        assertEquals("205", show(repo.observeExercise(bench).first()!!.trainingMaxKg!!))
    }

    @Test fun warmupOnlyWorkout_countsAsNothingLogged() = runTest {
        val s = repo.startEmpty()
        repo.addExerciseToSession(s, repo.getOrCreateExercise("Bench Press"), 90)
        val set = repo.observeSessionDetail(s).first()!!.exercises.single().sets.single()
        repo.setSetType(set.id, SetType.WARMUP)
        repo.completeSet(set.id, kg(45.0), 10)
        assertFalse(repo.finishSession(s))
    }

    @Test fun quickAddPrefill_ignoresLighterDays() = runTest {
        val squat = repo.getOrCreateExercise("Back Squat")
        val d = repo.addDay(repo.createProgram("P"), "A")
        repo.addDayExercise(d, squat, 1, 5, kg(200.0), 180, Progression.NONE)
        val normal = repo.startFromDay(d, lb, 45.0); completeAll(normal, 5); repo.finishSession(normal)
        clock.advanceSeconds(86_400)
        val light = repo.startFromDay(d, lb, 45.0, intensity = 0.9); completeAll(light, 5); repo.finishSession(light)
        clock.advanceSeconds(86_400)
        val q = repo.startEmpty()
        repo.addExerciseToSession(q, squat, 90)
        assertEquals("200", show(repo.observeSessionDetail(q).first()!!.exercises.single().sets.single().weightKg))
    }

    @Test fun warmupsForWeightsStoredInKg_arentFloatTruncated() {
        val work = Units.fromKg(kg(150.0), lb) // 149.99999999999997
        assertEquals(listOf(45.0 to 10, 60.0 to 5, 90.0 to 3, 120.0 to 2), Warmups.forWorkingWeight(work, 45.0, lb))
    }

    private fun row(w: Double, r: Int) = ExerciseSetRow(1, "W", 1, 1, kg(w), r)
    private val linear = ProgressionConfig(Progression.LINEAR, 3, 5, 5, 0.0, kg(5.0))

    @Test fun deloadNeverRoundsALightWeightToZero() {
        val fail = listOf(row(5.0, 3), row(5.0, 3), row(5.0, 3))
        val p = Progressions.plan(linear, listOf(fail, fail), lb)
        assertEquals("5", show(p.sets.first().first))
    }

    @Test fun linear_aHeavierSingleDoesNotBecomeTheWorkingWeight() {
        val p = Progressions.plan(linear, listOf(listOf(row(225.0, 5), row(225.0, 5), row(225.0, 5), row(245.0, 1))), lb)
        assertEquals("230", show(p.sets.first().first))
    }

    @Test fun doubleTapOnCheck_doesNotRecompleteTheSet() = runTest {
        val s = repo.startEmpty()
        repo.addExerciseToSession(s, repo.getOrCreateExercise("Bench Press"), 90)
        val set = repo.observeSessionDetail(s).first()!!.exercises.single().sets.single()
        assertEquals(90, repo.completeSet(set.id, kg(135.0), 5))
        val first = repo.observeSessionDetail(s).first()!!.exercises.single().sets.single().completedAt
        clock.advanceSeconds(1)
        assertNull(repo.completeSet(set.id, kg(135.0), 5)) // second tap: no rest restart
        assertEquals(first, repo.observeSessionDetail(s).first()!!.exercises.single().sets.single().completedAt)
    }

    @Test fun lateKeystroke_cannotUndoACompletedSet() = runTest {
        val s = repo.startEmpty()
        repo.addExerciseToSession(s, repo.getOrCreateExercise("Bench Press"), 90)
        val set = repo.observeSessionDetail(s).first()!!.exercises.single().sets.single()
        repo.completeSet(set.id, kg(135.0), 5)
        repo.updateSetValues(set.id, kg(13.0), 1) // stale edit arriving after the check mark
        val after = repo.observeSessionDetail(s).first()!!.exercises.single().sets.single()
        assertTrue(after.completed)
        assertEquals("135", show(after.weightKg))
        assertEquals(5, after.reps)
    }

    @Test fun numberInputValidation() {
        fun v(t: String, decimal: Boolean, max: Int) = com.codyphil.liftlog.ui.components.isValidNumberInput(t, decimal, max)
        assertTrue(v("102.5", true, 6)); assertTrue(v("102,5", true, 6)); assertTrue(v("", true, 6))
        assertFalse(v("1..5", true, 6)); assertFalse(v("2.5.", true, 6)); assertFalse(v("1,0.5", true, 6))
        assertFalse(v("٣٠", true, 6))        // non-ASCII digits would parse as nothing
        assertFalse(v("12.5", false, 6))     // reps: whole numbers only
        assertFalse(v("1234567", true, 6))
    }

    @Test fun trainingMaxCanBeCleared() = runTest {
        val bench = repo.getOrCreateExercise("Bench Press")
        repo.setTrainingMax(bench, kg(200.0))
        repo.setTrainingMax(bench, null)
        assertNull(repo.observeExercise(bench).first()!!.trainingMaxKg)
    }
}
