package com.codyphil.liftlog.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToLong

enum class WeightUnit(val label: String) {
    LB("lb"), KG("kg");
}

object Units {
    const val KG_PER_LB = 0.45359237

    fun toKg(value: Double, unit: WeightUnit): Double =
        if (unit == WeightUnit.KG) value else value * KG_PER_LB

    fun fromKg(kg: Double, unit: WeightUnit): Double =
        if (unit == WeightUnit.KG) kg else kg / KG_PER_LB

    /** Formats a weight for display: at most 2 decimals, trailing zeros stripped ("225", "102.5"). */
    fun format(kg: Double, unit: WeightUnit): String = formatNumber(fromKg(kg, unit))

    /** Rounded to a whole number, for estimates like e1RM where decimals are false precision. */
    fun formatWhole(kg: Double, unit: WeightUnit): String = fromKg(kg, unit).roundToLong().toString()

    fun formatWithUnit(kg: Double, unit: WeightUnit): String = "${format(kg, unit)} ${unit.label}"

    fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        return BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    /** Large numbers (volume) without decimals and with thousands separators. */
    fun formatVolume(kg: Double, unit: WeightUnit): String =
        "%,d %s".format(fromKg(kg, unit).roundToLong(), unit.label)

    /** Parses user input like "225", "102.5" or "102,5". Returns null when not a valid non-negative number. */
    fun parse(text: String): Double? {
        val v = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return if (v < 0 || v.isNaN() || v.isInfinite()) null else v
    }
}
