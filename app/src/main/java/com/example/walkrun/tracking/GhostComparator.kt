package com.example.walkrun.tracking

import android.location.Location
import com.example.walkrun.data.TrackPointEntity

/**
 * Compares the current workout with a reference workout by cumulative distance:
 * "At distance D, the reference workout had elapsed time T".
 * It does not do map matching. It assumes the same start point and the same route.
 */
class GhostComparator(points: List<TrackPointEntity>) {
    private val dist = DoubleArray(points.size) { points[it].distanceM }
    private val time = LongArray(points.size) { points[it].elapsedMs }
    private val lat = DoubleArray(points.size) { points[it].lat }
    private val lon = DoubleArray(points.size) { points[it].lon }

    val path: List<LatLon> = points.map { LatLon(it.lat, it.lon) }
    val totalDistance: Double = dist.lastOrNull() ?: 0.0

    /** Reference elapsed time at distance [d]. Null if [d] is outside the reference route. */
    fun timeAtDistance(d: Double): Long? {
        if (dist.size < 2 || d <= 0 || d > totalDistance) return null
        val idx = java.util.Arrays.binarySearch(dist, d)
        if (idx >= 0) return time[idx]
        val ins = -idx - 1
        if (ins == 0) return time[0]
        val d0 = dist[ins - 1]
        val d1 = dist[ins]
        val t0 = time[ins - 1]
        val t1 = time[ins]
        val f = if (d1 > d0) (d - d0) / (d1 - d0) else 0.0
        return (t0 + f * (t1 - t0)).toLong()
    }

    /** Distance in meters from the given position to the nearest reference point. */
    fun distanceFromRoute(latitude: Double, longitude: Double): Float {
        val r = FloatArray(1)
        var min = Float.MAX_VALUE
        for (i in lat.indices) {
            Location.distanceBetween(latitude, longitude, lat[i], lon[i], r)
            if (r[0] < min) min = r[0]
        }
        return min
    }
}
