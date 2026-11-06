package com.codyphil.liftlog

import com.codyphil.liftlog.data.AppDatabase
import com.codyphil.liftlog.data.Progression
import com.codyphil.liftlog.data.SetType
import com.codyphil.liftlog.data.WorkoutRepository
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PercentRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: WorkoutRepository
    private val clock = FakeClock()
    private val lb = WeightUnit.LB
    private fun kg(lbs: Double) = Units.toKg(lbs, lb)
    private fun show(kg: Double) = Units.format(kg, lb)

    @Before fun setUp() { db = inMemoryDb(); repo = WorkoutRepository(db, clock.fn) }
    @After fun tearDown() = db.close()

    private suspend fun percentDay(name: String = "Back Squat"): Pair<Long, Long> {
        val p = repo.createProgram("P")
        val d = repo.addDay(p, "A")
        val ex = repo.getOrCreateExercise(name)
        repo.addDayExercise(d, ex, 3, 5, 0.0, 180, Progression.PERCENT, 0.0, null, false, percentScheme = "75x5,80x5,85x5+")
        return d to ex
    }

    private suspend fun completeAll(sessionId: Long, lastReps: Int) {
        val sets = repo.observeSessionDetail(sessionId).first()!!.exercises.single().sets
        sets.forEachIndexed { i, s -> clock.advanceSeconds(120); repo.completeSet(s.id, s.weightKg, if (i == sets.lastIndex) lastReps else s.reps) }
    }

    @Test fun noTrainingMax_thenSettingOneRecalculatesTheWorkout() = runTest {
        val (day, ex) = percentDay()
        val s = repo.startFromDay(day, lb, 45.0)
        val se = repo.observeSessionDetail(s).first()!!.exercises.single()
        assertTrue(se.sets.all { it.weightKg == 0.0 })
        assertTrue(se.row.note!!.startsWith("Set a training max"))
        assertEquals(listOf(false, false, true), se.sets.map { it.amrap })
        assertEquals(5, se.sets.last().targetReps)

        repo.completeSet(se.sets[0].id, kg(135.0), 5) // a completed set must not be changed
        repo.applyTrainingMax(se.row.id, kg(300.0), lb)
        val after = repo.observeSessionDetail(s).first()!!.exercises.single()
        assertEquals(listOf("135", "240", "255"), after.sets.map { show(it.weightKg) })
        assertEquals("300", show(repo.observeExercise(ex).first()!!.trainingMaxKg!!))
    }

    @Test fun amrapResultUpdatesTrainingMaxOnFinish() = runTest {
        val (day, ex) = percentDay()
        repo.setTrainingMax(ex, kg(300.0))
        val s = repo.startFromDay(day, lb, 45.0)
        assertEquals(listOf("225", "240", "255"),
            repo.observeSessionDetail(s).first()!!.exercises.single().sets.map { show(it.weightKg) })
        completeAll(s, lastReps = 8)
        repo.finishSession(s, lb)
        assertEquals("310", show(repo.observeExercise(ex).first()!!.trainingMaxKg!!)) // squat increment = 10 lb
        val note = repo.observeSessionDetail(s).first()!!.exercises.single().row.note!!
        assertTrue(note, note.startsWith("Training max 300 lb → 310 lb"))

        clock.advanceSeconds(86_400)
        val s2 = repo.startFromDay(day, lb, 45.0)
        completeAll(s2, lastReps = 3) // missed
        repo.finishSession(s2, lb)
        assertEquals("275", show(repo.observeExercise(ex).first()!!.trainingMaxKg!!)) // 310 * 0.9 = 279 -> 275
    }

    @Test fun trainingMaxIsSeededFromHistory() = runTest {
        val bench = repo.getOrCreateExercise("Bench Press")
        val q = repo.startEmpty()
        repo.addExerciseToSession(q, bench, 90)
        val set = repo.observeSessionDetail(q).first()!!.exercises.single().sets.single()
        repo.completeSet(set.id, kg(225.0), 5) // e1RM 262.5
        repo.finishSession(q)
        val (day, _) = percentDay("Bench Press")
        val s = repo.startFromDay(day, lb, 45.0)
        val se = repo.observeSessionDetail(s).first()!!.exercises.single()
        assertEquals("235", show(repo.observeExercise(bench).first()!!.trainingMaxKg!!)) // 236.25 -> 235
        assertTrue(se.row.note!!.contains("90% of your best estimated 1RM"))
        assertEquals("175", show(se.sets.first().weightKg)) // 75% of 235 = 176.25 -> 175
    }

    @Test fun lighterDay_scalesWeights_andIsIgnoredByProgressionAndTrainingMax() = runTest {
        val p = repo.createProgram("P")
        val d = repo.addDay(p, "A")
        val squat = repo.getOrCreateExercise("Back Squat")
        repo.addDayExercise(d, squat, 3, 5, kg(200.0), 180, Progression.LINEAR, kg(10.0))
        val (pday, bench) = percentDay("Bench Press")
        repo.setTrainingMax(bench, kg(200.0))

        val light = repo.startFromDay(d, lb, 45.0, intensity = 0.9, readiness = 2.0)
        val ex = repo.observeSessionDetail(light).first()!!
        assertEquals(0.9, ex.session.intensity, 1e-9)
        assertEquals(2.0, ex.session.readiness!!, 1e-9)
        assertEquals(listOf("180", "180", "180"), ex.exercises.single().sets.map { show(it.weightKg) })
        assertTrue(ex.exercises.single().row.note!!.startsWith("Lighter day (−10%)"))
        completeAll(light, lastReps = 5)
        repo.finishSession(light, lb)

        // Next normal workout ignores the lighter day: still the starting weight, not 180 + 10.
        clock.advanceSeconds(86_400)
        val normal = repo.startFromDay(d, lb, 45.0)
        assertEquals("200", show(repo.observeSessionDetail(normal).first()!!.exercises.single().sets.first().weightKg))
        repo.deleteSession(normal)

        // A lighter % day doesn't move the training max even with a big AMRAP.
        val lightPct = repo.startFromDay(pday, lb, 45.0, intensity = 0.9)
        completeAll(lightPct, lastReps = 12)
        repo.finishSession(lightPct, lb)
        assertEquals("200", show(repo.observeExercise(bench).first()!!.trainingMaxKg!!))
    }

    @Test fun amrapToggleKeepsTarget() = runTest {
        val s = repo.startEmpty()
        repo.addExerciseToSession(s, repo.getOrCreateExercise("Dip"), 60)
        val set = repo.observeSessionDetail(s).first()!!.exercises.single().sets.single()
        repo.updateSetValues(set.id, 0.0, 8)
        repo.setAmrap(set.id, true)
        val on = repo.observeSessionDetail(s).first()!!.exercises.single().sets.single()
        assertTrue(on.amrap); assertEquals(8, on.targetReps)
        repo.setAmrap(set.id, false)
        assertEquals(null, repo.observeSessionDetail(s).first()!!.exercises.single().sets.single().targetReps)
        assertEquals(SetType.NORMAL, on.setType)
    }
}
