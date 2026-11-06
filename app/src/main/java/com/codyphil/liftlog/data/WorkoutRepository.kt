package com.codyphil.liftlog.data

import androidx.room.withTransaction
import com.codyphil.liftlog.domain.PercentScheme
import com.codyphil.liftlog.domain.Plan
import com.codyphil.liftlog.domain.ProgressionConfig
import com.codyphil.liftlog.domain.TrainingMax
import com.codyphil.liftlog.domain.Progressions
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.Warmups
import com.codyphil.liftlog.domain.WeightUnit
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
    private val bodyDao = db.bodyDao()

    /** Adds missing built-in exercises and fills in muscle groups for ones created before muscles existed. */
    suspend fun seedIfEmpty() = db.withTransaction {
        for ((name, muscle) in Seed.muscles) {
            if (exerciseDao.findByName(name) == null) exerciseDao.insertIgnore(Exercise(name = name, muscle = muscle))
            else exerciseDao.backfillMuscle(name, muscle)
        }
    }

    // ---------- Exercises ----------

    fun observeExercises(): Flow<List<Exercise>> = exerciseDao.observeAll()
    fun observeExercise(id: Long): Flow<Exercise?> = exerciseDao.observe(id)

    /** Returns the id of the exercise with this name, creating it if needed (case-insensitive match). */
    suspend fun getOrCreateExercise(name: String, muscle: Muscle = Seed.muscleFor(name)): Long {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        require(clean.isNotEmpty()) { "Exercise name is empty" }
        exerciseDao.findByName(clean)?.let { return it.id }
        // IGNORE + re-read handles a concurrent insert of the same name (e.g. first-launch seeding).
        val id = exerciseDao.insertIgnore(Exercise(name = clean, muscle = muscle))
        return if (id != -1L) id else requireNotNull(exerciseDao.findByName(clean)).id
    }

    /** Returns false if another exercise already has that name. */
    suspend fun renameExercise(id: Long, name: String): Boolean {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return false
        val existing = exerciseDao.findByName(clean)
        if (existing != null && existing.id != id) return false
        exerciseDao.rename(id, clean)
        return true
    }

    suspend fun setExerciseMuscle(id: Long, muscle: Muscle) = exerciseDao.setMuscle(id, muscle)

    suspend fun setTrainingMax(exerciseId: Long, kg: Double?) = exerciseDao.setTrainingMax(exerciseId, kg)

    /** 90% of the best estimated 1RM in your history, as a starting training max. Null without history. */
    suspend fun suggestedTrainingMax(exerciseId: Long, unit: WeightUnit): Double? =
        historyDao.bestE1rm(exerciseId)?.let { TrainingMax.fromE1rm(it, unit) }

    /**
     * Saves a new training max and recalculates the unfinished working sets of that exercise
     * in the given workout from its % scheme.
     */
    suspend fun applyTrainingMax(sessionExerciseId: Long, kg: Double, unit: WeightUnit) = db.withTransaction {
        val se = sessionDao.getSessionExercise(sessionExerciseId) ?: return@withTransaction
        exerciseDao.setTrainingMax(se.exerciseId, kg)
        val scheme = PercentScheme.parse(se.percentScheme)
        if (scheme.isEmpty()) return@withTransaction
        val session = sessionDao.get(se.sessionId)
        val intensity = session?.intensity ?: 1.0
        val working = sessionDao.getSets(sessionExerciseId).filter { it.setType != SetType.WARMUP }
        sessionDao.updateSets(working.mapIndexedNotNull { i, set ->
            val ps = scheme.getOrNull(i) ?: return@mapIndexedNotNull null
            if (set.completed) null
            else set.copy(weightKg = lighter(PercentScheme.weightFor(kg, ps.percent, unit), intensity, unit))
        })
        sessionDao.updateSessionExercises(listOf(se.copy(note = "Training max ${Units.formatWithUnit(kg, unit)}" +
            if (scheme.any { it.amrap }) " · + set: as many reps as you can" else "")))
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

    suspend fun createFromTemplate(template: Templates.Template, unit: WeightUnit = WeightUnit.LB): Long = db.withTransaction {
        val programId = createProgram(template.name, template.description)
        template.days.forEach { day ->
            val dayId = addDay(programId, day.name)
            day.items.forEach { item ->
                val d = Progressions.defaultFor(item.exercise, item.reps, unit)
                val progression = item.progression ?: d.progression
                addDayExercise(
                    dayId, getOrCreateExercise(item.exercise), item.sets, item.reps, 0.0, item.rest,
                    progression = progression,
                    incrementKg = d.incrementKg,
                    repMax = if (progression == Progression.DOUBLE) item.repMax ?: (item.reps + 4) else null,
                    autoWarmup = item.autoWarmup,
                    supersetGroup = item.superset,
                    percentScheme = item.percentScheme,
                )
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
        progression: Progression = Progression.NONE,
        incrementKg: Double = 0.0,
        repMax: Int? = null,
        autoWarmup: Boolean = false,
        supersetGroup: Int? = null,
        percentScheme: String? = null,
    ): Long = db.withTransaction {
        programDao.insertDayExercise(
            DayExercise(
                dayId = dayId, exerciseId = exerciseId, position = programDao.nextExercisePosition(dayId),
                targetSets = sets, targetReps = reps, targetWeightKg = weightKg, restSeconds = restSeconds,
                progression = progression, incrementKg = incrementKg, repMax = repMax, autoWarmup = autoWarmup,
                supersetGroup = supersetGroup, percentScheme = percentScheme,
            )
        )
    }

    suspend fun updateDayExercise(
        id: Long, dayId: Long, sets: Int, reps: Int, weightKg: Double, restSeconds: Int,
        progression: Progression = Progression.NONE,
        incrementKg: Double = 0.0,
        repMax: Int? = null,
        autoWarmup: Boolean = false,
        percentScheme: String? = null,
    ) = db.withTransaction {
        val item = programDao.getDayExercises(dayId).firstOrNull { it.id == id } ?: return@withTransaction
        programDao.updateDayExercises(
            listOf(
                item.copy(
                    targetSets = sets, targetReps = reps, targetWeightKg = weightKg, restSeconds = restSeconds,
                    progression = progression, incrementKg = incrementKg, repMax = repMax, autoWarmup = autoWarmup,
                    percentScheme = percentScheme,
                )
            )
        )
    }

    suspend fun deleteDayExercise(id: Long) = db.withTransaction {
        val dayId = programDao.getDayExerciseDayId(id) ?: return@withTransaction
        programDao.deleteDayExercise(id)
        val items = programDao.getDayExercises(dayId)
        programDao.updateDayExercises(Supersets.dissolveSingletons(items, { it.supersetGroup }) { it.copy(supersetGroup = null) })
    }

    suspend fun moveDayExercise(id: Long, dayId: Long, delta: Int) = db.withTransaction {
        val items = programDao.getDayExercises(dayId).toMutableList()
        val from = items.indexOfFirst { it.id == id }
        if (from < 0) return@withTransaction
        val to = (from + delta).coerceIn(0, items.lastIndex)
        if (from == to) return@withTransaction
        items.add(to, items.removeAt(from))
        programDao.updateDayExercises(items.mapIndexed { i, d -> d.copy(position = i) })
    }

    /** [link] = true joins this exercise with the next one; false removes it from its superset. */
    suspend fun setDaySuperset(id: Long, dayId: Long, link: Boolean) = db.withTransaction {
        val items = programDao.getDayExercises(dayId)
        val updated = Supersets.apply(items, items.indexOfFirst { it.id == id }, link, { it.supersetGroup }) { e, g ->
            e.copy(supersetGroup = g)
        }
        programDao.updateDayExercises(updated)
    }

    // ---------- Workouts ----------

    fun observeActiveSession(): Flow<WorkoutSession?> = sessionDao.observeActive()
    suspend fun hasActiveSession(): Boolean = sessionDao.getActive() != null
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

    /** Working sets from the most recent finished workout with this exercise. */
    suspend fun lastTimeSets(exerciseId: Long): List<ExerciseSetRow> = historyDao.lastSessionSets(exerciseId)

    /**
     * Starts a workout from a program day, applying each exercise's progression rule.
     * Returns the existing active workout's id instead if one is running.
     * @param barDisplay empty-bar weight in [unit], used for automatic warm-up sets.
     */
    suspend fun startFromDay(
        dayId: Long,
        unit: WeightUnit = WeightUnit.LB,
        barDisplay: Double = defaultBar(unit),
        intensity: Double = 1.0,
        readiness: Double? = null,
    ): Long =
        db.withTransaction {
            sessionDao.getActive()?.let { return@withTransaction it.id }
            val day = requireNotNull(programDao.getDay(dayId)) { "Day $dayId not found" }
            val program = programDao.get(day.programId)
            val sessionId = sessionDao.insert(
                WorkoutSession(
                    name = listOfNotNull(program?.name, day.name).joinToString(" · "),
                    programDayId = dayId,
                    startedAt = clock(),
                    readiness = readiness,
                    intensity = intensity,
                )
            )
            programDao.getDayExercises(dayId).forEachIndexed { index, de ->
                val plan = planFor(de, unit)
                val working = plan.sets.map { (kg, reps) -> lighter(kg, intensity, unit) to reps }
                val note = if (intensity < 0.999) {
                    "Lighter day (−${Math.round((1 - intensity) * 100)}%)" + (plan.note?.let { " · planned: $it" } ?: "")
                } else plan.note
                val seId = sessionDao.insertSessionExercise(
                    SessionExercise(
                        sessionId = sessionId, exerciseId = de.exerciseId, position = index,
                        restSeconds = de.restSeconds, targetReps = de.targetReps,
                        supersetGroup = de.supersetGroup, note = note,
                        repMax = if (de.progression == Progression.DOUBLE) de.repMax else null,
                        percentScheme = if (de.progression == Progression.PERCENT) de.percentScheme else null,
                    )
                )
                val warmups = if (de.autoWarmup) warmupSets(working.first().first, unit, barDisplay) else emptyList()
                val logs = warmups.map { (kg, reps) ->
                    SetLog(sessionExerciseId = seId, setNumber = 0, weightKg = kg, reps = reps, setType = SetType.WARMUP)
                } + working.mapIndexed { i, (kg, reps) ->
                    val amrap = plan.amrap.getOrElse(i) { false }
                    SetLog(sessionExerciseId = seId, setNumber = 0, weightKg = kg, reps = reps,
                        amrap = amrap, targetReps = if (amrap) reps else null)
                }
                sessionDao.insertSets(logs.mapIndexed { i, l -> l.copy(setNumber = i + 1) })
            }
            sessionId
        }

    /** Lighter-day scaling, rounded down to a loadable weight. */
    private fun lighter(kg: Double, intensity: Double, unit: WeightUnit): Double =
        if (intensity >= 0.999 || kg <= 0) kg else Progressions.roundDownLoadable(kg * intensity, unit)

    private suspend fun planFor(de: DayExercise, unit: WeightUnit): Plan {
        val recent = historyDao.recentSessionSets(de.exerciseId, 2)
            .groupBy { it.sessionId }.values.toList() // query is newest-first; groupBy keeps first-seen order
        var tm: Double? = null
        var tmNote: String? = null
        if (de.progression == Progression.PERCENT) {
            tm = exerciseDao.get(de.exerciseId)?.trainingMaxKg
            if (tm == null) {
                // No training max yet: start from 90% of the best estimated 1RM in history, and remember it.
                tm = suggestedTrainingMax(de.exerciseId, unit)
                if (tm != null) {
                    exerciseDao.setTrainingMax(de.exerciseId, tm)
                    tmNote = "Training max set to ${Units.formatWithUnit(tm, unit)} (90% of your best estimated 1RM)"
                }
            }
        }
        val cfg = ProgressionConfig(
            type = de.progression,
            targetSets = de.targetSets,
            repMin = de.targetReps,
            repMax = de.repMax ?: de.targetReps,
            startWeightKg = de.targetWeightKg,
            incrementKg = de.incrementKg,
            scheme = PercentScheme.parse(de.percentScheme),
            trainingMaxKg = tm,
        )
        val plan = Progressions.plan(cfg, recent, unit)
        return if (tmNote != null) plan.copy(note = tmNote) else plan
    }

    private fun warmupSets(workKg: Double, unit: WeightUnit, barDisplay: Double): List<Pair<Double, Int>> =
        Warmups.forWorkingWeight(Units.fromKg(workKg, unit), barDisplay, unit).map { (w, r) -> Units.toKg(w, unit) to r }

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

    suspend fun removeSessionExercise(sessionExerciseId: Long) = db.withTransaction {
        val se = sessionDao.getSessionExercise(sessionExerciseId) ?: return@withTransaction
        sessionDao.deleteSessionExercise(sessionExerciseId)
        val items = sessionDao.getSessionExercises(se.sessionId)
        sessionDao.updateSessionExercises(Supersets.dissolveSingletons(items, { it.supersetGroup }) { it.copy(supersetGroup = null) })
    }

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

    suspend fun setSessionSuperset(sessionExerciseId: Long, link: Boolean) = db.withTransaction {
        val se = sessionDao.getSessionExercise(sessionExerciseId) ?: return@withTransaction
        val items = sessionDao.getSessionExercises(se.sessionId)
        val updated = Supersets.apply(items, items.indexOfFirst { it.id == sessionExerciseId }, link, { it.supersetGroup }) { e, g ->
            e.copy(supersetGroup = g)
        }
        sessionDao.updateSessionExercises(updated)
    }

    suspend fun addSet(sessionExerciseId: Long) = db.withTransaction {
        val sets = sessionDao.getSets(sessionExerciseId)
        val last = sets.lastOrNull { it.setType != SetType.WARMUP } ?: sets.lastOrNull()
        sessionDao.insertSet(
            SetLog(
                sessionExerciseId = sessionExerciseId,
                setNumber = (sets.lastOrNull()?.setNumber ?: 0) + 1,
                weightKg = last?.weightKg ?: 0.0,
                reps = last?.reps ?: 0,
            )
        )
    }

    /**
     * Replaces any unfinished warm-up sets with a fresh ramp up to the first working set's weight.
     * Returns false when there's no working weight to warm up to (or it's too light to need one).
     */
    suspend fun addWarmupSets(sessionExerciseId: Long, unit: WeightUnit, barDisplay: Double): Boolean = db.withTransaction {
        val sets = sessionDao.getSets(sessionExerciseId)
        val work = sets.firstOrNull { it.setType != SetType.WARMUP }?.weightKg ?: return@withTransaction false
        val ramp = warmupSets(work, unit, barDisplay)
        if (ramp.isEmpty()) return@withTransaction false
        sets.filter { it.setType == SetType.WARMUP && !it.completed }.forEach { sessionDao.deleteSet(it.id) }
        val keptWarmups = sets.filter { it.setType == SetType.WARMUP && it.completed }
        val working = sets.filter { it.setType != SetType.WARMUP }
        sessionDao.insertSets(ramp.map { (kg, reps) ->
            SetLog(sessionExerciseId = sessionExerciseId, setNumber = 0, weightKg = kg, reps = reps, setType = SetType.WARMUP)
        })
        // Renumber: completed warm-ups, new warm-ups, then working sets.
        val fresh = sessionDao.getSets(sessionExerciseId).filter { it.setType == SetType.WARMUP && !it.completed }
        val ordered = keptWarmups + fresh + working
        sessionDao.updateSets(ordered.mapIndexed { i, s -> s.copy(setNumber = i + 1) })
        true
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

    suspend fun setSetType(setId: Long, type: SetType) {
        val set = sessionDao.getSet(setId) ?: return
        sessionDao.updateSet(set.copy(setType = type))
    }

    suspend fun setAmrap(setId: Long, amrap: Boolean) {
        val set = sessionDao.getSet(setId) ?: return
        sessionDao.updateSet(set.copy(amrap = amrap, targetReps = if (amrap) (set.targetReps ?: set.reps) else null))
    }

    suspend fun setSetRpe(setId: Long, rpe: Double?) {
        val set = sessionDao.getSet(setId) ?: return
        sessionDao.updateSet(set.copy(rpe = rpe))
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
    suspend fun finishSession(sessionId: Long, unit: WeightUnit = WeightUnit.LB): Boolean = db.withTransaction {
        if (sessionDao.lastCompletedAt(sessionId) == null) return@withTransaction false
        sessionDao.deleteIncompleteSets(sessionId)
        sessionDao.deleteEmptySessionExercises(sessionId)
        val session = sessionDao.get(sessionId) ?: return@withTransaction false
        val items = Supersets.dissolveSingletons(sessionDao.getSessionExercises(sessionId), { it.supersetGroup }) {
            it.copy(supersetGroup = null)
        }.map { se -> if (session.intensity >= 0.999) updateTrainingMax(se, unit) ?: se else se }
        sessionDao.updateSessionExercises(items)
        sessionDao.update(session.copy(endedAt = clock()))
        true
    }

    /** Applies the AMRAP rule to the exercise's training max. Returns the session exercise with the change noted. */
    private suspend fun updateTrainingMax(se: SessionExercise, unit: WeightUnit): SessionExercise? {
        val exercise = exerciseDao.get(se.exerciseId) ?: return null
        val tm = exercise.trainingMaxKg ?: return null
        val amrap = sessionDao.getSets(se.id).lastOrNull { it.amrap && it.completed && it.setType != SetType.WARMUP } ?: return null
        val target = amrap.targetReps ?: return null
        val inc = Progressions.defaultFor(exercise.name, target, unit).incrementKg
        val change = TrainingMax.afterAmrap(tm, target, amrap.reps, inc, unit)
        exerciseDao.setTrainingMax(exercise.id, change.newKg)
        return se.copy(note = change.note)
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
    fun observeMuscleSets(since: Long): Flow<List<MuscleSets>> = historyDao.observeMuscleSets(since)
    fun observeMuscleEvents(since: Long): Flow<List<MuscleSetEvent>> = historyDao.observeMuscleEvents(since)
    suspend fun recapRows(until: Long): List<RecapSetRow> = historyDao.recapRows(until)

    // ---------- Body ----------

    fun observeBody(metric: BodyMetric): Flow<List<BodyEntry>> = bodyDao.observe(metric)
    fun observeAllBody(): Flow<List<BodyEntry>> = bodyDao.observeAll()
    suspend fun addBodyEntry(metric: BodyMetric, value: Double, at: Long = clock()): Long =
        bodyDao.insert(BodyEntry(metric = metric, value = value, recordedAt = at))
    suspend fun deleteBodyEntry(id: Long) = bodyDao.delete(id)

    companion object {
        fun defaultBar(unit: WeightUnit) = if (unit == WeightUnit.LB) 45.0 else 20.0
    }
}

/** Pure helpers for superset grouping, shared by program days and workouts. */
object Supersets {
    /**
     * Links item [index] with the next item ([link] = true) or removes it from its group ([link] = false).
     * Returns the full list with updated groups; groups left with one member are dissolved.
     */
    fun <T> apply(items: List<T>, index: Int, link: Boolean, group: (T) -> Int?, withGroup: (T, Int?) -> T): List<T> {
        if (index !in items.indices) return items
        val out = items.toMutableList()
        if (link) {
            if (index + 1 > out.lastIndex) return items
            val g = group(out[index]) ?: group(out[index + 1]) ?: ((out.mapNotNull(group).maxOrNull() ?: 0) + 1)
            out[index] = withGroup(out[index], g)
            out[index + 1] = withGroup(out[index + 1], g)
        } else {
            out[index] = withGroup(out[index], null)
        }
        return dissolveSingletons(out, group) { withGroup(it, null) }
    }

    fun <T> dissolveSingletons(items: List<T>, group: (T) -> Int?, clear: (T) -> T): List<T> {
        val counts = items.mapNotNull(group).groupingBy { it }.eachCount()
        return items.map { val g = group(it); if (g != null && counts[g] == 1) clear(it) else it }
    }
}
