package com.codyphil.liftlog.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observe(id: Long): Flow<Exercise?>

    @Query("SELECT * FROM exercises WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Exercise?

    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(exercises: List<Exercise>)

    @Update
    suspend fun update(exercise: Exercise)

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """SELECT (SELECT COUNT(*) FROM day_exercises WHERE exerciseId = :id) +
                  (SELECT COUNT(*) FROM session_exercises WHERE exerciseId = :id)"""
    )
    suspend fun usageCount(id: Long): Int

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int
}

@Dao
interface ProgramDao {
    @Query(
        """SELECT p.id, p.name, p.notes,
                  (SELECT COUNT(*) FROM program_days d WHERE d.programId = p.id) AS dayCount
           FROM programs p ORDER BY p.createdAt DESC"""
    )
    fun observeSummaries(): Flow<List<ProgramSummary>>

    @Query("SELECT * FROM programs WHERE id = :id")
    fun observe(id: Long): Flow<Program?>

    @Query(
        """SELECT d.id, d.programId, p.name AS programName, d.name, d.position,
                  (SELECT COUNT(*) FROM day_exercises de WHERE de.dayId = d.id) AS exerciseCount
           FROM program_days d JOIN programs p ON p.id = d.programId
           ORDER BY p.createdAt DESC, d.position"""
    )
    fun observeAllDays(): Flow<List<DayWithProgram>>

    @Insert
    suspend fun insert(program: Program): Long

    @Update
    suspend fun update(program: Program)

    @Query("DELETE FROM programs WHERE id = :id")
    suspend fun delete(id: Long)

    // Days
    @Query(
        """SELECT d.id, d.programId, d.name, d.position,
                  (SELECT COUNT(*) FROM day_exercises de WHERE de.dayId = d.id) AS exerciseCount
           FROM program_days d WHERE d.programId = :programId ORDER BY d.position"""
    )
    fun observeDays(programId: Long): Flow<List<DaySummary>>

    @Query("SELECT * FROM program_days WHERE id = :id")
    fun observeDay(id: Long): Flow<ProgramDay?>

    @Query("SELECT * FROM program_days WHERE id = :id")
    suspend fun getDay(id: Long): ProgramDay?

    @Query("SELECT * FROM program_days WHERE programId = :programId ORDER BY position")
    suspend fun getDays(programId: Long): List<ProgramDay>

    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun get(id: Long): Program?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM program_days WHERE programId = :programId")
    suspend fun nextDayPosition(programId: Long): Int

    @Insert
    suspend fun insertDay(day: ProgramDay): Long

    @Update
    suspend fun updateDays(days: List<ProgramDay>)

    @Query("DELETE FROM program_days WHERE id = :id")
    suspend fun deleteDay(id: Long)

    // Day exercises
    @Query(
        """SELECT de.id, de.dayId, de.exerciseId, e.name AS exerciseName, de.position,
                  de.targetSets, de.targetReps, de.targetWeightKg, de.restSeconds
           FROM day_exercises de JOIN exercises e ON e.id = de.exerciseId
           WHERE de.dayId = :dayId ORDER BY de.position"""
    )
    fun observeDayExercises(dayId: Long): Flow<List<DayExerciseWithName>>

    @Query("SELECT * FROM day_exercises WHERE dayId = :dayId ORDER BY position")
    suspend fun getDayExercises(dayId: Long): List<DayExercise>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM day_exercises WHERE dayId = :dayId")
    suspend fun nextExercisePosition(dayId: Long): Int

    @Insert
    suspend fun insertDayExercise(item: DayExercise): Long

    @Update
    suspend fun updateDayExercises(items: List<DayExercise>)

