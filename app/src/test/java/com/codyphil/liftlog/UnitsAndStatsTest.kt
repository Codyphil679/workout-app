package com.codyphil.liftlog

import com.codyphil.liftlog.data.BackupManager
import com.codyphil.liftlog.data.ExerciseSetRow
import com.codyphil.liftlog.domain.ExerciseAnalytics
import com.codyphil.liftlog.domain.Stats
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitsAndStatsTest {
    @Test fun `lb round trips through kg without drift`() {
        for (lb in listOf(2.5, 45.0, 135.0, 225.0, 317.5, 405.0)) {
            assertEquals(Units.formatNumber(lb), Units.format(Units.toKg(lb, WeightUnit.LB), WeightUnit.LB))
        }
    }

    @Test fun `format strips trailing zeros`() {
        assertEquals("100", Units.format(100.0, WeightUnit.KG))
        assertEquals("102.5", Units.format(102.5, WeightUnit.KG))
    }

    @Test fun `parse accepts comma decimals and rejects junk`() {
        assertEquals(102.5, Units.parse("102,5")!!, 1e-9)
        assertNull(Units.parse("abc"))
        assertNull(Units.parse("-5"))
    }

    @Test fun `epley e1rm`() {
        assertEquals(100.0, Stats.e1rm(100.0, 1), 1e-9)
        assertEquals(116.6667, Stats.e1rm(100.0, 5), 1e-3)
        assertEquals(0.0, Stats.e1rm(100.0, 0), 1e-9)
    }

    @Test fun `records pick heaviest, best e1rm and best volume`() {
        val rows = listOf(
            ExerciseSetRow(1, "A", 1000, 1, 100.0, 5),
            ExerciseSetRow(1, "A", 1000, 2, 100.0, 5),
            ExerciseSetRow(2, "A", 2000, 1, 110.0, 1),
            ExerciseSetRow(2, "A", 2000, 2, 90.0, 10),
        )
        val pr = ExerciseAnalytics.records(rows)
        assertEquals(110.0, pr.heaviest!!.weightKg, 1e-9)
        assertEquals(120.0, pr.bestE1rm!!.second, 1e-9) // 90 x 10 -> 120
        // session 1 volume = 1000, session 2 = 110 + 900 = 1010
        assertEquals(2L, pr.bestVolume!!.sessionId)
        assertEquals(10, pr.mostReps!!.reps)
    }

    @Test fun `csv escaping and formula guard`() {
        assertEquals("plain", BackupManager.csv("plain"))
        assertEquals("\"a,b\"", BackupManager.csv("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", BackupManager.csv("say \"hi\""))
        assertEquals("'=SUM(A1)", BackupManager.csv("=SUM(A1)"))
    }
}
