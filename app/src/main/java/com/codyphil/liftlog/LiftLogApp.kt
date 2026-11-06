package com.codyphil.liftlog

import android.app.Application
import com.codyphil.liftlog.data.AppDatabase
import com.codyphil.liftlog.data.BackupManager
import com.codyphil.liftlog.data.SettingsRepository
import com.codyphil.liftlog.data.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(app: Application) {
    val database = AppDatabase.create(app)
    val repository = WorkoutRepository(database)
    val backup = BackupManager(database)
    val settings = SettingsRepository(app)
    val health = com.codyphil.liftlog.health.HealthSync(app, repository)
    /** For work that should outlive a screen (e.g. syncing after a workout ends). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fire-and-forget Health Connect sync of one workout, if the user turned sync on. */
    fun syncToHealth(sessionId: Long) {
        val s = settings.settings.value
        if (s.healthConnectSync) appScope.launch { health.syncSession(sessionId, s.unit) }
    }

    /** Starts a program day using the current unit and bar weight (for progression rounding and warm-ups). */
    suspend fun startFromDay(dayId: Long, intensity: Double = 1.0, readiness: Double? = null): Long =
        settings.settings.value.let { repository.startFromDay(dayId, it.unit, it.bar, intensity, readiness) }
}

class LiftLogApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { container.repository.seedIfEmpty() }
    }
}
