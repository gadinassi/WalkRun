package com.example.walkrun.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.walkrun.tracking.LatLon
import com.example.walkrun.util.Geo
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** A route drawn by the user before the workout. [pointsText]: "lat,lon;lat,lon;...". */
@Entity(tableName = "planned_routes")
data class PlannedRouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pointsText: String,
    val lengthM: Double,
    val createdAt: Long = System.currentTimeMillis()
) {
    val points: List<LatLon> get() = RouteGeometry.decode(pointsText)
}

/** A turn on a planned route: where it is (meters from the start) and which way. */
data class Turn(val atM: Double, val angle: Double) {
    /** Positive angle = right (clockwise). */
    val right: Boolean get() = angle > 0

    val speech: String
        get() = when {
            abs(angle) >= 120 -> if (right) "פנה חדות ימינה" else "פנה חדות שמאלה"
            abs(angle) >= 60 -> if (right) "פנה ימינה" else "פנה שמאלה"
            else -> if (right) "פנה קלות ימינה" else "פנה קלות שמאלה"
        }
}

/** Geometry of planned routes. Pure Kotlin: covered by unit tests. */
object RouteGeometry {
    /** A heading change smaller than this is not a turn. */
    private const val MIN_TURN_DEG = 35.0
    /** Points closer than this are merged, before looking for turns (GPS-like noise in the path). */
    private const val SIMPLIFY_M = 12.0

    /** "lat,lon;lat,lon;..." with 6 decimals (about 10 cm). */
    fun encode(points: List<LatLon>): String =
        points.joinToString(";") { String.format(java.util.Locale.US, "%.6f,%.6f", it.lat, it.lon) }

    fun decode(text: String): List<LatLon> = text.split(';').mapNotNull { p ->
        val parts = p.split(',')
        val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
        val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
        LatLon(lat, lon)
    }

    fun dist(a: LatLon, b: LatLon) = Geo.distanceM(a.lat, a.lon, b.lat, b.lon)

    fun length(points: List<LatLon>): Double = points.zipWithNext().sumOf { (a, b) -> dist(a, b) }

    /** Compass bearing from [a] to [b], 0..360 (0 = north, 90 = east). */
    fun bearing(a: LatLon, b: LatLon): Double {
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val dl = Math.toRadians(b.lon - a.lon)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** Heading change from bearing [b1] to [b2], -180..180. Positive = right. */
    fun turnAngle(b1: Double, b2: Double): Double {
        var d = b2 - b1
        while (d > 180) d -= 360
        while (d < -180) d += 360
        return d
    }

    /** Turns along the route, with their distance from the start. */
    fun turns(points: List<LatLon>): List<Turn> {
        // Keep points at least SIMPLIFY_M apart, with their distance along the full route.
        val kept = mutableListOf<Pair<LatLon, Double>>()
        var along = 0.0
        points.forEachIndexed { i, p ->
            if (i > 0) along += dist(points[i - 1], p)
            if (kept.isEmpty() || dist(kept.last().first, p) >= SIMPLIFY_M || i == points.lastIndex) kept += p to along
        }
        val out = mutableListOf<Turn>()
        for (i in 1 until kept.size - 1) {
            val a = turnAngle(bearing(kept[i - 1].first, kept[i].first), bearing(kept[i].first, kept[i + 1].first))
            if (abs(a) >= MIN_TURN_DEG) out += Turn(kept[i].second, a)
        }
        return out
    }

    /**
     * Where a position is on the route: distance along it and distance off it.
     * Searches near [fromIndex] first (the route can cross itself), so progress does not jump back.
     */
    data class Projection(val alongM: Double, val offM: Double, val index: Int)

    fun project(points: List<LatLon>, p: LatLon, fromIndex: Int = 0, window: Int = 60): Projection? {
        if (points.size < 2) return null
        val cum = DoubleArray(points.size)
        for (i in 1 until points.size) cum[i] = cum[i - 1] + dist(points[i - 1], points[i])
        var best: Projection? = null
        val start = (fromIndex - 2).coerceAtLeast(0)
        val end = (fromIndex + window).coerceAtMost(points.size - 1)
        fun search(range: IntRange) {
            for (i in range) {
                val a = points[i]
                val b = points[i + 1]
                // Local flat projection (meters), good for short segments.
                val kx = 111_320.0 * cos(Math.toRadians(a.lat))
                val ky = 110_540.0
                val bx = (b.lon - a.lon) * kx
                val by = (b.lat - a.lat) * ky
                val px = (p.lon - a.lon) * kx
                val py = (p.lat - a.lat) * ky
                val len2 = bx * bx + by * by
                val t = if (len2 > 0) ((px * bx + py * by) / len2).coerceIn(0.0, 1.0) else 0.0
                val dx = px - t * bx
                val dy = py - t * by
                val off = kotlin.math.sqrt(dx * dx + dy * dy)
                if (best == null || off < best!!.offM) best = Projection(cum[i] + t * kotlin.math.sqrt(len2), off, i)
            }
        }
        search(start until end)
        // Far from the expected part: search the whole route.
        if (best == null || best!!.offM > 50) search(0 until points.size - 1)
        return best
    }
}
