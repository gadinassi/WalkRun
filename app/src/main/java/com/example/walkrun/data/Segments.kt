package com.example.walkrun.data

import android.location.Location
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A segment: a fixed part of a route chosen by the user ("the hill in the park").
 * Every workout that passes its start and later its end gets a time on it.
 */
@Entity(tableName = "route_segments")
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    /** Length along the route of the workout it was made from. */
    val lengthM: Double,
    val createdAt: Long
)

/** One pass of a workout through a segment. */
data class SegmentEffort(val workout: WorkoutEntity, val timeMs: Long)

object SegmentMatcher {
    /** A point within this distance of the start or end counts as passing it. */
    private const val RADIUS_M = 30f
    /** The distance covered between start and end must be close to the segment length. */
    private const val MIN_RATIO = 0.75
    private const val MAX_RATIO = 1.3

    private fun dist(p: TrackPointEntity, lat: Double, lon: Double): Float {
        val r = FloatArray(1)
        Location.distanceBetween(p.lat, p.lon, lat, lon, r)
        return r[0]
    }

    /**
     * Fastest pass through [s] in the points of one workout, in active ms. Null = no pass.
     * Start: the point closest to the segment start, inside each approach to it.
     * End: the first approach to the segment end after at least 75% of the length.
     */
    fun bestTime(s: SegmentEntity, points: List<TrackPointEntity>): Long? {
        var best: Long? = null
        var i = 0
        val n = points.size
        while (i < n) {
            if (dist(points[i], s.startLat, s.startLon) > RADIUS_M) { i++; continue }
            var k = i
            var bi = i
            var bd = dist(points[i], s.startLat, s.startLon)
            while (k + 1 < n && dist(points[k + 1], s.startLat, s.startLon) <= RADIUS_M) {
                k++
                val d = dist(points[k], s.startLat, s.startLon)
                if (d < bd) { bd = d; bi = k }
            }
            var j = bi + 1
            var endJ = -1
            var endD = Float.MAX_VALUE
            while (j < n) {
                val covered = points[j].distanceM - points[bi].distanceM
                if (covered > s.lengthM * MAX_RATIO) break
                if (covered >= s.lengthM * MIN_RATIO) {
                    val de = dist(points[j], s.endLat, s.endLon)
                    if (de <= RADIUS_M) {
                        if (de < endD) { endD = de; endJ = j }
                    } else if (endJ >= 0) break // Left the end area: take the closest point.
                }
                j++
            }
            if (endJ >= 0) {
                val t = points[endJ].elapsedMs - points[bi].elapsedMs
                if (t > 0 && (best == null || t < best)) best = t
            }
            i = k + 1
        }
        return best
    }

    /** A new segment from part of a workout, between two distances from its start. */
    fun create(name: String, points: List<TrackPointEntity>, fromM: Double, toM: Double): SegmentEntity? {
        if (points.size < 2 || toM - fromM < 50) return null
        val a = points.minByOrNull { kotlin.math.abs(it.distanceM - fromM) } ?: return null
        val b = points.minByOrNull { kotlin.math.abs(it.distanceM - toM) } ?: return null
        if (b.distanceM - a.distanceM < 50) return null
        return SegmentEntity(
            name = name, startLat = a.lat, startLon = a.lon, endLat = b.lat, endLon = b.lon,
            lengthM = b.distanceM - a.distanceM, createdAt = System.currentTimeMillis()
        )
    }
}
