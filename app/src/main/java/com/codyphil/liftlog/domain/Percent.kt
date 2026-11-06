package com.codyphil.liftlog.domain

import kotlin.math.roundToLong

/** One set of a percentage scheme: [percent] of training max × [reps]; [amrap] = as many reps as possible. */
data class PercentSet(val percent: Double, val reps: Int, val amrap: Boolean = false)

object PercentScheme {
    /** Ready-made schemes. Generic structures, not any branded program. */
    val presets: List<Pair<String, String>> = listOf(
        "Ramp to AMRAP (75/80/85%)" to "75x5,80x5,85x5+",
        "Heavy single + back-offs" to "90x1+,80x3,80x3,80x3",
        "Top triple + back-offs" to "85x3+,75x5,75x5",
        "5 × 5 at 80%" to "80x5,80x5,80x5,80x5,80x5",
        "Volume 4 × 8 at 70%" to "70x8,70x8,70x8,70x8",
    )

    /** Parses "75x5,80x5,85x5+" (also accepts × and spaces). Invalid parts are skipped. */
    fun parse(text: String?): List<PercentSet> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(',').mapNotNull { part ->
            val p = part.trim().replace('×', 'x').replace("%", "").lowercase()
            val amrap = p.endsWith("+")
            val (pct, reps) = p.removeSuffix("+").split('x').map { it.trim() }.takeIf { it.size == 2 } ?: return@mapNotNull null
            val percent = pct.toDoubleOrNull()?.takeIf { it in 1.0..150.0 } ?: return@mapNotNull null
            val r = reps.toIntOrNull()?.takeIf { it in 1..100 } ?: return@mapNotNull null
            PercentSet(percent, r, amrap)
        }
    }

    fun format(sets: List<PercentSet>): String =
        sets.joinToString(",") { Units.formatNumber(it.percent) + "x" + it.reps + if (it.amrap) "+" else "" }

    /** Short label like "75%×5, 80%×5, 85%×5+". */
    fun describe(sets: List<PercentSet>): String =
        sets.joinToString(", ") { Units.formatNumber(it.percent) + "%×" + it.reps + if (it.amrap) "+" else "" }

    /** Weight for a percentage of the training max, rounded to the nearest loadable jump (5 lb / 2.5 kg). */
    fun weightFor(trainingMaxKg: Double, percent: Double, unit: WeightUnit): Double {
        val step = if (unit == WeightUnit.LB) 5.0 else 2.5
        val display = Units.fromKg(trainingMaxKg * percent / 100.0, unit)
        return Units.toKg((display / step).roundToLong() * step, unit)
    }
}

object TrainingMax {
    data class Change(val newKg: Double, val note: String)

    /** A common starting point: 90% of your estimated 1-rep max, rounded down to a loadable weight. */
    fun fromE1rm(e1rmKg: Double, unit: WeightUnit): Double = Progressions.roundDownLoadable(e1rmKg * 0.9, unit)

    /**
     * Adjusts the training max from an AMRAP result:
     * below the target → −10%; hit it → +1 increment; beat it by 5+ reps → +2 increments.
     */
    fun afterAmrap(tmKg: Double, target: Int, actual: Int, incrementKg: Double, unit: WeightUnit): Change {
        fun f(kg: Double) = Units.formatWithUnit(kg, unit)
        return when {
            actual < target -> {
                val next = Progressions.roundDownLoadable(tmKg * 0.9, unit)
                Change(next, "Training max ${f(tmKg)} → ${f(next)}: $actual of $target reps on the AMRAP set")
            }
            actual >= target + 5 -> {
                val next = tmKg + 2 * incrementKg
                Change(next, "Training max ${f(tmKg)} → ${f(next)}: $actual reps on the AMRAP set (target $target)")
            }
            else -> {
                val next = tmKg + incrementKg
                Change(next, "Training max ${f(tmKg)} → ${f(next)}: $actual reps on the AMRAP set (target $target)")
            }
        }
    }
}

object Readiness {
    val questions = listOf(
        "Sleep" to listOf("Awful", "Poor", "OK", "Good", "Great"),
        "Energy" to listOf("Drained", "Low", "OK", "Good", "Fired up"),
        "Soreness" to listOf("Very sore", "Sore", "A bit", "Fine", "None"),
        "Stress" to listOf("Maxed", "High", "Some", "Low", "None"),
    )

    /** Average of 1–5 answers. */
    fun score(answers: List<Int>): Double = answers.average()

    /** At or below this, the app suggests (never forces) a lighter day. */
    const val LIGHTER_DAY_THRESHOLD = 2.5
    const val LIGHTER_DAY_INTENSITY = 0.9

    fun suggestLighter(score: Double) = score <= LIGHTER_DAY_THRESHOLD
}
