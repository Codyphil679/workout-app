package com.codyphil.liftlog.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Exercise::class, Program::class, ProgramDay::class, DayExercise::class,
        WorkoutSession::class, SessionExercise::class, SetLog::class, BodyEntry::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // v2: set types, RPE, supersets, progression, muscle groups, body metrics. Additive only.
        AutoMigration(from = 1, to = 2),
        // v3: training max + % schemes, AMRAP sets, readiness check-in, lighter days. Additive only.
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun programDao(): ProgramDao
    abstract fun sessionDao(): SessionDao
    abstract fun historyDao(): HistoryDao
    abstract fun backupDao(): BackupDao
    abstract fun bodyDao(): BodyDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "liftlog.db").build()
    }
}
