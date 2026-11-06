package com.codyphil.liftlog.data

/** Query result / UI models that are not tables. */

data class DayExerciseWithName(
    val id: Long,
    val dayId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val position: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double,
    val restSeconds: Int,
    val progression: Progression,
    val incrementKg: Double,
    val repMax: Int?,
    val supersetGroup: Int?,
    val autoWarmup: Boolean,
    val muscle: Muscle,
    val percentScheme: String?,
    val trainingMaxKg: Double?,
)

data class DaySummary(
    val id: Long,
    val programId: Long,
    val name: String,
    val position: Int,
    val exerciseCount: Int,
)

data class ProgramSummary(
    val id: Long,
    val name: String,
    val notes: String,
    val dayCount: Int,
)

data class SessionSummary(
    val id: Long,
    val name: String,
    val programDayId: Long?,
    val startedAt: Long,
    val endedAt: Long?,
    val setCount: Int,
    val volumeKg: Double,
)

data class SessionExerciseRow(
    val id: Long,
    val sessionId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val position: Int,
    val restSeconds: Int,
    val targetReps: Int?,
    val supersetGroup: Int?,
    val note: String?,
    val repMax: Int?,
    val percentScheme: String?,
    val trainingMaxKg: Double?,
)

data class ExerciseSetRow(
    val sessionId: Long,
    val sessionName: String,
    val startedAt: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double? = null,
)

data class ExerciseHistorySummary(
    val id: Long,
    val name: String,
    val lastPerformed: Long,
    val sessionCount: Int,
)

/** A workout with its exercises and their sets, grouped for display. */
data class SessionDetail(
    val session: WorkoutSession,
    val exercises: List<SessionExerciseWithSets>,
)

data class SessionExerciseWithSets(
    val row: SessionExerciseRow,
    val sets: List<SetLog>,
)

data class DayWithProgram(
    val id: Long,
    val programId: Long,
    val programName: String,
    val name: String,
    val position: Int,
    val exerciseCount: Int,
)

data class MuscleSets(val muscle: Muscle, val sets: Int)

/** A completed working set with its muscle and time, for recovery and recaps. */
data class MuscleSetEvent(
    val muscle: Muscle,
    val completedAt: Long,
)

/** A completed working set with exercise and workout info, for monthly recaps. */
data class RecapSetRow(
    val sessionId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val muscle: Muscle,
    val weightKg: Double,
    val reps: Int,
)
