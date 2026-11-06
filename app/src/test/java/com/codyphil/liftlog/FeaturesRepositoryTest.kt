package com.codyphil.liftlog

import com.codyphil.liftlog.data.AppDatabase
import com.codyphil.liftlog.data.BodyMetric
import com.codyphil.liftlog.data.Muscle
import com.codyphil.liftlog.data.Progression
import com.codyphil.liftlog.data.SetType
import com.codyphil.liftlog.data.Templates
import com.codyphil.liftlog.data.WorkoutRepository
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.WeightUnit
import com.codyphil.liftlog.ui.workout.SupersetFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeaturesRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: WorkoutRepository
    private val clock = FakeClock()
    private val lb = WeightUnit.LB
    private fun kg(lbs: Double) = Units.toKg(lbs, lb)
    private fun show(kg: Double) = Units.format(kg, lb)

    @Before fun setUp() { db = inMemoryDb(); repo = WorkoutRepository(db, clock.fn) }
    @After fun tearDown() = db.close()

    private suspend fun squatDay(autoWarmup: Boolean = false): Pair<Long, Long> {
        val p = repo.createProgram("P")
        val d = repo.addDay(p, "A")
        val squat = repo.getOrCreateExercise("Back Squat")
        repo.addDayExercise(d, squat, 3, 5, kg(135.0), 180, Progression.LINEAR, kg(10.0), null, autoWarmup)
        return d to squat
    }

    private suspend fun doWorkout(day: Long, reps: List<Int>) {
        val s = repo.startFromDay(day, lb, 45.0)
        val sets = repo.observeSessionDetail(s).first()!!.exercises.single().sets
        sets.forEachIndexed { i, set ->
            clock.advanceSeconds(120)
            repo.completeSet(set.id, set.weightKg, if (set.setType == SetType.WARMUP) set.reps else reps.getOrElse(i - sets.count { it.setType == SetType.WARMUP }) { 5 })
        }
        repo.finishSession(s)
        clock.advanceSeconds(86_400 * 2)
    }

    @Test fun linearProgression_runsAcrossWorkouts_withNoteOnTheCard() = runTest {
        val (day, _) = squatDay()
        doWorkout(day, listOf(5, 5, 5))                    // 135 ✓
        val s = repo.startFromDay(day, lb, 45.0)
        val ex = repo.observeSessionDetail(s).first()!!.exercises.single()
        assertEquals(listOf("145", "145", "145"), ex.sets.map { show(it.weightKg) })
        assertEquals("+10 lb: you hit every rep last time", ex.row.note)
        repo.deleteSession(s)

        doWorkout(day, listOf(5, 5, 3))                    // 145 ✗
        doWorkout(day, listOf(5, 4, 3))                    // 145 ✗ again
        val s2 = repo.startFromDay(day, lb, 45.0)
        val ex2 = repo.observeSessionDetail(s2).first()!!.exercises.single()
        assertEquals("130", show(ex2.sets.first().weightKg)) // 145 * 0.9 = 130.5 -> 130
        assertTrue(ex2.row.note!!.startsWith("Deload"))
    }

    @Test fun autoWarmups_comeFirst_andAreExcludedFromHistoryAndProgression() = runTest {
        val (day, squat) = squatDay(autoWarmup = true)
        val s = repo.startFromDay(day, lb, 45.0)
        val sets = repo.observeSessionDetail(s).first()!!.exercises.single().sets
        assertEquals(List(4) { SetType.WARMUP } + List(3) { SetType.NORMAL }, sets.map { it.setType })
        assertEquals(listOf("45", "50", "80", "105", "135", "135", "135"), sets.map { show(it.weightKg) })
        assertEquals((1..7).toList(), sets.map { it.setNumber })
        sets.forEach { repo.completeSet(it.id, it.weightKg, it.reps) }
        repo.finishSession(s)

        val history = repo.observeExerciseHistory(squat).first()
        assertEquals(3, history.size)
        assertTrue(history.all { show(it.weightKg) == "135" })
        assertEquals(3, repo.lastTimeSets(squat).size)
        val summary = repo.observeFinishedSessions().first().single()
        assertEquals(3, summary.setCount)
        assertEquals(kg(135.0) * 15, summary.volumeKg, 1e-6)
    }

    @Test fun addWarmupSets_replacesUnfinishedWarmups_andRenumbers() = runTest {
        val s = repo.startEmpty()
        repo.addExerciseToSession(s, repo.getOrCreateExercise("Bench Press"), 90)
        val se = repo.observeSessionDetail(s).first()!!.exercises.single()
        assertFalse(repo.addWarmupSets(se.row.id, lb, 45.0)) // weight 0: nothing to warm up to
        repo.updateSetValues(se.sets.single().id, kg(225.0), 5)
        assertTrue(repo.addWarmupSets(se.row.id, lb, 45.0))
        assertTrue(repo.addWarmupSets(se.row.id, lb, 45.0)) // again: replaced, not duplicated
        val sets = repo.observeSessionDetail(s).first()!!.exercises.single().sets
        assertEquals(listOf("45", "90", "135", "180", "225"), sets.map { show(it.weightKg) })
        assertEquals(listOf(1, 2, 3, 4, 5), sets.map { it.setNumber })
        assertEquals(SetType.NORMAL, sets.last().setType)
    }

    @Test fun setTypeAndRpe_persist() = runTest {
        val (day, _) = squatDay()
        val s = repo.startFromDay(day, lb, 45.0)
        val set = repo.observeSessionDetail(s).first()!!.exercises.single().sets.last()
        repo.setSetType(set.id, SetType.FAILURE)
        repo.setSetRpe(set.id, 9.5)
        val after = repo.observeSessionDetail(s).first()!!.exercises.single().sets.last()
        assertEquals(SetType.FAILURE, after.setType)
        assertEquals(9.5, after.rpe!!, 1e-9)
        repo.setSetRpe(set.id, null)
        assertNull(repo.observeSessionDetail(s).first()!!.exercises.single().sets.last().rpe)
    }

    @Test fun supersets_copyFromDay_andFlowSkipsRestWithinARound() = runTest {
        repo.createFromTemplate(Templates.all.first { it.name == "Push / Pull / Legs" }, lb)
        val push = repo.observeAllDays().first().first { it.name == "Push" }
        val s = repo.startFromDay(push.id, lb, 45.0)
        val ex = repo.observeSessionDetail(s).first()!!.exercises
        val raise = ex.first { it.row.exerciseName == "Lateral Raise" }
        val pushdown = ex.first { it.row.exerciseName == "Tricep Pushdown" }
        assertNotNull(raise.row.supersetGroup)
        assertEquals(raise.row.supersetGroup, pushdown.row.supersetGroup)
        assertNull(ex.first { it.row.exerciseName == "Bench Press" }.row.supersetGroup)
        assertEquals(16, raise.row.repMax) // double progression from the template: 12..16

        val members = ex.filter { it.row.supersetGroup == raise.row.supersetGroup }
        val afterRaise = SupersetFlow.afterSet(ex, raise, raise.sets.first(), members)
        assertFalse(afterRaise.rest)
        assertEquals("Tricep Pushdown", afterRaise.goTo!!.row.exerciseName)
        val afterPushdown = SupersetFlow.afterSet(ex, pushdown, pushdown.sets.first(), members)
        assertTrue(afterPushdown.rest)
        assertEquals("Lateral Raise", afterPushdown.goTo!!.row.exerciseName)

        // Unlink in the workout; the pair dissolves.
        repo.setSessionSuperset(raise.row.id, false)
        assertTrue(repo.observeSessionDetail(s).first()!!.exercises.all { it.row.supersetGroup == null })
    }

    @Test fun weeklyMuscleSets_countWorkingSetsByPrimaryMuscle() = runTest {
        repo.seedIfEmpty()
        val s = repo.startEmpty()
        repo.addExerciseToSession(s, repo.getOrCreateExercise("Bench Press"), 90)
        repo.addExerciseToSession(s, repo.getOrCreateExercise("Dumbbell Curl"), 60)
        repo.addSet(repo.observeSessionDetail(s).first()!!.exercises[0].row.id)
        val ex = repo.observeSessionDetail(s).first()!!.exercises
        ex[0].sets.forEach { repo.completeSet(it.id, kg(135.0), 8) }
        repo.setSetType(ex[0].sets[0].id, SetType.WARMUP)
        repo.completeSet(ex[1].sets[0].id, kg(30.0), 10)
        val bySet = repo.observeMuscleSets(0).first().associate { it.muscle to it.sets }
        assertEquals(1, bySet[Muscle.CHEST]) // 2 bench sets, one marked warm-up
        assertEquals(1, bySet[Muscle.BICEPS])
        assertEquals(emptyMap<Muscle, Int>(), repo.observeMuscleSets(clock.now + 1).first().associate { it.muscle to it.sets })
    }

    @Test fun seed_backfillsMusclesForOldExercises_andRenameKeepsMuscle() = runTest {
        // An exercise created before muscle groups existed.
        val id = db.exerciseDao().insert(com.codyphil.liftlog.data.Exercise(name = "Bench Press"))
        repo.seedIfEmpty()
        assertEquals(Muscle.CHEST, repo.observeExercise(id).first()!!.muscle)
        assertTrue(repo.renameExercise(id, "Flat Bench"))
        assertEquals(Muscle.CHEST, repo.observeExercise(id).first()!!.muscle)
        repo.setExerciseMuscle(id, Muscle.TRICEPS)
        repo.seedIfEmpty() // must not overwrite a user's choice
        assertEquals(Muscle.TRICEPS, repo.observeExercise(id).first()!!.muscle)
    }

    @Test fun bodyEntries() = runTest {
        repo.addBodyEntry(BodyMetric.BODY_WEIGHT, 80.0, 1000)
        repo.addBodyEntry(BodyMetric.BODY_WEIGHT, 79.0, 2000)
        val waist = repo.addBodyEntry(BodyMetric.WAIST, 84.0, 1500)
        assertEquals(listOf(80.0, 79.0), repo.observeBody(BodyMetric.BODY_WEIGHT).first().map { it.value })
        repo.deleteBodyEntry(waist)
        assertTrue(repo.observeBody(BodyMetric.WAIST).first().isEmpty())
    }

    @Test fun everyTemplateImports() = runTest {
        Templates.all.forEach { repo.createFromTemplate(it, lb) }
        assertEquals(Templates.all.size, repo.observePrograms().first().size)
        val days = repo.observeAllDays().first()
        assertEquals(Templates.all.sumOf { it.days.size }, days.size)
        assertTrue(days.all { it.exerciseCount > 0 })
    }
}
