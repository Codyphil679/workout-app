package com.codyphil.liftlog

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.codyphil.liftlog.data.AppDatabase
import com.codyphil.liftlog.data.Muscle
import com.codyphil.liftlog.data.Progression
import com.codyphil.liftlog.data.SetType
import com.codyphil.liftlog.data.WorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Builds a real version-1 database from the exported v1 schema (what 1.0.0 shipped), fills it with data,
 * then opens it with the current app database and checks nothing was lost.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @Test fun v1DatabaseUpgradesWithoutDataLoss() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.codyphil.liftlog.data.AppDatabase/1.json").readText()).getJSONObject("database")

        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { v1 ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                v1.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                val idx = e.optJSONArray("indices")
                if (idx != null) for (j in 0 until idx.length()) {
                    v1.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) v1.execSQL(setup.getString(i))
            v1.version = 1

            fun ins(table: String, vararg kv: Pair<String, Any?>) = v1.insertOrThrow(table, null, ContentValues().apply {
                kv.forEach { (k, v) ->
                    when (v) { null -> putNull(k); is Long -> put(k, v); is Int -> put(k, v); is Double -> put(k, v); else -> put(k, v.toString()) }
                }
            })
            ins("exercises", "id" to 1L, "name" to "Back Squat")
            ins("exercises", "id" to 2L, "name" to "My Custom Lift")
            ins("programs", "id" to 1L, "name" to "Old Program", "notes" to "", "createdAt" to 1L)
            ins("program_days", "id" to 1L, "programId" to 1L, "name" to "Day A", "position" to 0)
            ins("day_exercises", "id" to 1L, "dayId" to 1L, "exerciseId" to 1L, "position" to 0, "targetSets" to 3,
                "targetReps" to 5, "targetWeightKg" to 100.0, "restSeconds" to 180)
            ins("sessions", "id" to 1L, "name" to "Old Workout", "programDayId" to 1L, "startedAt" to 1000L, "endedAt" to 5000L, "notes" to "")
            ins("session_exercises", "id" to 1L, "sessionId" to 1L, "exerciseId" to 1L, "position" to 0, "restSeconds" to 180, "targetReps" to 5)
            ins("set_logs", "id" to 1L, "sessionExerciseId" to 1L, "setNumber" to 1, "weightKg" to 100.0, "reps" to 5,
                "completed" to 1, "completedAt" to 2000L, "restBeforeSeconds" to null)
            ins("set_logs", "id" to 2L, "sessionExerciseId" to 1L, "setNumber" to 2, "weightKg" to 100.0, "reps" to 4,
                "completed" to 1, "completedAt" to 3000L, "restBeforeSeconds" to 150)
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        val repo = WorkoutRepository(db)
        repo.seedIfEmpty()

        val history = repo.observeExerciseHistory(1).first()
        assertEquals(listOf(5, 4), history.map { it.reps })
        val detail = repo.observeSessionDetail(1).first()!!
        assertEquals("Old Workout", detail.session.name)
        assertEquals(listOf(SetType.NORMAL, SetType.NORMAL), detail.exercises.single().sets.map { it.setType })
        assertNull(detail.exercises.single().sets.first().rpe)
        assertEquals(150, detail.exercises.single().sets[1].restBeforeSeconds)
        val de = repo.observeDayExercises(1).first().single()
        assertEquals(Progression.NONE, de.progression) // old program behaves exactly as before
        assertEquals(100.0, de.targetWeightKg, 1e-9)
        assertEquals(Muscle.QUADS, repo.observeExercise(1).first()!!.muscle) // backfilled
        assertEquals(Muscle.OTHER, repo.observeExercise(2).first()!!.muscle)
        assertEquals(1, repo.observeFinishedSessions().first().size)
        db.close()
    }
}
