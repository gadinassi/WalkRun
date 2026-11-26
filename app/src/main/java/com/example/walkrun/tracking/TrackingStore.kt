package com.example.walkrun.tracking

import com.example.walkrun.data.WorkoutType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TrackingStatus { IDLE, RECORDING, PAUSED, SAVING }

data class LatLon(val lat: Double, val lon: Double)

data class TrackingSnapshot(
    val status: TrackingStatus = TrackingStatus.IDLE,
    val type: WorkoutType = WorkoutType.WALK,
    val elapsedMs: Long = 0,
    val distanceM: Double = 0.0,
    val currentSpeedMps: Float = 0f,
    val steps: Int = 0,
    val stepsEstimated: Boolean = false,
    val calories: Double = 0.0,
    val elevationGainM: Double = 0.0,
    val segments: List<List<LatLon>> = emptyList(),
    val reference: List<LatLon> = emptyList(),
    /** Positive = slower than the reference workout. Null = no comparison. */
    val ghostDiffMs: Long? = null,
    /** Set after a workout is saved. The UI opens the summary and clears it. */
    val lastSavedWorkoutId: Long? = null,
    /** Set when a finished workout was too short to save. The UI tells the user and clears it. */
    val discardedMinKm: Double? = null,
    /** Paused because the user stopped moving. Resumes by itself. */
    val autoPaused: Boolean = false,
    /** Current interval phase, for example "ריצה · סבב 3/8 · 1:12". Null = no intervals. */
    val intervalLabel: String? = null
)

/** Shared state between TrackingService and the UI. */
object TrackingStore {
    private val _state = MutableStateFlow(TrackingSnapshot())
    val state: StateFlow<TrackingSnapshot> = _state.asStateFlow()

    fun update(snapshot: TrackingSnapshot) {
        _state.value = snapshot
    }

    fun consumeSaved() {
        _state.update { it.copy(lastSavedWorkoutId = null, discardedMinKm = null) }
    }
}
