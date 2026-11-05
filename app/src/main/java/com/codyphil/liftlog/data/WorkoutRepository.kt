package com.codyphil.liftlog.data

import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class WorkoutRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val exerciseDao = db.exerciseDao()
    private val programDao = db.programDao()
    private val sessionDao = db.sessionDao()
    private val historyDao = db.historyDao()

    suspend fun seedIfEmpty() {
        if (exerciseDao.count() == 0) {
            exerciseDao.insertAllIgnore(Seed.exercises.map { Exercise(name = it) })
        }
    }

    // ---------- Exercises ----------

    fun observeExercises(): Flow<List<Exercise>> = exerciseDao.observeAll()
    fun observeExercise(id: Long): Flow<Exercise?> = exerciseDao.observe(id)

    /** Returns the id of the exercise with this name, creating it if needed (case-insensitive match). */
    suspend fun getOrCreateExercise(name: String): Long {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        require(clean.isNotEmpty()) { "Exercise name is empty" }
        exerciseDao.findByName(clean)?.let { return it.id }
        // IGNORE + re-read handles a concurrent insert of the same name (e.g. first-launch seeding).
        val id = exerciseDao.insertIgnore(Exercise(name = clean))
        return if (id != -1L) id else requireNotNull(exerciseDao.findByName(clean)).id
    }

    /** Returns false if another exercise already has that name. */
    suspend fun renameExercise(id: Long, name: String): Boolean {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return false
        val existing = exerciseDao.findByName(clean)
        if (existing != null && existing.id != id) return false
        exerciseDao.update(Exercise(id = id, name = clean))
        return true
    }

    /** Only deletes exercises that are not referenced by any program or workout. */
    suspend fun deleteExerciseIfUnused(id: Long): Boolean = db.withTransaction {
        if (exerciseDao.usageCount(id) > 0) false else {
            exerciseDao.delete(id); true
        }
    }

    // ---------- Programs ----------

    fun observePrograms(): Flow<List<ProgramSummary>> = programDao.observeSummaries()
    fun observeProgram(id: Long): Flow<Program?> = programDao.observe(id)
    fun observeAllDays(): Flow<List<DayWithProgram>> = programDao.observeAllDays()
    fun observeDays(programId: Long): Flow<List<DaySummary>> = programDao.observeDays(programId)
    fun observeDay(dayId: Long): Flow<ProgramDay?> = programDao.observeDay(dayId)
    fun observeDayExercises(dayId: Long): Flow<List<DayExerciseWithName>> = programDao.observeDayExercises(dayId)

    suspend fun createProgram(name: String, notes: String = ""): Long =
        programDao.insert(Program(name = name.trim(), notes = notes.trim(), createdAt = clock()))

    suspend fun createFromTemplate(template: Templates.Template): Long = db.withTransaction {
        val programId = createProgram(template.name, template.description)
        template.days.forEach { day ->
            val dayId = addDay(programId, day.name)
            day.items.forEach { item ->
                addDayExercise(dayId, getOrCreateExercise(item.exercise), item.sets, item.reps, 0.0, item.rest)
            }
        }
        programId
    }

    suspend fun updateProgram(id: Long, name: String, notes: String) {
        val p = programDao.get(id) ?: return
        programDao.update(p.copy(name = name.trim(), notes = notes.trim()))
    }

    suspend fun deleteProgram(id: Long) = programDao.delete(id)

    suspend fun addDay(programId: Long, name: String): Long = db.withTransaction {
        programDao.insertDay(
            ProgramDay(programId = programId, name = name.trim(), position = programDao.nextDayPosition(programId))
        )
    }

    suspend fun renameDay(dayId: Long, name: String) {
        val d = programDao.getDay(dayId) ?: return
        programDao.updateDays(listOf(d.copy(name = name.trim())))
    }

    suspend fun deleteDay(dayId: Long) = programDao.deleteDay(dayId)

    suspend fun moveDay(dayId: Long, delta: Int) = db.withTransaction {
        val day = programDao.getDay(dayId) ?: return@withTransaction
        val days = programDao.getDays(day.programId).toMutableList()
        val from = days.indexOfFirst { it.id == dayId }
        val to = (from + delta).coerceIn(0, days.lastIndex)
        if (from == to) return@withTransaction
        days.add(to, days.removeAt(from))
        programDao.updateDays(days.mapIndexed { i, d -> d.copy(position = i) })
    }

    suspend fun addDayExercise(
        dayId: Long, exerciseId: Long, sets: Int, reps: Int, weightKg: Double, restSeconds: Int,
    ): Long = db.withTransaction {
        programDao.insertDayExercise(
            DayExercise(
                dayId = dayId, exerciseId = exerciseId, position = programDao.nextExercisePosition(dayId),
                targetSets = sets, targetReps = reps, targetWeightKg = weightKg, restSeconds = restSeconds,
            )
        )
    }

    suspend fun updateDayExercise(id: Long, dayId: Long, sets: Int, reps: Int, weightKg: Double, restSeconds: Int) =
        db.withTransaction {
            val item = programDao.getDayExercises(dayId).firstOrNull { it.id == id } ?: return@withTransaction
            programDao.updateDayExercises(
                listOf(item.copy(targetSets = sets, targetReps = reps, targetWeightKg = weightKg, restSeconds = restSeconds))
            )
        }

    suspend fun deleteDayExercise(id: Long) = programDao.deleteDayExercise(id)

    suspend fun moveDayExercise(id: Long, dayId: Long, delta: Int) = db.withTransaction {
        val items = programDao.getDayExercises(dayId).toMutableList()
        val from = items.indexOfFirst { it.id == id }
        if (from < 0) return@withTransaction
        val to = (from + delta).coerceIn(0, items.lastIndex)
        if (from == to) return@withTransaction
        items.add(to, items.removeAt(from))
        programDao.updateDayExercises(items.mapIndexed { i, d -> d.copy(position = i) })
    }

    // ---------- Workouts ----------

    fun observeActiveSession(): Flow<WorkoutSession?> = sessionDao.observeActive()
    fun observeFinishedSessions(): Flow<List<SessionSummary>> = sessionDao.observeFinishedSummaries()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSessionDetail(sessionId: Long): Flow<SessionDetail?> =
        sessionDao.observe(sessionId).flatMapLatest { session ->
            if (session == null) flowOf(null)
            else combine(
                sessionDao.observeSessionExercises(sessionId),
                sessionDao.observeSets(sessionId),
            ) { exercises, sets ->
                val bySe = sets.groupBy { it.sessionExerciseId }
                SessionDetail(session, exercises.map { SessionExerciseWithSets(it, bySe[it.id].orEmpty()) })
            }
        }

    suspend fun lastTimeSets(exerciseId: Long): List<ExerciseSetRow> = historyDao.lastSessionSets(exerciseId)

    /** Starts a workout from a program day. Returns the existing active workout's id instead if one is running. */
    suspend fun startFromDay(dayId: Long): Long = db.withTransaction {
        sessionDao.getActive()?.let { return@withTransaction it.id }
        val day = requireNotNull(programDao.getDay(dayId)) { "Day $dayId not found" }
        val program = programDao.get(day.programId)
        val sessionId = sessionDao.insert(
            WorkoutSession(
                name = listOfNotNull(program?.name, day.name).joinToString(" · "),
                programDayId = dayId,
                startedAt = clock(),
            )
        )
        programDao.getDayExercises(dayId).forEachIndexed { index, de ->
            val seId = sessionDao.insertSessionExercise(
                SessionExercise(
                    sessionId = sessionId, exerciseId = de.exerciseId, position = index,
                    restSeconds = de.restSeconds, targetReps = de.targetReps,
                )
            )
            val last = historyDao.lastSessionSets(de.exerciseId)
            sessionDao.insertSets((1..de.targetSets.coerceAtLeast(1)).map { n ->
                // Prefill with what you lifted last time for this set; fall back to the program's starting weight.
                val prev = last.getOrNull(n - 1) ?: last.lastOrNull()
                SetLog(sessionExerciseId = seId, setNumber = n, weightKg = prev?.weightKg ?: de.targetWeightKg, reps = de.targetReps)
            })
        }
        sessionId
    }

    suspend fun startEmpty(): Long = db.withTransaction {
        sessionDao.getActive()?.id ?: sessionDao.insert(
            WorkoutSession(name = "Quick Workout", programDayId = null, startedAt = clock())
        )
    }

    suspend fun addExerciseToSession(sessionId: Long, exerciseId: Long, restSeconds: Int) = db.withTransaction {
        val seId = sessionDao.insertSessionExercise(
            SessionExercise(
                sessionId = sessionId, exerciseId = exerciseId,
                position = sessionDao.nextExercisePosition(sessionId), restSeconds = restSeconds,
            )
        )
        val last = historyDao.lastSessionSets(exerciseId)
        val sets = if (last.isEmpty()) listOf(SetLog(sessionExerciseId = seId, setNumber = 1, weightKg = 0.0, reps = 0))
        else last.mapIndexed { i, s -> SetLog(sessionExerciseId = seId, setNumber = i + 1, weightKg = s.weightKg, reps = s.reps) }
        sessionDao.insertSets(sets)
    }

    suspend fun removeSessionExercise(sessionExerciseId: Long) = sessionDao.deleteSessionExercise(sessionExerciseId)

    suspend fun setSessionExerciseRest(sessionExerciseId: Long, restSeconds: Int) {
        val se = sessionDao.getSessionExercise(sessionExerciseId) ?: return
        sessionDao.updateSessionExercises(listOf(se.copy(restSeconds = restSeconds)))
    }

    suspend fun moveSessionExercise(sessionExerciseId: Long, delta: Int) = db.withTransaction {
        val se = sessionDao.getSessionExercise(sessionExerciseId) ?: return@withTransaction
        val items = sessionDao.getSessionExercises(se.sessionId).toMutableList()
        val from = items.indexOfFirst { it.id == sessionExerciseId }
        val to = (from + delta).coerceIn(0, items.lastIndex)
        if (from == to) return@withTransaction
        items.add(to, items.removeAt(from))
        sessionDao.updateSessionExercises(items.mapIndexed { i, s -> s.copy(position = i) })
    }

    suspend fun addSet(sessionExerciseId: Long) = db.withTransaction {
        val sets = sessionDao.getSets(sessionExerciseId)
        val last = sets.lastOrNull()
        sessionDao.insertSet(
            SetLog(
                sessionExerciseId = sessionExerciseId,
                setNumber = (last?.setNumber ?: 0) + 1,
                weightKg = last?.weightKg ?: 0.0,
                reps = last?.reps ?: 0,
            )
        )
    }

    suspend fun deleteSet(setId: Long) = db.withTransaction {
        val set = sessionDao.getSet(setId) ?: return@withTransaction
        sessionDao.deleteSet(setId)
        val remaining = sessionDao.getSets(set.sessionExerciseId)
        sessionDao.updateSets(remaining.mapIndexed { i, s -> s.copy(setNumber = i + 1) })
    }

    suspend fun updateSetValues(setId: Long, weightKg: Double, reps: Int) {
        val set = sessionDao.getSet(setId) ?: return
        if (set.weightKg != weightKg || set.reps != reps) sessionDao.updateSet(set.copy(weightKg = weightKg, reps = reps))
    }

    /**
     * Marks a set done. Returns the rest time (seconds) configured for its exercise so the caller can start the timer,
     * or null if the set no longer exists.
     */
    suspend fun completeSet(setId: Long, weightKg: Double, reps: Int): Int? = db.withTransaction {
        val set = sessionDao.getSet(setId) ?: return@withTransaction null
        val se = sessionDao.getSessionExercise(set.sessionExerciseId) ?: return@withTransaction null
        val now = clock()
        val previous = sessionDao.lastCompletedAt(se.sessionId)
        sessionDao.updateSet(
            set.copy(
                weightKg = weightKg, reps = reps, completed = true, completedAt = now,
                restBeforeSeconds = previous?.let { ((now - it) / 1000).toInt().coerceAtLeast(0) },
            )
        )
        se.restSeconds
    }

    suspend fun uncompleteSet(setId: Long) {
        val set = sessionDao.getSet(setId) ?: return
        sessionDao.updateSet(set.copy(completed = false, completedAt = null, restBeforeSeconds = null))
    }

    /**
     * Ends the workout: drops unfinished sets and empty exercises. Returns false (and changes nothing)
     * when there are no completed sets, so the UI can offer to discard instead.
     */
    suspend fun finishSession(sessionId: Long): Boolean = db.withTransaction {
        if (sessionDao.lastCompletedAt(sessionId) == null) return@withTransaction false
        sessionDao.deleteIncompleteSets(sessionId)
        sessionDao.deleteEmptySessionExercises(sessionId)
        val session = sessionDao.get(sessionId) ?: return@withTransaction false
        sessionDao.update(session.copy(endedAt = clock()))
        true
    }

    suspend fun updateSessionNotes(sessionId: Long, notes: String) {
        val s = sessionDao.get(sessionId) ?: return
        sessionDao.update(s.copy(notes = notes))
    }

    suspend fun renameSession(sessionId: Long, name: String) {
        val s = sessionDao.get(sessionId) ?: return
        if (name.isNotBlank()) sessionDao.update(s.copy(name = name.trim()))
    }

    suspend fun deleteSession(sessionId: Long) = sessionDao.delete(sessionId)

    // ---------- History / progress ----------

    fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseSetRow>> = historyDao.observeExerciseSets(exerciseId)
    fun observeExercisesWithHistory(): Flow<List<ExerciseHistorySummary>> = historyDao.observeExercisesWithHistory()
}
