package com.example.walkrun

import com.example.walkrun.data.TrackPointEntity
import com.example.walkrun.data.WorkoutEntity

/** Synthetic tracks and workouts with known answers. */
object TestData {
    /** Meters per degree of latitude (same earth radius as Geo). */
    const val M_PER_DEG_LAT = 111_195.08

    /**
     * A straight track going north from (lat0, lon0), one point every [stepM] meters.
     * [paceSecPerKm]: the pace of each km (the last value repeats for the rest).
     */
    fun track(
        km: Double,
        paceSecPerKm: List<Double>,
        lat0: Double = 32.0,
        lon0: Double = 34.8,
        stepM: Double = 10.0,
        segment: Int = 0,
        startElapsedMs: Long = 0,
        startDistanceM: Double = 0.0
    ): List<TrackPointEntity> {
        val out = mutableListOf<TrackPointEntity>()
        var d = 0.0
        var t = 0.0
        val n = (km * 1000 / stepM).toInt()
        for (i in 0..n) {
            if (i > 0) {
                val pace = paceSecPerKm[minOf((d / 1000).toInt(), paceSecPerKm.lastIndex)]
                d += stepM
                t += pace * stepM / 1000.0 * 1000.0 // ms
            }
            out += point(lat0 + d / M_PER_DEG_LAT, lon0, startDistanceM + d, startElapsedMs + t.toLong(), segment)
        }
        return out
    }

    fun point(lat: Double, lon: Double, distanceM: Double, elapsedMs: Long, segment: Int = 0, alt: Double? = null) =
        TrackPointEntity(
            workoutId = 1, time = 1_700_000_000_000 + elapsedMs, elapsedMs = elapsedMs,
            lat = lat, lon = lon, altitude = alt, speedMps = 0f, distanceM = distanceM, steps = 0, segment = segment
        )

    fun workout(
        id: Long = 1,
        type: String = "WALK",
        distanceM: Double = 5000.0,
        durationMs: Long = 1_800_000,
        startTime: Long = 1_700_000_000_000,
        routeId: Long? = null,
        calories: Double = 300.0
    ) = WorkoutEntity(
        id = id, type = type, startTime = startTime, endTime = startTime + durationMs, durationMs = durationMs,
        distanceM = distanceM, steps = 6000, stepsEstimated = false, calories = calories, elevationGainM = 10.0,
        avgSpeedMps = distanceM / (durationMs / 1000.0), maxSpeedMps = 3.0, routeId = routeId
    )
}
