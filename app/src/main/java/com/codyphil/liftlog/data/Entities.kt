package com.codyphil.liftlog.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * All weights are stored in kilograms and converted for display (see domain/Units.kt).
 * All timestamps are epoch milliseconds.
 */

@Entity(tableName = "exercises", indices = [Index(value = ["name"], unique = true)])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "programs")
data class Program(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val createdAt: Long,
)

@Entity(
    tableName = "program_days",
    foreignKeys = [
        ForeignKey(Program::class, ["id"], ["programId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("programId")],
)
data class ProgramDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val name: String,
    val position: Int,
)

@Entity(
    tableName = "day_exercises",
    foreignKeys = [
        ForeignKey(ProgramDay::class, ["id"], ["dayId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Exercise::class, ["id"], ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("dayId"), Index("exerciseId")],
)
data class DayExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double,
    val restSeconds: Int,
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(ProgramDay::class, ["id"], ["programDayId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("programDayId"), Index("startedAt")],
)
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val programDayId: Long?,
    val startedAt: Long,
    /** Null while the workout is in progress. */
    val endedAt: Long? = null,
    val notes: String = "",
)

@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(WorkoutSession::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Exercise::class, ["id"], ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val position: Int,
    val restSeconds: Int,
    val targetReps: Int? = null,
)

@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(SessionExercise::class, ["id"], ["sessionExerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionExerciseId")],
)
data class SetLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    /**
     * Seconds between the previous completed set in this workout and this one.
     * This includes the time spent performing this set, so it slightly overstates pure rest.
     */
    val restBeforeSeconds: Int? = null,
)
