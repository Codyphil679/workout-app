package com.codyphil.liftlog.domain

import com.codyphil.liftlog.data.ExerciseSetRow
import com.codyphil.liftlog.data.Progression
import kotlin.math.floor

/** What to pre-fill for an exercise in the next workout, plus a one-line reason shown on the card. */
data class Plan(
    /** One entry per working set: weight (kg) and reps to pre-fill. */
    val sets: List<Pair<Double, Int>>,
    val note: String?,
)

data class ProgressionConfig(
    val type: Progression,
    val targetSets: Int,
    /** Target reps (linear) or bottom of the rep range (double). */
    val repMin: Int,
    /** Top of the rep range (double). Ignored for other types. */
    val repMax: Int,
    val startWeightKg: Double,
    val incrementKg: Double,
)

object Progressions {
    /** Smallest jump you can make with standard plates: 5 lb (2.5 per side) or 2.5 kg (1.25 per side). */
    fun roundingKg(unit: WeightUnit): Double = if (unit == WeightUnit.LB) Units.toKg(5.0, WeightUnit.LB) else 2.5

    /** Rounds down to a loadable weight, in the user's unit, so a deload never lands on a weight you can't load. */
    fun roundDownLoadable(kg: Double, unit: WeightUnit): Double {
        val step = if (unit == WeightUnit.LB) 5.0 else 2.5
        val display = Units.fromKg(kg, unit)
        return Units.toKg(floor(display / step + 1e-9) * step, unit)
    }

    /**
     * @param recent working sets from previous workouts, grouped per workout, newest first.
     */
    fun plan(cfg: ProgressionConfig, recent: List<List<ExerciseSetRow>>, unit: WeightUnit): Plan {
        val sets = cfg.targetSets.coerceAtLeast(1)
        val last = recent.firstOrNull()
        if (last.isNullOrEmpty()) {
            return Plan(List(sets) { cfg.startWeightKg to cfg.repMin }, null)
        }
        val inc = Units.format(cfg.incrementKg, unit) + " " + unit.label
        return when (cfg.type) {
            Progression.NONE -> Plan(
                List(sets) { i -> (last.getOrNull(i) ?: last.last()).let { it.weightKg to it.reps } },
                null,
            )

            Progression.LINEAR -> {
                val w = last.maxOf { it.weightKg }
                // A workout "succeeded" when every target set at its top weight reached the target reps.
                fun success(s: List<ExerciseSetRow>): Boolean {
                    val top = s.maxOf { it.weightKg }
                    val atTop = s.filter { kotlin.math.abs(it.weightKg - top) < 1e-6 }
                    return atTop.size >= sets && atTop.all { it.reps >= cfg.repMin }
                }
                when {
                    success(last) -> Plan(
                        List(sets) { (w + cfg.incrementKg) to cfg.repMin },
                        "+$inc: you hit every rep last time",
                    )
                    recent.size >= 2 && !success(recent[1]) &&
                        kotlin.math.abs(recent[1].maxOf { it.weightKg } - w) < 1e-6 -> {
                        val deload = roundDownLoadable(w * 0.9, unit)
                        Plan(List(sets) { deload to cfg.repMin }, "Deload −10%: missed reps two workouts in a row")
                    }
                    else -> Plan(
                        List(sets) { w to cfg.repMin },
                        "Same weight: hit $sets×${cfg.repMin} to move up",
                    )
                }
            }

            Progression.DOUBLE -> {
                val top = cfg.repMax.coerceAtLeast(cfg.repMin)
                val w = last.maxOf { it.weightKg }
                val atWeight = last.filter { kotlin.math.abs(it.weightKg - w) < 1e-6 }
                if (atWeight.size >= sets && atWeight.all { it.reps >= top }) {
                    Plan(List(sets) { (w + cfg.incrementKg) to cfg.repMin }, "+$inc: you hit $top reps on every set")
                } else {
                    Plan(
                        List(sets) { i -> w to (atWeight.getOrNull(i)?.reps ?: cfg.repMin).coerceIn(cfg.repMin, top) },
                        "Aim for $top reps on every set, then add weight",
                    )
                }
            }
        }
    }

    /** Sensible default per exercise: linear for the big barbell lifts, double progression for everything else. */
    fun defaultFor(exerciseName: String, reps: Int, unit: WeightUnit): Defaults {
        val n = exerciseName.lowercase()
        val lower = listOf("squat", "deadlift").any { n.contains(it) } && !n.contains("split") && !n.contains("goblet")
        val barbell = n in BARBELL_COMPOUNDS
        val small = if (unit == WeightUnit.LB) Units.toKg(5.0, WeightUnit.LB) else 2.5
        val big = if (unit == WeightUnit.LB) Units.toKg(10.0, WeightUnit.LB) else 5.0
        return if (barbell) Defaults(Progression.LINEAR, if (lower) big else small, null)
        else Defaults(Progression.DOUBLE, small, reps + 4)
    }

    data class Defaults(val progression: Progression, val incrementKg: Double, val repMax: Int?)

    private val BARBELL_COMPOUNDS = setOf(
        "back squat", "front squat", "deadlift", "romanian deadlift", "bench press", "incline bench press",
        "overhead press", "barbell row",
    )
}
