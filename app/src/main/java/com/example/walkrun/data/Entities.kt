package com.example.walkrun.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class WorkoutType(val label: String) {
    WALK("הליכה"), RUN("ריצה");

    companion object {
        fun from(name: String?): WorkoutType = entries.firstOrNull { it.name == name } ?: WALK
    }
}

enum class Trend {
    FIRST, IMPROVED, SAME, REGRESSED, NOT_COMPARABLE;

    companion object {
        fun from(name: String?): Trend? = entries.firstOrNull { it.name == name }
    }
}

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val startTime: Long,
    val endTime: Long,
    /** Active time. Pauses are not included. */
    val durationMs: Long,
    val distanceM: Double,
    val steps: Int,
    val stepsEstimated: Boolean,
    val calories: Double,
    val elevationGainM: Double,
    val avgSpeedMps: Double,
    val maxSpeedMps: Double,
    /** ID of the first workout on this route. Null if this workout is not a repeated route. */
    val routeId: Long? = null,
    /** ID of the workout used for live comparison. */
    val ghostWorkoutId: Long? = null,
    /** ID of the workout used for the trend calculation. */
    val comparedToId: Long? = null,
    val trend: String? = null,
    val trendText: String? = null,
    /** Name of the route, set by the user. Shared by all workouts of the same route. */
    val title: String? = null,
    /** Fastest active time for the distance inside this workout. Null = the workout is shorter. */
    val best1kMs: Long? = null,
    val best5kMs: Long? = null,
    val best10kMs: Long? = null,
    val bestHalfMs: Long? = null,
    /** True after the best times were calculated from the points. */
    @ColumnInfo(defaultValue = "0") val bestsDone: Boolean = false,
    /** Personal records set by this workout: RecordKind names, comma-separated. */
    val newRecords: String? = null
) {
    fun bestMs(kind: RecordKind): Long? = when (kind) {
        RecordKind.KM1 -> best1kMs
        RecordKind.KM5 -> best5kMs
        RecordKind.KM10 -> best10kMs
        RecordKind.HALF -> bestHalfMs
        else -> null
    }

    val records: List<RecordKind>
        get() = newRecords?.split(',')?.mapNotNull { n -> RecordKind.entries.firstOrNull { it.name == n } } ?: emptyList()

    /** ID of the first workout of this route. Workouts of the same route share it. */
    val routeRootId: Long get() = routeId ?: id
}

@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workoutId")]
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val time: Long,
    /** Active elapsed time at this point. Pauses are not included. */
    val elapsedMs: Long,
    val lat: Double,
    val lon: Double,
    val altitude: Double?,
    val speedMps: Float,
    /** Cumulative distance at this point. */
    val distanceM: Double,
    val steps: Int,
    /** Increments after each pause. */
    val segment: Int
)
