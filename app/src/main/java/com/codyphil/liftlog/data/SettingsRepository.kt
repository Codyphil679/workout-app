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
)

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read() = AppSettings(
        unit = runCatching { WeightUnit.valueOf(prefs.getString("unit", "LB")!!) }.getOrDefault(WeightUnit.LB),
        defaultRestSeconds = prefs.getInt("defaultRest", 90),
        autoStartRest = prefs.getBoolean("autoStartRest", true),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit {
            putString("unit", next.unit.name)
            putInt("defaultRest", next.defaultRestSeconds)
            putBoolean("autoStartRest", next.autoStartRest)
        }
        _settings.value = next
    }
}
