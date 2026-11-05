package com.codyphil.liftlog.data

import androidx.room.withTransaction
import com.codyphil.liftlog.domain.Format
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.WeightUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Full-fidelity JSON backup (restorable) and a flat CSV of completed sets (for spreadsheets).
 */
class BackupManager(private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.backupDao()

    suspend fun exportJson(): String = db.withTransaction {
        JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAt", clock())
            put("exercises", dao.exercises().toJson { JSONObject().put("id", it.id).put("name", it.name) })
            put("programs", dao.programs().toJson {
                JSONObject().put("id", it.id).put("name", it.name).put("notes", it.notes).put("createdAt", it.createdAt)
            })
            put("programDays", dao.programDays().toJson {
                JSONObject().put("id", it.id).put("programId", it.programId).put("name", it.name).put("position", it.position)
            })
            put("dayExercises", dao.dayExercises().toJson {
                JSONObject().put("id", it.id).put("dayId", it.dayId).put("exerciseId", it.exerciseId)
                    .put("position", it.position).put("targetSets", it.targetSets).put("targetReps", it.targetReps)
                    .put("targetWeightKg", it.targetWeightKg).put("restSeconds", it.restSeconds)
            })
            put("sessions", dao.sessions().toJson {
                JSONObject().put("id", it.id).put("name", it.name).put("programDayId", it.programDayId ?: JSONObject.NULL)
                    .put("startedAt", it.startedAt).put("endedAt", it.endedAt ?: JSONObject.NULL).put("notes", it.notes)
            })
            put("sessionExercises", dao.sessionExercises().toJson {
                JSONObject().put("id", it.id).put("sessionId", it.sessionId).put("exerciseId", it.exerciseId)
                    .put("position", it.position).put("restSeconds", it.restSeconds)
                    .put("targetReps", it.targetReps ?: JSONObject.NULL)
            })
            put("setLogs", dao.setLogs().toJson {
                JSONObject().put("id", it.id).put("sessionExerciseId", it.sessionExerciseId).put("setNumber", it.setNumber)
                    .put("weightKg", it.weightKg).put("reps", it.reps).put("completed", it.completed)
                    .put("completedAt", it.completedAt ?: JSONObject.NULL)
                    .put("restBeforeSeconds", it.restBeforeSeconds ?: JSONObject.NULL)
            })
        }.toString(2)
    }

    /**
     * Replaces ALL data with the backup's contents. The whole import runs in one transaction,
     * so a malformed file leaves existing data untouched.
     */
    suspend fun importJson(json: String): ImportResult {
        val root = try { JSONObject(json) } catch (e: Exception) { throw IllegalArgumentException("Not a valid backup file") }
        require(root.optString("format") == FORMAT) { "Not a LiftLog backup file" }
        val version = root.optInt("version", -1)
        require(version in 1..VERSION) { "Unsupported backup version $version" }

        val exercises = root.getJSONArray("exercises").map { Exercise(it.getLong("id"), it.getString("name")) }
        val programs = root.getJSONArray("programs").map {
            Program(it.getLong("id"), it.getString("name"), it.optString("notes"), it.getLong("createdAt"))
        }
        val days = root.getJSONArray("programDays").map {
            ProgramDay(it.getLong("id"), it.getLong("programId"), it.getString("name"), it.getInt("position"))
        }
        val dayExercises = root.getJSONArray("dayExercises").map {
            DayExercise(
                it.getLong("id"), it.getLong("dayId"), it.getLong("exerciseId"), it.getInt("position"),
                it.getInt("targetSets"), it.getInt("targetReps"), it.getDouble("targetWeightKg"), it.getInt("restSeconds"),
            )
        }
        val sessions = root.getJSONArray("sessions").map {
            WorkoutSession(
                it.getLong("id"), it.getString("name"), it.optLongOrNull("programDayId"),
                it.getLong("startedAt"), it.optLongOrNull("endedAt"), it.optString("notes"),
            )
        }
        val sessionExercises = root.getJSONArray("sessionExercises").map {
            SessionExercise(
                it.getLong("id"), it.getLong("sessionId"), it.getLong("exerciseId"), it.getInt("position"),
                it.getInt("restSeconds"), it.optLongOrNull("targetReps")?.toInt(),
            )
        }
        val sets = root.getJSONArray("setLogs").map {
            SetLog(
                it.getLong("id"), it.getLong("sessionExerciseId"), it.getInt("setNumber"), it.getDouble("weightKg"),
                it.getInt("reps"), it.getBoolean("completed"), it.optLongOrNull("completedAt"),
                it.optLongOrNull("restBeforeSeconds")?.toInt(),
            )
        }

        db.withTransaction {
            dao.clearSetLogs(); dao.clearSessionExercises(); dao.clearSessions()
            dao.clearDayExercises(); dao.clearProgramDays(); dao.clearPrograms(); dao.clearExercises()
            dao.insertExercises(exercises)
            dao.insertPrograms(programs)
            dao.insertProgramDays(days)
            dao.insertDayExercises(dayExercises)
            dao.insertSessions(sessions)
            dao.insertSessionExercises(sessionExercises)
            dao.insertSetLogs(sets)
        }
        return ImportResult(programs.size, sessions.count { it.endedAt != null }, sets.size)
    }

    suspend fun exportCsv(unit: WeightUnit): String {
        val sb = StringBuilder()
        sb.append("date,workout,exercise,set,weight_${unit.label},reps,seconds_since_previous_set\n")
        for (r in dao.csvRows()) {
            sb.append(csv(Format.isoDateTime(r.startedAt))).append(',')
                .append(csv(r.sessionName)).append(',')
                .append(csv(r.exerciseName)).append(',')
                .append(r.setNumber).append(',')
                .append(Units.format(r.weightKg, unit)).append(',')
                .append(r.reps).append(',')
                .append(r.restBeforeSeconds?.toString() ?: "")
                .append('\n')
        }
        return sb.toString()
    }

    data class ImportResult(val programs: Int, val workouts: Int, val sets: Int)

    companion object {
        const val FORMAT = "liftlog-backup"
        const val VERSION = 1

        /** RFC 4180 quoting, plus a leading quote on formula-like cells so spreadsheets don't execute them. */
        fun csv(value: String): String {
            val safe = if (value.isNotEmpty() && value[0] in "=+-@") "'$value" else value
            return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\""
            else safe
        }
    }
}

private inline fun <T> List<T>.toJson(block: (T) -> JSONObject): JSONArray =
    JSONArray().also { arr -> forEach { arr.put(block(it)) } }

private inline fun <T> JSONArray.map(block: (JSONObject) -> T): List<T> =
    (0 until length()).map { block(getJSONObject(it)) }

private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
