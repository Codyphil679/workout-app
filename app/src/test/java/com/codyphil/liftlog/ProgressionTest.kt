package com.codyphil.liftlog

import com.codyphil.liftlog.data.ExerciseSetRow
import com.codyphil.liftlog.data.Progression
import com.codyphil.liftlog.data.Supersets
import com.codyphil.liftlog.domain.Plates
import com.codyphil.liftlog.domain.ProgressionConfig
import com.codyphil.liftlog.domain.Progressions
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.Warmups
import com.codyphil.liftlog.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {
    private val lb = WeightUnit.LB
    private fun kg(lbs: Double) = Units.toKg(lbs, lb)
    private fun show(kg: Double) = Units.format(kg, lb)
    private fun session(id: Long, vararg sets: Pair<Double, Int>) =
        sets.mapIndexed { i, (w, r) -> ExerciseSetRow(id, "W", id * 1000, i + 1, kg(w), r) }

    private val linear = ProgressionConfig(Progression.LINEAR, 3, 5, 5, kg(135.0), kg(5.0))
    private val double = ProgressionConfig(Progression.DOUBLE, 3, 8, 12, kg(50.0), kg(5.0))

    @Test fun `first time uses the starting weight`() {
        val p = Progressions.plan(linear, emptyList(), lb)
        assertEquals(List(3) { "135" to 5 }, p.sets.map { show(it.first) to it.second })
        assertNull(p.note)
    }

    @Test fun `linear adds weight after hitting every rep`() {
        val p = Progressions.plan(linear, listOf(session(2, 225.0 to 5, 225.0 to 5, 225.0 to 5)), lb)
        assertEquals(listOf("230", "230", "230"), p.sets.map { show(it.first) })
        assertTrue(p.note!!.startsWith("+5 lb"))
    }

    @Test fun `linear repeats the weight after one miss`() {
        val p = Progressions.plan(linear, listOf(session(2, 225.0 to 5, 225.0 to 5, 225.0 to 3)), lb)
        assertEquals(listOf("225", "225", "225"), p.sets.map { show(it.first) })
        assertTrue(p.note!!.startsWith("Same weight"))
    }

    @Test fun `linear deloads 10 percent after two misses at the same weight, rounded to loadable plates`() {
        val recent = listOf(
            session(3, 225.0 to 5, 225.0 to 4, 225.0 to 3),
            session(2, 225.0 to 5, 225.0 to 5, 225.0 to 4),
        )
        val p = Progressions.plan(linear, recent, lb)
        assertEquals(listOf("200", "200", "200"), p.sets.map { show(it.first) }) // 202.5 rounded down to 200
        assertTrue(p.note!!.startsWith("Deload"))
    }

    @Test fun `linear does not deload when the earlier miss was at a different weight`() {
        val recent = listOf(session(3, 230.0 to 5, 230.0 to 5, 230.0 to 3), session(2, 225.0 to 5, 225.0 to 5, 225.0 to 4))
        assertEquals("230", show(Progressions.plan(linear, recent, lb).sets.first().first))
    }

    @Test fun `linear needs all target sets, not just perfect reps on fewer sets`() {
        val p = Progressions.plan(linear, listOf(session(2, 225.0 to 5, 225.0 to 5)), lb)
        assertEquals("225", show(p.sets.first().first))
    }

    @Test fun `warm-up-free history with a back-off set still judges the top weight`() {
        val p = Progressions.plan(linear, listOf(session(2, 225.0 to 5, 225.0 to 5, 225.0 to 5, 185.0 to 8)), lb)
        assertEquals("230", show(p.sets.first().first))
    }

    @Test fun `double progression keeps weight and carries reps until the top of the range`() {
        val p = Progressions.plan(double, listOf(session(2, 50.0 to 12, 50.0 to 10, 50.0 to 9)), lb)
        assertEquals(listOf("50" to 12, "50" to 10, "50" to 9), p.sets.map { show(it.first) to it.second })
        assertTrue(p.note!!.contains("12 reps"))
    }

    @Test fun `double progression adds weight and resets reps when every set hits the top`() {
        val p = Progressions.plan(double, listOf(session(2, 50.0 to 12, 50.0 to 12, 50.0 to 13)), lb)
        assertEquals(listOf("55" to 8, "55" to 8, "55" to 8), p.sets.map { show(it.first) to it.second })
    }

    @Test fun `none repeats last time set by set`() {
        val cfg = linear.copy(type = Progression.NONE)
        val p = Progressions.plan(cfg, listOf(session(2, 100.0 to 10, 110.0 to 8)), lb)
        assertEquals(listOf("100" to 10, "110" to 8, "110" to 8), p.sets.map { show(it.first) to it.second })
    }

    @Test fun `defaults are linear for barbell compounds and double for accessories`() {
        val squat = Progressions.defaultFor("Back Squat", 5, lb)
        assertEquals(Progression.LINEAR, squat.progression)
        assertEquals("10", show(squat.incrementKg))
        assertEquals("5", show(Progressions.defaultFor("Bench Press", 5, lb).incrementKg))
        val curl = Progressions.defaultFor("Dumbbell Curl", 8, lb)
        assertEquals(Progression.DOUBLE, curl.progression)
        assertEquals(12, curl.repMax)
        assertEquals(Progression.DOUBLE, Progressions.defaultFor("Bulgarian Split Squat", 8, lb).progression)
        assertEquals(2.5, Progressions.defaultFor("Bench Press", 5, WeightUnit.KG).incrementKg, 1e-9)
    }

    @Test fun `plate math`() {
        val r = Plates.forWeight(225.0, 45.0, lb)
        assertEquals(listOf(45.0, 45.0), r.perSide)
        assertEquals(0.0, r.leftover, 1e-9)
        assertEquals(listOf(45.0, 35.0, 2.5), Plates.forWeight(210.0, 45.0, lb).perSide)
        assertEquals(listOf(25.0, 20.0, 2.5, 1.25), Plates.forWeight(117.5, 20.0, WeightUnit.KG).perSide)
        assertEquals(1.0, Plates.forWeight(136.0, 45.0, lb).leftover, 1e-9)
        assertTrue(Plates.forWeight(30.0, 45.0, lb).belowBar)
        assertEquals(emptyList<Double>(), Plates.forWeight(45.0, 45.0, lb).perSide)
    }

    @Test fun `warm-up ramp`() {
        assertEquals(listOf(45.0 to 10, 90.0 to 5, 135.0 to 3, 180.0 to 2), Warmups.forWorkingWeight(225.0, 45.0, lb))
        // Light working weight: no duplicate bar sets, nothing at or above the working weight.
        assertEquals(listOf(45.0 to 10, 55.0 to 2), Warmups.forWorkingWeight(70.0, 45.0, lb))
        assertEquals(emptyList<Pair<Double, Int>>(), Warmups.forWorkingWeight(50.0, 45.0, lb))
        assertEquals(listOf(20.0 to 10, 40.0 to 5, 60.0 to 3, 80.0 to 2), Warmups.forWorkingWeight(100.0, 20.0, WeightUnit.KG))
    }

    @Test fun `superset linking and unlinking`() {
        data class E(val id: Int, val g: Int?)
        val items = listOf(E(1, null), E(2, null), E(3, null))
        val linked = Supersets.apply(items, 0, true, { it.g }) { e, g -> e.copy(g = g) }
        assertEquals(listOf(1, 1, null), linked.map { it.g })
        val three = Supersets.apply(linked, 1, true, { it.g }) { e, g -> e.copy(g = g) }
        assertEquals(listOf(1, 1, 1), three.map { it.g })
        val second = Supersets.apply(listOf(E(1, 1), E(2, 1), E(3, null), E(4, null)), 2, true, { it.g }) { e, g -> e.copy(g = g) }
        assertEquals(listOf(1, 1, 2, 2), second.map { it.g })
        // Removing one of a pair dissolves the group.
        val unlinked = Supersets.apply(linked, 0, false, { it.g }) { e, g -> e.copy(g = g) }
        assertEquals(listOf<Int?>(null, null, null), unlinked.map { it.g })
    }
}
