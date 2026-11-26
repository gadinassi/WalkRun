package com.example.walkrun.report

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import com.example.walkrun.WalkRunApp
import com.example.walkrun.data.WorkoutEntity
import com.example.walkrun.data.WorkoutType
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * Writes each saved workout to Health Connect: an exercise session, distance, steps and calories.
 * Each record has a fixed client id (from the workout id), so writing it again replaces it, not duplicates it.
 */
object HealthSync {
    private const val TAG = "HealthSync"

    val PERMISSIONS = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)
    )

    fun available(context: Context) =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermissions(context: Context): Boolean = available(context) &&
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

    /** Writes one workout. Returns false when Health Connect is not available or not allowed. */
    suspend fun write(context: Context, w: WorkoutEntity): Boolean {
        if (!hasPermissions(context)) return false
        val client = HealthConnectClient.getOrCreate(context)
        val start = Instant.ofEpochMilli(w.startTime)
        // The session covers the whole workout, pauses included (end = the time it was stopped).
        val end = Instant.ofEpochMilli(maxOf(w.endTime, w.startTime + 1000))
        val zone = ZoneId.systemDefault().rules.getOffset(start)
        val device = Device(type = Device.TYPE_PHONE)
        fun meta(kind: String) = Metadata.activelyRecorded(device = device, clientRecordId = "walkrun-${w.id}-$kind", clientRecordVersion = 1)
        val type = WorkoutType.from(w.type)
        val records = buildList {
            add(
                ExerciseSessionRecord(
                    startTime = start, startZoneOffset = zone, endTime = end, endZoneOffset = zone,
                    exerciseType = if (type == WorkoutType.RUN) ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
                    else ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
                    title = w.title ?: "WalkRun – ${type.label}",
                    metadata = meta("session")
                )
            )
            if (w.distanceM > 0) add(
                DistanceRecord(
                    startTime = start, startZoneOffset = zone, endTime = end, endZoneOffset = zone,
                    distance = Length.meters(w.distanceM), metadata = meta("distance")
                )
            )
            if (w.steps > 0) add(
                StepsRecord(
                    startTime = start, startZoneOffset = zone, endTime = end, endZoneOffset = zone,
                    count = w.steps.toLong(), metadata = meta("steps")
                )
            )
            if (w.calories > 0) add(
                TotalCaloriesBurnedRecord(
                    startTime = start, startZoneOffset = zone, endTime = end, endZoneOffset = zone,
                    energy = Energy.kilocalories(w.calories), metadata = meta("calories")
                )
            )
        }
        client.insertRecords(records)
        return true
    }

    /** After a new workout is saved. Errors are logged, never shown: the workout is saved anyway. */
    suspend fun afterSave(app: WalkRunApp, w: WorkoutEntity) {
        if (!app.settings.healthConnect.first()) return
        runCatching { write(app, w) }.onFailure { Log.w(TAG, "Write failed", it) }
    }

    /** All saved workouts, for example after Health Connect was turned on. Returns how many were written. */
    suspend fun writeAll(app: WalkRunApp): Int {
        var n = 0
        for (w in app.workouts.allOnce()) {
            if (runCatching { write(app, w) }.onFailure { Log.w(TAG, "Write failed", it) }.getOrDefault(false)) n++
        }
        return n
    }
}
