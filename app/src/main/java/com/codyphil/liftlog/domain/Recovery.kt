package com.codyphil.liftlog.domain

import com.codyphil.liftlog.data.Muscle
import com.codyphil.liftlog.data.MuscleSetEvent

enum class RecoveryStatus(val label: String) {
    RECOVERING("Recovering"),
    READY("Ready"),
    FRESH("Fresh"),
}

data class MuscleRecovery(
    val muscle: Muscle,
    /** Completed working sets in the last 72 hours. */
    val recentSets: Int,
    val lastTrained: Long?,
    val status: RecoveryStatus,
)

/**
 * A deliberately simple, transparent rule, not a physiological model:
 * - Recovering: trained in the last 24 h, or 6+ hard sets in the last 48 h
 * - Fresh: no hard sets in the last 4 days
 * - Ready: everything in between
 */
object Recovery {
    private const val HOUR = 3_600_000L

    fun compute(events: List<MuscleSetEvent>, now: Long): List<MuscleRecovery> {
        val byMuscle = events.groupBy { it.muscle }
        return Muscle.entries.filter { it != Muscle.OTHER }.map { m ->
            val e = byMuscle[m].orEmpty()
            val last = e.maxOfOrNull { it.completedAt }
            val sets72 = e.count { now - it.completedAt <= 72 * HOUR }
            val sets48 = e.count { now - it.completedAt <= 48 * HOUR }
            val hours = last?.let { (now - it) / HOUR }
            val status = when {
                hours == null || hours >= 96 -> RecoveryStatus.FRESH
                hours < 24 || sets48 >= 6 -> RecoveryStatus.RECOVERING
                else -> RecoveryStatus.READY
            }
            MuscleRecovery(m, sets72, last, status)
        }
    }

    /** "20 h ago", "3 days ago", "Over 4 days ago". */
    fun ago(lastTrained: Long?, now: Long): String {
        if (lastTrained == null) return "Not in the last week"
        val h = (now - lastTrained) / HOUR
        return when {
            h < 1 -> "Just now"
            h < 48 -> "${h} h ago"
            else -> "${h / 24} days ago"
        }
    }
}
