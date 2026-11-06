package com.codyphil.liftlog.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.codyphil.liftlog.data.WorkoutRepository
import com.codyphil.liftlog.domain.WeightUnit
import kotlinx.coroutines.flow.first

/**
 * Writes finished workouts to Health Connect, the on-device health data store on Android.
 * Nothing leaves the phone through LiftLog; Health Connect itself is local too.
 */
class HealthSync(private val context: Context, private val repo: WorkoutRepository) {
    val permissions = setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class))

    enum class Availability { AVAILABLE, NEEDS_INSTALL_OR_UPDATE, UNSUPPORTED }

    fun availability(): Availability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NEEDS_INSTALL_OR_UPDATE
        else -> Availability.UNSUPPORTED
    }

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    /** Play Store page for Health Connect (Android 13 and older, where it's a separate app). */
    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(
        "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"
    )).setPackage("com.android.vending")

    private fun client() = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermission(): Boolean = runCatching {
        availability() == Availability.AVAILABLE &&
            client().permissionController.getGrantedPermissions().containsAll(permissions)
    }.getOrDefault(false)

    /** Writes (or updates) one finished workout. Returns false when it couldn't be written. */
    suspend fun syncSession(sessionId: Long, unit: WeightUnit): Boolean = runCatching {
        if (!hasPermission()) return false
        val detail = repo.observeSessionDetail(sessionId).first() ?: return false
        val record = HealthRecords.build(detail, unit) ?: return false
        client().insertRecords(listOf(record))
        true
    }.getOrDefault(false)

    /** Writes every finished workout. Safe to repeat: records are matched by workout id. Returns how many were written. */
    suspend fun syncAll(unit: WeightUnit): Int = runCatching { syncAllUnsafe(unit) }.getOrDefault(0)

    /** Removes a workout from Health Connect (when it's deleted in LiftLog). */
    suspend fun deleteSession(sessionId: Long): Boolean = runCatching {
        if (!hasPermission()) return false
        client().deleteRecords(ExerciseSessionRecord::class, emptyList(), listOf(HealthRecords.clientId(sessionId)))
        true
    }.getOrDefault(false)

    private suspend fun syncAllUnsafe(unit: WeightUnit): Int {
        if (!hasPermission()) return 0
        val ids = repo.observeFinishedSessions().first().map { it.id }
        val records = ids.mapNotNull { id -> repo.observeSessionDetail(id).first()?.let { HealthRecords.build(it, unit) } }
        var written = 0
        records.chunked(50).forEach { batch ->
            runCatching { client().insertRecords(batch); written += batch.size }
        }
        return written
    }
}
