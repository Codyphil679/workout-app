package com.codyphil.liftlog

import com.codyphil.liftlog.data.ExerciseSetRow
import com.codyphil.liftlog.data.Progression
import com.codyphil.liftlog.domain.PercentScheme
import com.codyphil.liftlog.domain.PercentSet
import com.codyphil.liftlog.domain.ProgressionConfig
import com.codyphil.liftlog.domain.Progressions
import com.codyphil.liftlog.domain.Readiness
import com.codyphil.liftlog.domain.TrainingMax
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PercentAndRpeTest {
    private val lb = WeightUnit.LB
    private fun kg(lbs: Double) = Units.toKg(lbs, lb)
    private fun show(kg: Double) = Units.format(kg, lb)

    @Test fun `scheme parse, format, describe`() {
        val s = PercentScheme.parse(" 75x5, 80×5 ,85x5+, junk, 0x5, 70x0")
        assertEquals(listOf(PercentSet(75.0, 5), PercentSet(80.0, 5), PercentSet(85.0, 5, true)), s)
        assertEquals("75x5,80x5,85x5+", PercentScheme.format(s))
        assertEquals("75%×5, 80%×5, 85%×5+", PercentScheme.describe(s))
        assertEquals(emptyList<PercentSet>(), PercentScheme.parse(null))
        PercentScheme.presets.forEach { (_, v) -> assertTrue(v, PercentScheme.parse(v).isNotEmpty()) }
    }

    @Test fun `percent weights round to the nearest loadable jump`() {
        assertEquals("170", show(PercentScheme.weightFor(kg(200.0), 85.0, lb)))
        assertEquals("175", show(PercentScheme.weightFor(kg(205.0), 85.0, lb))) // 174.25
        assertEquals("155", show(PercentScheme.weightFor(kg(205.0), 75.0, lb))) // 153.75
        assertEquals(77.5, PercentScheme.weightFor(100.0, 77.0, WeightUnit.KG), 1e-9)
    }

    @Test fun `training max moves with the AMRAP result`() {
        val tm = kg(200.0); val inc = kg(5.0)
        assertEquals("180", show(TrainingMax.afterAmrap(tm, 5, 3, inc, lb).newKg))   // miss: -10%
        assertEquals("205", show(TrainingMax.afterAmrap(tm, 5, 5, inc, lb).newKg))   // hit
        assertEquals("205", show(TrainingMax.afterAmrap(tm, 5, 9, inc, lb).newKg))
        assertEquals("210", show(TrainingMax.afterAmrap(tm, 5, 10, inc, lb).newKg))  // +5 over
        assertTrue(TrainingMax.afterAmrap(tm, 5, 3, inc, lb).note.contains("3 of 5"))
        assertEquals("225", show(TrainingMax.fromE1rm(kg(255.0), lb)))               // 229.5 -> 225
    }

    @Test fun `percent plan with and without a training max`() {
        val cfg = ProgressionConfig(Progression.PERCENT, 3, 5, 5, 0.0, 0.0, PercentScheme.parse("75x5,80x5,85x5+"), kg(200.0))
        val p = Progressions.plan(cfg, emptyList(), lb)
        assertEquals(listOf("150" to 5, "160" to 5, "170" to 5), p.sets.map { show(it.first) to it.second })
        assertEquals(listOf(false, false, true), p.amrap)
        assertTrue(p.note!!.startsWith("Training max 200 lb"))
        val none = Progressions.plan(cfg.copy(trainingMaxKg = null), emptyList(), lb)
        assertTrue(none.sets.all { it.first == 0.0 })
        assertTrue(none.note!!.startsWith("Set a training max"))
    }

    private fun session(id: Long, vararg sets: Triple<Double, Int, Double?>) =
        sets.mapIndexed { i, (w, r, rpe) -> ExerciseSetRow(id, "W", id, i + 1, kg(w), r, rpe) }
    private val linear = ProgressionConfig(Progression.LINEAR, 3, 5, 5, kg(135.0), kg(5.0))

    @Test fun `linear holds when every rep was hit at RPE 10`() {
        val p = Progressions.plan(linear, listOf(session(1, Triple(225.0, 5, 8.0), Triple(225.0, 5, 9.0), Triple(225.0, 5, 10.0))), lb)
        assertEquals("225", show(p.sets.first().first))
        assertTrue(p.note!!.contains("RPE 10"))
    }

    @Test fun `linear doubles the jump when every set was RPE 7 or easier`() {
        val p = Progressions.plan(linear, listOf(session(1, Triple(225.0, 5, 6.5), Triple(225.0, 5, 7.0), Triple(225.0, 5, 7.0))), lb)
        assertEquals("235", show(p.sets.first().first))
    }

    @Test fun `linear uses the normal jump when RPE is only partly logged`() {
        val p = Progressions.plan(linear, listOf(session(1, Triple(225.0, 5, 6.0), Triple(225.0, 5, null), Triple(225.0, 5, null))), lb)
        assertEquals("230", show(p.sets.first().first))
    }

    @Test fun `double progression holds at the top of the range if it was RPE 10`() {
        val cfg = ProgressionConfig(Progression.DOUBLE, 3, 8, 12, kg(50.0), kg(5.0))
        val p = Progressions.plan(cfg, listOf(session(1, Triple(50.0, 12, 9.0), Triple(50.0, 12, 9.5), Triple(50.0, 12, 10.0))), lb)
        assertEquals(listOf("50" to 12, "50" to 12, "50" to 12), p.sets.map { show(it.first) to it.second })
    }

    @Test fun `readiness score and lighter day threshold`() {
        assertEquals(2.5, Readiness.score(listOf(2, 3, 2, 3)), 1e-9)
        assertTrue(Readiness.suggestLighter(2.5))
        assertFalse(Readiness.suggestLighter(2.75))
    }
}