    @Query("DELETE FROM day_exercises WHERE id = :id")
    suspend fun deleteDayExercise(id: Long)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutSession?>

    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): WorkoutSession?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<WorkoutSession?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): WorkoutSession?

    @Query(
        """SELECT s.id, s.name, s.programDayId, s.startedAt, s.endedAt,
                  (SELECT COUNT(*) FROM set_logs l JOIN session_exercises se ON se.id = l.sessionExerciseId
                     WHERE se.sessionId = s.id AND l.completed = 1) AS setCount,
                  (SELECT COALESCE(SUM(l.weightKg * l.reps), 0) FROM set_logs l
                     JOIN session_exercises se ON se.id = l.sessionExerciseId
                     WHERE se.sessionId = s.id AND l.completed = 1) AS volumeKg
           FROM sessions s WHERE s.endedAt IS NOT NULL ORDER BY s.startedAt DESC"""
    )
    fun observeFinishedSummaries(): Flow<List<SessionSummary>>

    @Insert
    suspend fun insert(session: WorkoutSession): Long

    @Update
    suspend fun update(session: WorkoutSession)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    // Session exercises
    @Query(
        """SELECT se.id, se.sessionId, se.exerciseId, e.name AS exerciseName, se.position,
                  se.restSeconds, se.targetReps
           FROM session_exercises se JOIN exercises e ON e.id = se.exerciseId
           WHERE se.sessionId = :sessionId ORDER BY se.position"""
    )
    fun observeSessionExercises(sessionId: Long): Flow<List<SessionExerciseRow>>

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY position")
    suspend fun getSessionExercises(sessionId: Long): List<SessionExercise>

    @Query("SELECT * FROM session_exercises WHERE id = :id")
    suspend fun getSessionExercise(id: Long): SessionExercise?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM session_exercises WHERE sessionId = :sessionId")
    suspend fun nextExercisePosition(sessionId: Long): Int

    @Insert
    suspend fun insertSessionExercise(item: SessionExercise): Long

    @Update
    suspend fun updateSessionExercises(items: List<SessionExercise>)

    @Query("DELETE FROM session_exercises WHERE id = :id")
    suspend fun deleteSessionExercise(id: Long)

    // Sets
    @Query(
        """SELECT l.* FROM set_logs l JOIN session_exercises se ON se.id = l.sessionExerciseId
           WHERE se.sessionId = :sessionId ORDER BY l.sessionExerciseId, l.setNumber"""
    )
    fun observeSets(sessionId: Long): Flow<List<SetLog>>

    @Query("SELECT * FROM set_logs WHERE sessionExerciseId = :sessionExerciseId ORDER BY setNumber")
    suspend fun getSets(sessionExerciseId: Long): List<SetLog>

    @Query("SELECT * FROM set_logs WHERE id = :id")
    suspend fun getSet(id: Long): SetLog?

    @Query(
        """SELECT MAX(l.completedAt) FROM set_logs l JOIN session_exercises se ON se.id = l.sessionExerciseId
           WHERE se.sessionId = :sessionId AND l.completed = 1"""
    )
    suspend fun lastCompletedAt(sessionId: Long): Long?

    @Insert
    suspend fun insertSet(set: SetLog): Long

    @Insert
    suspend fun insertSets(sets: List<SetLog>)

    @Update
    suspend fun updateSet(set: SetLog)

    @Update
    suspend fun updateSets(sets: List<SetLog>)

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query(
        """DELETE FROM set_logs WHERE completed = 0 AND sessionExerciseId IN
             (SELECT id FROM session_exercises WHERE sessionId = :sessionId)"""
    )
    suspend fun deleteIncompleteSets(sessionId: Long)

    @Query(
        """DELETE FROM session_exercises WHERE sessionId = :sessionId AND id NOT IN
             (SELECT sessionExerciseId FROM set_logs)"""
    )
    suspend fun deleteEmptySessionExercises(sessionId: Long)
}

@Dao
interface HistoryDao {
    /** Every completed set of an exercise across finished workouts, oldest first. */
    @Query(
        """SELECT s.id AS sessionId, s.name AS sessionName, s.startedAt, l.setNumber, l.weightKg, l.reps
           FROM set_logs l
           JOIN session_exercises se ON se.id = l.sessionExerciseId
           JOIN sessions s ON s.id = se.sessionId
           WHERE se.exerciseId = :exerciseId AND l.completed = 1 AND s.endedAt IS NOT NULL
           ORDER BY s.startedAt, se.position, l.setNumber"""
    )
    fun observeExerciseSets(exerciseId: Long): Flow<List<ExerciseSetRow>>

