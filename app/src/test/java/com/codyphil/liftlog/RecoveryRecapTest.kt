package com.codyphil.liftlog

import com.codyphil.liftlog.data.Muscle
import com.codyphil.liftlog.data.MuscleSetEvent
import com.codyphil.liftlog.data.RecapSetRow
import com.codyphil.liftlog.domain.Recaps
import com.codyphil.liftlog.domain.Recovery
import com.codyphil.liftlog.domain.RecoveryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecoveryRecapTest {
    private val h = 3_600_000L
    private val now = 1_800_000_000_000L

    private fun events(muscle: Muscle, hoursAgo: Long, n: Int) = List(n) { MuscleSetEvent(muscle, now - hoursAgo * h) }

    @Test fun `recovery rule`() {
        val r = Recovery.compute(
            events(Muscle.CHEST, 30, 6) +        // 6 sets 30 h ago -> recovering (6+ in 48 h)
                events(Muscle.BACK, 30, 5) +     // 5 sets 30 h ago -> ready
                events(Muscle.QUADS, 10, 2) +    // trained 10 h ago -> recovering
                events(Muscle.BICEPS, 100, 9),   // 100 h ago -> fresh
            now,
        ).associateBy { it.muscle }
        assertEquals(RecoveryStatus.RECOVERING, r[Muscle.CHEST]!!.status)
        assertEquals(RecoveryStatus.READY, r[Muscle.BACK]!!.status)
        assertEquals(RecoveryStatus.RECOVERING, r[Muscle.QUADS]!!.status)
        assertEquals(RecoveryStatus.FRESH, r[Muscle.BICEPS]!!.status)
        assertEquals(RecoveryStatus.FRESH, r[Muscle.CALVES]!!.status)
        assertEquals(0, r[Muscle.BICEPS]!!.recentSets) // outside 72 h
        assertEquals(6, r[Muscle.CHEST]!!.recentSets)
        assertTrue(Muscle.OTHER !in r)
        assertEquals("30 h ago", Recovery.ago(now - 30 * h, now))
        assertEquals("4 days ago", Recovery.ago(now - 100 * h, now))
    }

    private fun at(y: Int, m: Int, d: Int, hour: Int = 18) = Calendar.getInstance().apply { clear(); set(y, m, d, hour, 0) }.timeInMillis
    private fun row(session: Long, start: Long, ex: Long, name: String, muscle: Muscle, kg: Double, reps: Int, minutes: Long = 60) =
        RecapSetRow(session, start, start + minutes * 60_000, ex, name, muscle, kg, reps)

    @Test fun `monthly recap`() {
        val oct = Recaps.monthStart(2026, Calendar.OCTOBER)
        val rows = listOf(
            row(1, at(2026, Calendar.SEPTEMBER, 28), 1, "Bench", Muscle.CHEST, 100.0, 5),          // before: e1RM 116.7
            row(2, at(2026, Calendar.OCTOBER, 5), 1, "Bench", Muscle.CHEST, 100.0, 8),             // 126.7 -> PR
            row(2, at(2026, Calendar.OCTOBER, 5), 2, "Squat", Muscle.QUADS, 140.0, 5),             // first time: no PR
            row(3, at(2026, Calendar.OCTOBER, 12), 1, "Bench", Muscle.CHEST, 90.0, 5),
            row(3, at(2026, Calendar.OCTOBER, 12), 1, "Bench", Muscle.CHEST, 90.0, 5),
            row(4, at(2026, Calendar.NOVEMBER, 2), 1, "Bench", Muscle.CHEST, 200.0, 5),            // next month: ignored
        )
        val r = Recaps.build(rows, oct)
        assertEquals(2, r.workouts)
        assertEquals(2, r.trainingDays)
        assertEquals(4, r.sets)
        assertEquals(120L, r.totalMinutes)
        assertEquals(100.0 * 8 + 140 * 5 + 90 * 5 * 2, r.volumeKg, 1e-9)
        assertEquals(listOf("Bench"), r.prs.map { it.exerciseName })
        assertEquals(Muscle.CHEST to 3, r.topMuscle)
        assertEquals("Bench" to 3, r.topExercise)
        assertEquals(1, r.previousMonthWorkouts)
        assertEquals(3, r.longestWeekStreak) // weeks of Sep 28, Oct 5, Oct 12
    }

    @Test fun `empty month`() {
        val r = Recaps.build(emptyList(), Recaps.monthStart(2026, Calendar.JANUARY))
        assertEquals(0, r.workouts)
        assertNull(r.topMuscle)
        assertEquals(0, r.longestWeekStreak)
    }
}
