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

    /** Starts a program day using the current unit and bar weight (for progression rounding and warm-ups). */
    suspend fun startFromDay(dayId: Long): Long =
        settings.settings.value.let { repository.startFromDay(dayId, it.unit, it.bar) }
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
