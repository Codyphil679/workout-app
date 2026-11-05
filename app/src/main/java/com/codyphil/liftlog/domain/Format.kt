package com.codyphil.liftlog.domain

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    fun duration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** "1h 05m" / "42m" for workout lengths. */
    fun durationShort(millis: Long): String {
        val totalMin = (millis / 60_000).coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h %02dm".format(m) else "${m}m"
    }

    fun date(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

    fun dateShort(millis: Long): String = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))

    fun weekdayDate(millis: Long): String =
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(millis))

    fun time(millis: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

    fun isoDateTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
}
