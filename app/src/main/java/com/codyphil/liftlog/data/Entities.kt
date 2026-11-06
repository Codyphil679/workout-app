package com.codyphil.liftlog.data

import androidx.room.ColumnInfo
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
    /** Primary muscle, used for weekly sets-per-muscle. */
    @ColumnInfo(defaultValue = "OTHER") val muscle: Muscle = Muscle.OTHER,
    /** Training max for percentage-based programs (usually ~90% of a true 1RM). */
    val trainingMaxKg: Double? = null,
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
    @ColumnInfo(defaultValue = "NONE") val progression: Progression = Progression.NONE,
    /** Weight added when progression succeeds. */
    @ColumnInfo(defaultValue = "0") val incrementKg: Double = 0.0,
    /** Top of the rep range for double progression (targetReps is the bottom). */
    val repMax: Int? = null,
    /** Exercises sharing a group number in the same day are done as a superset. */
    val supersetGroup: Int? = null,
    @ColumnInfo(defaultValue = "0") val autoWarmup: Boolean = false,
    /** For PERCENT progression: sets as "75x5,80x5,85x5+" (percent of training max × reps, + = AMRAP). */
    val percentScheme: String? = null,
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
    /** Average of the pre-workout readiness check-in (1–5), if one was done. */
    val readiness: Double? = null,
    /** 1.0 = as planned; 0.9 = lighter day. Lighter days are ignored by progression. */
    @ColumnInfo(defaultValue = "1.0") val intensity: Double = 1.0,
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
    val supersetGroup: Int? = null,
    /** Progression advice shown on the exercise card, e.g. "+5 lb: you hit every rep last time". */
    val note: String? = null,
    /** Top of the rep range when the exercise uses double progression. */
    val repMax: Int? = null,
    /** Copied from the program day for % TM exercises, so weights can be recalculated if the training max changes. */
    val percentScheme: String? = null,
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
    @ColumnInfo(defaultValue = "NORMAL") val setType: SetType = SetType.NORMAL,
    /** Rate of perceived exertion, 6–10 in 0.5 steps. */
    val rpe: Double? = null,
    /** As many reps as possible; [targetReps] is the minimum to aim for. */
    @ColumnInfo(defaultValue = "0") val amrap: Boolean = false,
    val targetReps: Int? = null,
)

@Entity(tableName = "body_metrics", indices = [Index("recordedAt")])
data class BodyEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metric: BodyMetric,
    /** kg for weight, cm for lengths, % for body fat. */
    val value: Double,
    val recordedAt: Long,
)
