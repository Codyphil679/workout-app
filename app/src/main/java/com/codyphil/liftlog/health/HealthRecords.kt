package com.codyphil.liftlog.health

import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import com.codyphil.liftlog.data.SessionDetail
import com.codyphil.liftlog.data.SetType
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.WeightUnit
import java.time.Instant
import java.time.ZoneId

/** Turns a finished LiftLog workout into a Health Connect strength-training session. Pure, so it's unit-tested. */
object HealthRecords {
    /** Stable id so re-syncing a workout updates the same Health Connect record instead of duplicating it. */
    fun clientId(sessionId: Long) = "liftlog-session-$sessionId"

    /** Longest a single set is assumed to take when placing it on the timeline. */
    private const val SET_SECONDS = 60L

    fun build(detail: SessionDetail, unit: WeightUnit, zone: ZoneId = ZoneId.systemDefault()): ExerciseSessionRecord? {
        val s = detail.session
        val endedAt = s.endedAt ?: return null
        val start = Instant.ofEpochMilli(s.startedAt)
        val end = Instant.ofEpochMilli(maxOf(endedAt, s.startedAt + 1000))

        // One segment per completed set: it ends when the set was checked off and starts up to a minute
        // earlier, never overlapping the previous set or leaving the workout's time range.
        val sets = detail.exercises.flatMap { ex -> ex.sets.filter { it.completed && it.completedAt != null }.map { ex.row.exerciseName to it } }
            .sortedBy { it.second.completedAt }
        val segments = mutableListOf<ExerciseSegment>()
        var prevEnd = start
        for ((name, set) in sets) {
            val setEnd = Instant.ofEpochMilli(set.completedAt!!).coerceIn(start, end)
            val setStart = maxOf(prevEnd, setEnd.minusSeconds(SET_SECONDS))
            if (!setStart.isBefore(setEnd)) continue
            segments += ExerciseSegment(setStart, setEnd, segmentTypeFor(name), set.reps.coerceAtLeast(0))
            prevEnd = setEnd
        }

        val notes = detail.exercises.filter { e -> e.sets.any { it.completed } }.joinToString("\n") { e ->
            e.row.exerciseName + ": " + e.sets.filter { it.completed }.joinToString(", ") {
                (if (it.setType == SetType.WARMUP) "w " else "") + "${Units.format(it.weightKg, unit)}×${it.reps}"
            } + " ${unit.label}"
        }.take(1000)

        return ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = zone.rules.getOffset(start),
            endTime = end,
            endZoneOffset = zone.rules.getOffset(end),
            metadata = Metadata.activelyRecorded(
                device = Device(type = Device.TYPE_PHONE),
                clientRecordId = clientId(s.id),
                clientRecordVersion = endedAt,
            ),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = s.name.take(100),
            notes = notes,
            segments = segments,
        )
    }

    /** Best-matching Health Connect segment type for an exercise name; everything else counts as weightlifting. */
    fun segmentTypeFor(name: String): Int {
        val n = name.lowercase()
        return when {
            "lunge" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE
            "squat" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT
            "deadlift" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT
            "hip thrust" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIP_THRUST
            "bench" in n || "chest press" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS
            "overhead press" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BARBELL_SHOULDER_PRESS
            "shoulder press" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS
            "curl" in n && "leg" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL
            "curl" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL
            "pushdown" in n || "skull" in n || "tricep" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION
            "lateral raise" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LATERAL_RAISE
            "front raise" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_FRONT_RAISE
            "dumbbell row" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW
            "pulldown" in n || "pull-down" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN
            "pull-up" in n || "pull up" in n || "chin" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP
            "leg extension" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_EXTENSION
            "leg press" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_PRESS
            "leg raise" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_RAISE
            "plank" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK
            "crunch" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH
            "sit-up" in n || "sit up" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_SIT_UP
            "kettlebell swing" in n -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_KETTLEBELL_SWING
            else -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING
        }
    }
}
