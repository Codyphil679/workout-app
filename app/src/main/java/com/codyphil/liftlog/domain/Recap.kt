package com.codyphil.liftlog.domain

import com.codyphil.liftlog.data.Muscle
import com.codyphil.liftlog.data.RecapSetRow
import java.util.Calendar
import java.util.Locale

data class RecapPr(val exerciseName: String, val e1rmKg: Double, val previousKg: Double)

data class MonthRecap(
    val monthStart: Long,
    val workouts: Int,
    val trainingDays: Int,
    val totalMinutes: Long,
    val sets: Int,
    val volumeKg: Double,
    val prs: List<RecapPr>,
    val topMuscle: Pair<Muscle, Int>?,
    val topExercise: Pair<String, Int>?,
    val favoriteWeekday: String?,
    /** Longest run of consecutive calendar weeks (Mon–Sun) with at least one workout, ending in this month. */
    val longestWeekStreak: Int,
    val previousMonthWorkouts: Int,
)

object Recaps {
    fun monthStart(year: Int, month0: Int): Long = Calendar.getInstance().apply {
        clear(); set(year, month0, 1, 0, 0, 0)
    }.timeInMillis

    fun nextMonth(start: Long): Long = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.MONTH, 1) }.timeInMillis
    fun previousMonth(start: Long): Long = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.MONTH, -1) }.timeInMillis

    fun monthName(start: Long): String =
        java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(java.util.Date(start))

    /** Builds the recap for [monthStart, next month) from all working sets before the end of that month. */
    fun build(rows: List<RecapSetRow>, monthStart: Long): MonthRecap {
        val end = nextMonth(monthStart)
        val prevStart = previousMonth(monthStart)
        val inMonth = rows.filter { it.startedAt in monthStart until end }
        val before = rows.filter { it.startedAt < monthStart }
        val sessions = inMonth.groupBy { it.sessionId }

        fun e1(r: RecapSetRow) = if (r.reps in 1..12) Stats.e1rm(r.weightKg, r.reps) else 0.0
        val bestBefore = before.groupBy { it.exerciseId }.mapValues { (_, l) -> l.maxOf(::e1) }
        val prs = inMonth.groupBy { it.exerciseId }.mapNotNull { (id, l) ->
            val best = l.maxOf(::e1)
            val prev = bestBefore[id] ?: return@mapNotNull null // first time isn't a "record"
            if (prev > 0 && best > prev + 1e-6) RecapPr(l.first().exerciseName, best, prev) else null
        }.sortedByDescending { (it.e1rmKg - it.previousKg) / it.previousKg }

        val cal = Calendar.getInstance()
        fun dayKey(t: Long): Int { cal.timeInMillis = t; return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR) }
        val weekday = sessions.values.map { it.first().startedAt }.groupingBy {
            cal.timeInMillis = it
            cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())
        }.eachCount().maxByOrNull { it.value }?.key

        return MonthRecap(
            monthStart = monthStart,
            workouts = sessions.size,
            trainingDays = sessions.values.map { dayKey(it.first().startedAt) }.distinct().size,
            totalMinutes = sessions.values.sumOf { (it.first().endedAt - it.first().startedAt).coerceAtLeast(0) } / 60_000,
            sets = inMonth.size,
            volumeKg = inMonth.sumOf { it.weightKg * it.reps },
            prs = prs,
            topMuscle = inMonth.filter { it.muscle != Muscle.OTHER }.groupingBy { it.muscle }.eachCount()
                .maxByOrNull { it.value }?.toPair(),
            topExercise = inMonth.groupingBy { it.exerciseName }.eachCount().maxByOrNull { it.value }?.toPair(),
            favoriteWeekday = weekday,
            longestWeekStreak = longestWeekStreak(rows.filter { it.startedAt < end }.map { it.startedAt }, monthStart, end),
            previousMonthWorkouts = rows.filter { it.startedAt in prevStart until monthStart }.map { it.sessionId }.distinct().size,
        )
    }

    /** Longest streak of consecutive ISO weeks with a workout, counting only streaks that touch this month. */
    private fun longestWeekStreak(starts: List<Long>, monthStart: Long, end: Long): Int {
        val cal = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY; minimalDaysInFirstWeek = 4 }
        fun weekIndex(t: Long): Long {
            cal.timeInMillis = t
            cal.set(Calendar.HOUR_OF_DAY, 12)
            val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
            cal.add(Calendar.DAY_OF_YEAR, -dow)
            return cal.timeInMillis / (7L * 86_400_000L)
        }
        val weeks = starts.map(::weekIndex).toSortedSet().toList()
        val monthWeeks = starts.filter { it in monthStart until end }.map(::weekIndex).toSet()
        var best = 0; var run = 0; var prev: Long? = null; var touches = false
        for (w in weeks) {
            if (prev != null && w == prev + 1) run++ else { run = 1; touches = false }
            if (w in monthWeeks) touches = true
            if (touches) best = maxOf(best, run)
            prev = w
        }
        return best
    }
}
