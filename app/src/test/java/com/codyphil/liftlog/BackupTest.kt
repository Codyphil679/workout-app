package com.codyphil.liftlog

import com.codyphil.liftlog.data.BackupManager
import com.codyphil.liftlog.data.Templates
import com.codyphil.liftlog.data.WorkoutRepository
import com.codyphil.liftlog.domain.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private val clock = FakeClock()

    @Test fun exportThenImport_roundTripsEverything() = runTest {
        val src = inMemoryDb()
        val repo = WorkoutRepository(src, clock.fn)
        repo.createFromTemplate(Templates.all[1])
        val day = repo.observeAllDays().first().first()
        val s = repo.startFromDay(day.id)
        val sets = repo.observeSessionDetail(s).first()!!.exercises.first().sets
        repo.completeSet(sets[0].id, 60.0, 6)
        clock.advanceSeconds(120)
        repo.completeSet(sets[1].id, 62.5, 6)
        repo.finishSession(s)
        repo.updateSessionNotes(s, "Felt strong, \"PR\" day")

        val json = BackupManager(src, clock.fn).exportJson()

        val dst = inMemoryDb()
        val dstRepo = WorkoutRepository(dst, clock.fn)
        dstRepo.getOrCreateExercise("Something that should be wiped")
        val result = BackupManager(dst, clock.fn).importJson(json)
        assertEquals(1, result.programs)
        assertEquals(1, result.workouts)

        val reExport = BackupManager(dst, clock.fn).exportJson()
        assertEquals(stripTimestamp(json), stripTimestamp(reExport))
        assertEquals("Felt strong, \"PR\" day", dstRepo.observeSessionDetail(s).first()!!.session.notes)
        src.close(); dst.close()
    }

    @Test fun badFile_isRejected_andLeavesDataAlone() = runTest {
        val db = inMemoryDb()
        val repo = WorkoutRepository(db, clock.fn)
        repo.createProgram("Keep me")
        val backup = BackupManager(db, clock.fn)
        for (bad in listOf("not json", "{}", """{"format":"liftlog-backup","version":99}""")) {
            try { backup.importJson(bad); fail("expected failure for $bad") } catch (_: IllegalArgumentException) {}
        }
        // Valid header but broken body: the transaction must not have wiped anything.
        try {
            backup.importJson("""{"format":"liftlog-backup","version":1,"exercises":[{"id":1}]}""")
            fail("expected failure")
        } catch (_: Exception) {}
        assertEquals("Keep me", repo.observePrograms().first().single().name)
        db.close()
    }

    @Test fun csv_containsOnlyCompletedSetsInChosenUnit() = runTest {
        val db = inMemoryDb()
        val repo = WorkoutRepository(db, clock.fn)
        val s = repo.startEmpty()
        repo.addExerciseToSession(s, repo.getOrCreateExercise("Bench Press"), 90)
        repo.addExerciseToSession(s, repo.getOrCreateExercise("Curl, Barbell"), 60)
        val ex = repo.observeSessionDetail(s).first()!!.exercises
        repo.completeSet(ex[0].sets[0].id, 102.05828325, 5) // 225 lb
        repo.completeSet(ex[1].sets[0].id, 20.0, 10)
        repo.addSet(ex[1].row.id) // incomplete, must not be exported
        repo.finishSession(s)
        val lines = BackupManager(db, clock.fn).exportCsv(WeightUnit.LB).trim().lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("date,workout,exercise,set,weight_lb"))
        assertTrue(lines[1], lines[1].contains(",Bench Press,1,225,5,"))
        assertTrue(lines[2], lines[2].contains(",\"Curl, Barbell\",1,44.09,10,"))
        db.close()
    }

    private fun stripTimestamp(json: String) = JSONObject(json).apply { remove("exportedAt") }.toString()
}
