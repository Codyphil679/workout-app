package com.codyphil.liftlog

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.codyphil.liftlog.data.AppDatabase

fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/** Manually advanced clock for deterministic timestamps. */
class FakeClock(var now: Long = 1_700_000_000_000L) {
    fun advanceSeconds(s: Long) { now += s * 1000 }
    val fn: () -> Long = { now }
}
