package com.codyphil.liftlog.domain

import kotlin.math.floor
import kotlin.math.roundToLong

object Plates {
    val LB = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)
    val KG = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    fun available(unit: WeightUnit) = if (unit == WeightUnit.LB) LB else KG

    data class Result(
        /** Plates for ONE side, heaviest first, in the display unit. */
        val perSide: List<Double>,
        /** Weight (display unit, total) that can't be made with the available plates. 0 when exact. */
        val leftover: Double,
        val belowBar: Boolean,
    )

    /** Greedy plate loading. [total] and [bar] are in the display unit. */
    fun forWeight(total: Double, bar: Double, unit: WeightUnit): Result {
        if (total < bar - 1e-9) return Result(emptyList(), 0.0, belowBar = true)
        var side = (total - bar) / 2
        val out = mutableListOf<Double>()
        for (p in available(unit)) {
            val n = floor(side / p + 1e-9).toInt()
            repeat(n) { out += p }
            side -= n * p
        }
        val leftover = ((side * 2) * 100).roundToLong() / 100.0
        return Result(out, if (leftover < 1e-6) 0.0 else leftover, belowBar = false)
    }
}

object Warmups {
    /**
     * Warm-up ramp before a working weight (all values in the display unit):
     * empty bar × 10, then about 40% × 5, 60% × 3 and 80% × 2, rounded down to loadable weights.
     * Steps that would be the bar again, or too close to the working weight, are dropped.
     */
    fun forWorkingWeight(work: Double, bar: Double, unit: WeightUnit): List<Pair<Double, Int>> {
        val step = if (unit == WeightUnit.LB) 5.0 else 2.5
        if (work <= bar * 1.25 + 1e-6) return emptyList()
        val out = mutableListOf(bar to 10)
        for ((pct, reps) in listOf(0.4 to 5, 0.6 to 3, 0.8 to 2)) {
            // Small epsilon: weights stored in kg come back as e.g. 149.99999999999997 lb.
            val w = floor(work * pct / step + 1e-6) * step
            if (w > out.last().first + 1e-6 && w < work - step / 2) out += w to reps
        }
        return out
    }
}
