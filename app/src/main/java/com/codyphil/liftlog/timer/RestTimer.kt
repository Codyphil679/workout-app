package com.codyphil.liftlog.timer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RestState(
    /** Wall-clock end time (System.currentTimeMillis based). */
    val endAt: Long,
    val totalSeconds: Int,
    val label: String,
) {
    fun remainingMillis(now: Long = System.currentTimeMillis()): Long = (endAt - now).coerceAtLeast(0)
}

/**
 * Single source of truth for the rest countdown. The UI reads [state]; [RestTimerService]
 * keeps the countdown alive (and alerts) while the app is in the background or the screen is off.
 */
object RestTimer {
    private val _state = MutableStateFlow<RestState?>(null)
    val state: StateFlow<RestState?> = _state.asStateFlow()

    fun start(context: Context, seconds: Int, label: String) {
        if (seconds <= 0) return
        _state.value = RestState(System.currentTimeMillis() + seconds * 1000L, seconds, label)
        ContextCompat.startForegroundService(context, Intent(context, RestTimerService::class.java))
    }

    fun adjust(deltaSeconds: Int) {
        val s = _state.value ?: return
        val newEnd = s.endAt + deltaSeconds * 1000L
        val now = System.currentTimeMillis()
        _state.value = if (newEnd <= now) null
        else s.copy(endAt = newEnd, totalSeconds = (s.totalSeconds + deltaSeconds).coerceAtLeast(1))
    }

    fun skip() {
        _state.value = null
    }

    fun finished(expected: RestState) {
        _state.compareAndSet(expected, null)
    }
}
