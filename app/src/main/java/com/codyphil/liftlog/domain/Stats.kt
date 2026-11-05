package com.codyphil.liftlog.domain

import com.codyphil.liftlog.data.ExerciseSetRow

object Stats {
    /**
     * Estimated one-rep max using the Epley formula. Accuracy drops off past ~10 reps,
     * so treat high-rep estimates as rough.
     */
    fun e1rm(weightKg: Double, reps: Int): Double = when {
        reps <= 0 -> 0.0
        reps == 1 -> weightKg
        else -> weightKg * (1 + reps / 30.0)
    }
}

data class SessionPoint(
    val sessionId: Long,
    val sessionName: String,
    val date: Long,
    val topWeightKg: Double,
    val bestE1rmKg: Double,
    val volumeKg: Double,
    val totalReps: Int,
    val sets: List<ExerciseSetRow>,
)

data class PersonalRecords(
    val heaviest: ExerciseSetRow?,
    val bestE1rm: Pair<ExerciseSetRow, Double>?,
    val bestVolume: SessionPoint?,
    val mostReps: ExerciseSetRow?,
)

object ExerciseAnalytics {
    /** Groups rows (assumed sorted oldest first) into one point per workout. */
    fun perSession(rows: List<ExerciseSetRow>): List<SessionPoint> =
        rows.groupBy { it.sessionId }.map { (id, sets) ->
            SessionPoint(
                sessionId = id,
                sessionName = sets.first().sessionName,
                date = sets.first().startedAt,
                topWeightKg = sets.maxOf { it.weightKg },
                bestE1rmKg = sets.maxOf { Stats.e1rm(it.weightKg, it.reps) },
                volumeKg = sets.sumOf { it.weightKg * it.reps },
                totalReps = sets.sumOf { it.reps },
                sets = sets,
            )
        }.sortedBy { it.date }

    fun records(rows: List<ExerciseSetRow>): PersonalRecords {
        val working = rows.filter { it.reps > 0 }
        return PersonalRecords(
            // Ties broken by more reps, then the earliest date (first time it was achieved).
            heaviest = working.sortedWith(
                compareByDescending<ExerciseSetRow> { it.weightKg }.thenByDescending { it.reps }.thenBy { it.startedAt }
            ).firstOrNull(),
            bestE1rm = working.map { it to Stats.e1rm(it.weightKg, it.reps) }
                .sortedWith(compareByDescending<Pair<ExerciseSetRow, Double>> { it.second }.thenBy { it.first.startedAt })
                .firstOrNull(),
            bestVolume = perSession(rows).maxByOrNull { it.volumeKg },
            mostReps = working.sortedWith(
                compareByDescending<ExerciseSetRow> { it.reps }.thenByDescending { it.weightKg }.thenBy { it.startedAt }
            ).firstOrNull(),
        )
    }
}
