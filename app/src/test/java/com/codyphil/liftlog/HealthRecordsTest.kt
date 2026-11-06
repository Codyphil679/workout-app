package com.codyphil.liftlog

import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.codyphil.liftlog.data.SetType
import com.codyphil.liftlog.data.WorkoutRepository
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.WeightUnit
import com.codyphil.liftlog.health.HealthRecords
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class HealthRecordsTest {
    @Test fun buildsAStrengthSessionWithOneSegmentPerSet() = runTest {
        val db = inMemoryDb()
        val clock = FakeClock()
        val repo = WorkoutRepository(db, clock.fn)
        val s = repo.startEmpty()
        repo.addExerciseToSession(s, repo.getOrCreateExercise("Back Squat"), 120)
        repo.addExerciseToSession(s, repo.getOrCreateExercise("Cable Woodchop"), 60)
        val squat = repo.observeSessionDetail(s).first()!!.exercises[0].row.id
        repo.addSet(squat)
        var ex = repo.observeSessionDetail(s).first()!!.exercises
        repo.setSetType(ex[0].sets[0].id, SetType.WARMUP)
        clock.advanceSeconds(30); repo.completeSet(ex[0].sets[0].id, Units.toKg(135.0, WeightUnit.LB), 5) // 30 s in: segment clamped to start
        clock.advanceSeconds(180); repo.completeSet(ex[0].sets[1].id, Units.toKg(225.0, WeightUnit.LB), 5)
        clock.advanceSeconds(20); repo.completeSet(ex[1].sets[0].id, 20.0, 12) // 20 s later: no overlap
        assertNull(HealthRecords.build(repo.observeSessionDetail(s).first()!!, WeightUnit.LB)) // not finished yet
        clock.advanceSeconds(60)
        repo.finishSession(s)

        val r = HealthRecords.build(repo.observeSessionDetail(s).first()!!, WeightUnit.LB, ZoneId.of("UTC"))!!
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, r.exerciseType)
        assertEquals(HealthRecords.clientId(s), r.metadata.clientRecordId)
        assertEquals(3, r.segments.size)
        assertEquals(listOf(5, 5, 12), r.segments.map { it.repetitions })
        assertEquals(
            listOf(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT, ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT, ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING),
            r.segments.map { it.segmentType },
        )
        assertEquals(r.startTime, r.segments[0].startTime)                 // clamped
        assertEquals(20L, r.segments[2].endTime.epochSecond - r.segments[2].startTime.epochSecond) // shortened to avoid overlap
        r.segments.zipWithNext().forEach { (a, b) -> assertTrue(!a.endTime.isAfter(b.startTime)) }
        assertTrue(r.notes!!, r.notes!!.contains("Back Squat: w 135×5, 225×5 lb"))
        db.close()
    }

    @Test fun segmentTypeMapping() {
        fun t(n: String) = HealthRecords.segmentTypeFor(n)
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS, t("Incline Dumbbell Bench"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL, t("Leg Curl"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL, t("Hammer Curl"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE, t("Walking Lunge"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT, t("Bulgarian Split Squat"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP, t("Chin-Up"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BARBELL_SHOULDER_PRESS, t("Overhead Press"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION, t("Overhead Tricep Extension"))
    }
}
