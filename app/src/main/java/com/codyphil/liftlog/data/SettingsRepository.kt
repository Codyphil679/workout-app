package com.codyphil.liftlog.data

import android.content.Context
import androidx.core.content.edit
import com.codyphil.liftlog.domain.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val unit: WeightUnit = WeightUnit.LB,
    val defaultRestSeconds: Int = 90,
    val autoStartRest: Boolean = true,
    val trackRpe: Boolean = false,
    /** Empty-bar weight for each unit, used by the plate and warm-up calculators. */
    val barLb: Double = 45.0,
    val barKg: Double = 20.0,
    /** Ask how you feel before starting a program day, and offer a lighter day when it's low. */
    val readinessCheck: Boolean = true,
    val healthConnectSync: Boolean = false,
) {
    val bar: Double get() = if (unit == WeightUnit.LB) barLb else barKg
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read() = AppSettings(
        unit = runCatching { WeightUnit.valueOf(prefs.getString("unit", "LB")!!) }.getOrDefault(WeightUnit.LB),
        defaultRestSeconds = prefs.getInt("defaultRest", 90),
        autoStartRest = prefs.getBoolean("autoStartRest", true),
        trackRpe = prefs.getBoolean("trackRpe", false),
        barLb = prefs.getFloat("barLb", 45f).toDouble(),
        barKg = prefs.getFloat("barKg", 20f).toDouble(),
        readinessCheck = prefs.getBoolean("readinessCheck", true),
        healthConnectSync = prefs.getBoolean("healthConnectSync", false),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit {
            putString("unit", next.unit.name)
            putInt("defaultRest", next.defaultRestSeconds)
            putBoolean("autoStartRest", next.autoStartRest)
            putBoolean("trackRpe", next.trackRpe)
            putFloat("barLb", next.barLb.toFloat())
            putFloat("barKg", next.barKg.toFloat())
            putBoolean("readinessCheck", next.readinessCheck)
            putBoolean("healthConnectSync", next.healthConnectSync)
        }
        _settings.value = next
    }
}
