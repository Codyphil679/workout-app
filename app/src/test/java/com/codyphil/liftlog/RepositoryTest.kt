package com.codyphil.liftlog

import com.codyphil.liftlog.data.AppDatabase
import com.codyphil.liftlog.data.WorkoutRepository
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
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: WorkoutRepository
    private val clock = FakeClock()

    @Before fun setUp() {
        db = inMemoryDb()
        repo = WorkoutRepository(db, clock.fn)
    }

    @After fun tearDown() = db.close()

    private suspend fun programWithSquat(sets: Int = 3, reps: Int = 5, kg: Double = 100.0, rest: Int = 120): Pair<Long, Long> {
        val p = repo.createProgram("Strength")
        val d = repo.addDay(p, "Day A")
        val squat = repo.getOrCreateExercise("Back Squat")
        repo.addDayExercise(d, squat, sets, reps, kg, rest)
        return d to squat
    }

    @Test fun startFromDay_usesTemplateWeightFirstTime_thenLastTime() = runTest {
        val (day, squat) = programWithSquat()
        val s1 = repo.startFromDay(day)
        val d1 = repo.observeSessionDetail(s1).first()!!
        assertEquals("Strength · Day A", d1.session.name)
        assertEquals(3, d1.exercises.single().sets.size)
        assertTrue(d1.exercises.single().sets.all { it.weightKg == 100.0 && it.reps == 5 })

        // Log heavier sets: 105, 105, 102.5
        val sets = d1.exercises.single().sets
        repo.completeSet(sets[0].id, 105.0, 5)
        repo.completeSet(sets[1].id, 105.0, 5)
        repo.completeSet(sets[2].id, 102.5, 4)
        assertTrue(repo.finishSession(s1))

        val s2 = repo.startFromDay(day)
        val prefill = repo.observeSessionDetail(s2).first()!!.exercises.single().sets.map { it.weightKg }
        assertEquals(listOf(105.0, 105.0, 102.5), prefill)
        assertEquals(listOf(105.0, 105.0, 102.5), repo.lastTimeSets(squat).map { it.weightKg })
    }

    @Test fun startFromDay_returnsActiveSessionInsteadOfStartingSecond() = runTest {
        val (day, _) = programWithSquat()
        val s1 = repo.startFromDay(day)
        assertEquals(s1, repo.startFromDay(day))
        assertEquals(s1, repo.startEmpty())
    }

    @Test fun completeSet_recordsTimeSincePreviousSet_andReturnsRest() = runTest {
        val (day, _) = programWithSquat(rest = 150)
        val s = repo.startFromDay(day)
        val sets = repo.observeSessionDetail(s).first()!!.exercises.single().sets
        assertEquals(150, repo.completeSet(sets[0].id, 100.0, 5))
        clock.advanceSeconds(185)
        repo.completeSet(sets[1].id, 100.0, 5)
        val after = repo.observeSessionDetail(s).first()!!.exercises.single().sets
        assertNull(after[0].restBeforeSeconds)
        assertEquals(185, after[1].restBeforeSeconds)
    }

    @Test fun finishSession_dropsIncompleteSetsAndEmptyExercises() = runTest {
        val (day, _) = programWithSquat()
        val s = repo.startFromDay(day)
        val bench = repo.getOrCreateExercise("Bench Press")
        repo.addExerciseToSession(s, bench, 90)
        val squatSets = repo.observeSessionDetail(s).first()!!.exercises.first().sets
        repo.completeSet(squatSets[0].id, 100.0, 5)

        assertTrue(repo.finishSession(s))
        val detail = repo.observeSessionDetail(s).first()!!
        assertNotNull(detail.session.endedAt)
        assertEquals(1, detail.exercises.size)
        assertEquals(1, detail.exercises.single().sets.size)
        assertNull(repo.observeActiveSession().first())
    }

    @Test fun finishSession_withNothingCompleted_returnsFalseAndKeepsWorkout() = runTest {
        val (day, _) = programWithSquat()
        val s = repo.startFromDay(day)
        assertFalse(repo.finishSession(s))
        assertEquals(s, repo.observeActiveSession().first()!!.id)
    }

    @Test fun history_onlyIncludesFinishedWorkouts() = runTest {
        val (day, squat) = programWithSquat()
        val s = repo.startFromDay(day)
        val sets = repo.observeSessionDetail(s).first()!!.exercises.single().sets
        repo.completeSet(sets[0].id, 100.0, 5)
        assertTrue(repo.observeExerciseHistory(squat).first().isEmpty())
        assertTrue(repo.observeFinishedSessions().first().isEmpty())
        repo.finishSession(s)
        assertEquals(1, repo.observeExerciseHistory(squat).first().size)
        val summary = repo.observeFinishedSessions().first().single()
        assertEquals(1, summary.setCount)
        assertEquals(500.0, summary.volumeKg, 1e-9)
    }

    @Test fun deleteSet_renumbersRemaining() = runTest {
        val (day, _) = programWithSquat()
        val s = repo.startFromDay(day)
        val sets = repo.observeSessionDetail(s).first()!!.exercises.single().sets
        repo.deleteSet(sets[0].id)
        assertEquals(listOf(1, 2), repo.observeSessionDetail(s).first()!!.exercises.single().sets.map { it.setNumber })
    }

    @Test fun exercises_caseInsensitiveDedupe_andDeleteOnlyWhenUnused() = runTest {
        val a = repo.getOrCreateExercise("Front Squat")
        assertEquals(a, repo.getOrCreateExercise("  front   squat "))
        val (_, squat) = programWithSquat()
        assertFalse(repo.deleteExerciseIfUnused(squat))
        assertTrue(repo.deleteExerciseIfUnused(a))
        repo.getOrCreateExercise("Deadlift")
        assertFalse(repo.renameExercise(squat, "deadlift"))
        assertTrue(repo.renameExercise(squat, "High-Bar Squat"))
    }

    @Test fun moveDay_reordersPositions() = runTest {
        val p = repo.createProgram("PPL")
        val push = repo.addDay(p, "Push")
        repo.addDay(p, "Pull")
        val legs = repo.addDay(p, "Legs")
        repo.moveDay(legs, -2)
        assertEquals(listOf("Legs", "Push", "Pull"), repo.observeDays(p).first().map { it.name })
        repo.moveDay(push, 5)
        assertEquals(listOf("Legs", "Pull", "Push"), repo.observeDays(p).first().map { it.name })
    }

    @Test fun deletingProgramKeepsHistory() = runTest {
        val (day, _) = programWithSquat()
        val s = repo.startFromDay(day)
        repo.completeSet(repo.observeSessionDetail(s).first()!!.exercises.single().sets[0].id, 100.0, 5)
        repo.finishSession(s)
        repo.deleteProgram(repo.observePrograms().first().single().id)
        val kept = repo.observeSessionDetail(s).first()!!
        assertNull(kept.session.programDayId)
        assertEquals(1, kept.exercises.single().sets.size)
    }
}
