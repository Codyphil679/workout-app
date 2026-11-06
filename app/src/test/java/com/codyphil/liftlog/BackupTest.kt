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
        repo.setSetType(sets[0].id, com.codyphil.liftlog.data.SetType.WARMUP)
        repo.setSetRpe(sets[1].id, 8.5)
        repo.finishSession(s)
        repo.updateSessionNotes(s, "Felt strong, \"PR\" day")
        repo.addBodyEntry(com.codyphil.liftlog.data.BodyMetric.BODY_WEIGHT, 82.5, 1234)

        val json = BackupManager(src, clock.fn).exportJson()

        val dst = inMemoryDb()
        val dstRepo = WorkoutRepository(dst, clock.fn)
        dstRepo.getOrCreateExercise("Something that should be wiped")
        val result = BackupManager(dst, clock.fn).importJson(json)
        assertEquals(1, result.programs)
        assertEquals(1, result.workouts)
        assertEquals(82.5, dstRepo.observeBody(com.codyphil.liftlog.data.BodyMetric.BODY_WEIGHT).first().single().value, 1e-9)
        val restoredSets = dstRepo.observeSessionDetail(s).first()!!.exercises.first().sets
        assertEquals(com.codyphil.liftlog.data.SetType.WARMUP, restoredSets[0].setType)
        assertEquals(8.5, restoredSets[1].rpe!!, 1e-9)

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
        assertTrue(lines[0].startsWith("date,workout,exercise,set,set_type,weight_lb,reps,rpe"))
        assertTrue(lines[1], lines[1].contains(",Bench Press,1,normal,225,5,,"))
        assertTrue(lines[2], lines[2].contains(",\"Curl, Barbell\",1,normal,44.09,10,,"))
        db.close()
    }

    @Test fun version1BackupFile_stillRestores() = runTest {
        val v1 = """
            {"format":"liftlog-backup","version":1,"exportedAt":1,
             "exercises":[{"id":1,"name":"Back Squat"}],
             "programs":[{"id":1,"name":"Old","notes":"","createdAt":1}],
             "programDays":[{"id":1,"programId":1,"name":"A","position":0}],
             "dayExercises":[{"id":1,"dayId":1,"exerciseId":1,"position":0,"targetSets":3,"targetReps":5,"targetWeightKg":100.0,"restSeconds":180}],
             "sessions":[{"id":1,"name":"W","programDayId":1,"startedAt":10,"endedAt":20,"notes":""}],
             "sessionExercises":[{"id":1,"sessionId":1,"exerciseId":1,"position":0,"restSeconds":180,"targetReps":5}],
             "setLogs":[{"id":1,"sessionExerciseId":1,"setNumber":1,"weightKg":100.0,"reps":5,"completed":true,"completedAt":15,"restBeforeSeconds":null}]}
        """.trimIndent()
        val db = inMemoryDb()
        val r = BackupManager(db, clock.fn).importJson(v1)
        assertEquals(1, r.workouts)
        val repo = WorkoutRepository(db, clock.fn)
        assertEquals(com.codyphil.liftlog.data.SetType.NORMAL, repo.observeSessionDetail(1).first()!!.exercises.single().sets.single().setType)
        assertEquals(com.codyphil.liftlog.data.Progression.NONE, repo.observeDayExercises(1).first().single().progression)
        db.close()
    }

    private fun stripTimestamp(json: String) = JSONObject(json).apply { remove("exportedAt") }.toString()
}