    /** Completed sets from the most recent finished workout that included this exercise. */
    @Query(
        """SELECT s.id AS sessionId, s.name AS sessionName, s.startedAt, l.setNumber, l.weightKg, l.reps
           FROM set_logs l
           JOIN session_exercises se ON se.id = l.sessionExerciseId
           JOIN sessions s ON s.id = se.sessionId
           WHERE se.exerciseId = :exerciseId AND l.completed = 1 AND s.id = (
               SELECT s2.id FROM sessions s2
               JOIN session_exercises se2 ON se2.sessionId = s2.id
               JOIN set_logs l2 ON l2.sessionExerciseId = se2.id
               WHERE se2.exerciseId = :exerciseId AND l2.completed = 1 AND s2.endedAt IS NOT NULL
               ORDER BY s2.startedAt DESC LIMIT 1)
           ORDER BY se.position, l.setNumber"""
    )
    suspend fun lastSessionSets(exerciseId: Long): List<ExerciseSetRow>

    @Query(
        """SELECT e.id, e.name, MAX(s.startedAt) AS lastPerformed, COUNT(DISTINCT s.id) AS sessionCount
           FROM exercises e
           JOIN session_exercises se ON se.exerciseId = e.id
           JOIN sessions s ON s.id = se.sessionId
           JOIN set_logs l ON l.sessionExerciseId = se.id
           WHERE l.completed = 1 AND s.endedAt IS NOT NULL
           GROUP BY e.id ORDER BY lastPerformed DESC"""
    )
    fun observeExercisesWithHistory(): Flow<List<ExerciseHistorySummary>>
}

/** Raw table access used by backup export/import. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM exercises") suspend fun exercises(): List<Exercise>
    @Query("SELECT * FROM programs") suspend fun programs(): List<Program>
    @Query("SELECT * FROM program_days") suspend fun programDays(): List<ProgramDay>
    @Query("SELECT * FROM day_exercises") suspend fun dayExercises(): List<DayExercise>
    @Query("SELECT * FROM sessions") suspend fun sessions(): List<WorkoutSession>
    @Query("SELECT * FROM session_exercises") suspend fun sessionExercises(): List<SessionExercise>
    @Query("SELECT * FROM set_logs") suspend fun setLogs(): List<SetLog>

    @Insert suspend fun insertExercises(items: List<Exercise>)
    @Insert suspend fun insertPrograms(items: List<Program>)
    @Insert suspend fun insertProgramDays(items: List<ProgramDay>)
    @Insert suspend fun insertDayExercises(items: List<DayExercise>)
    @Insert suspend fun insertSessions(items: List<WorkoutSession>)
    @Insert suspend fun insertSessionExercises(items: List<SessionExercise>)
    @Insert suspend fun insertSetLogs(items: List<SetLog>)

    @Query("DELETE FROM set_logs") suspend fun clearSetLogs()
    @Query("DELETE FROM session_exercises") suspend fun clearSessionExercises()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM day_exercises") suspend fun clearDayExercises()
    @Query("DELETE FROM program_days") suspend fun clearProgramDays()
    @Query("DELETE FROM programs") suspend fun clearPrograms()
    @Query("DELETE FROM exercises") suspend fun clearExercises()

    @Query(
        """SELECT s.startedAt, s.name AS sessionName, e.name AS exerciseName, l.setNumber,
                  l.weightKg, l.reps, l.restBeforeSeconds
           FROM set_logs l
           JOIN session_exercises se ON se.id = l.sessionExerciseId
           JOIN sessions s ON s.id = se.sessionId
           JOIN exercises e ON e.id = se.exerciseId
           WHERE l.completed = 1 AND s.endedAt IS NOT NULL
           ORDER BY s.startedAt, se.position, l.setNumber"""
    )
    suspend fun csvRows(): List<CsvRow>
}

data class CsvRow(
    val startedAt: Long,
    val sessionName: String,
    val exerciseName: String,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val restBeforeSeconds: Int?,
)
