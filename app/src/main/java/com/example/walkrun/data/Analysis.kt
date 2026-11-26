package com.example.walkrun.data

import com.example.walkrun.tracking.LatLon
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Calculations for the summary and statistics screens. */
object Analysis {

    // ---------------- Elevation ----------------

    /** (distance in m, altitude in m), smoothed. Empty when the workout has no altitude data. */
    fun elevationProfile(points: List<TrackPointEntity>): List<Pair<Double, Double>> {
        val raw = points.mapNotNull { p -> p.altitude?.let { p.distanceM to it } }
        if (raw.size < 3) return emptyList()
        // Moving average over 7 points: GPS altitude jumps a lot.
        val w = 3
        return raw.indices.map { i ->
            val from = (i - w).coerceAtLeast(0)
            val to = (i + w).coerceAtMost(raw.lastIndex)
            raw[i].first to (from..to).sumOf { raw[it].second } / (to - from + 1)
        }
    }

    // ---------------- Pace heat map ----------------

    /**
     * The route cut into pieces of about [pieceM] meters, each with its pace (s/km).
     * A pause (new segment) always starts a new piece.
     */
    fun pacePieces(points: List<TrackPointEntity>, pieceM: Double = 100.0): List<Pair<List<LatLon>, Double>> {
        val out = mutableListOf<Pair<List<LatLon>, Double>>()
        for (seg in points.groupBy { it.segment }.toSortedMap().values) {
            var start = 0
            for (i in 1 until seg.size) {
                val d = seg[i].distanceM - seg[start].distanceM
                if (d >= pieceM || i == seg.lastIndex) {
                    val t = (seg[i].elapsedMs - seg[start].elapsedMs) / 1000.0
                    if (d > 5 && t > 0) {
                        out += seg.subList(start, i + 1).map { LatLon(it.lat, it.lon) } to t / (d / 1000.0)
                    }
                    start = i
                }
            }
        }
        return out
    }

    /**
     * Color of a pace compared with the workout average:
     * green = 8% faster or more, yellow = about the average, red = 8% slower or more.
     */
    fun heatColor(pace: Double, avg: Double): Int {
        val r = ((pace / avg) - 1.0).coerceIn(-0.08, 0.08) / 0.08 // -1 fast .. 1 slow
        val green = 0xFF2E7D32.toInt()
        val yellow = 0xFFF9A825.toInt()
        val red = 0xFFC62828.toInt()
        return if (r < 0) mix(yellow, green, -r) else mix(yellow, red, r)
    }

    private fun mix(a: Int, b: Int, f: Double): Int {
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        fun lerp(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * f).toInt()
        return (0xFF shl 24) or (lerp(16) shl 16) or (lerp(8) shl 8) or lerp(0)
    }

    // ---------------- Calendar and streaks ----------------

    fun day(ms: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** km per day. */
    fun kmByDay(workouts: List<WorkoutEntity>): Map<LocalDate, Double> =
        workouts.groupBy { day(it.startTime) }.mapValues { (_, l) -> l.sumOf { it.distanceM } / 1000.0 }

    data class Streaks(val currentDays: Int, val longestDays: Int, val goalWeeks: Int?)

    /**
     * Current streak: days in a row with a workout, ending today or yesterday (today can still come).
     * [goalWeeks]: full weeks in a row (Sunday to Saturday, before this week) that reached the goal.
     */
    fun streaks(workouts: List<WorkoutEntity>, goalKm: Double, today: LocalDate = LocalDate.now()): Streaks {
        val days = workouts.map { day(it.startTime) }.toSortedSet()
        var longest = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            longest = maxOf(longest, run)
            prev = d
        }
        var current = 0
        var d = if (today in days) today else today.minusDays(1)
        while (d in days) { current++; d = d.minusDays(1) }

        val goalWeeks = if (goalKm > 0) {
            val byDay = kmByDay(workouts)
            var n = 0
            var weekStart = Week.startOf(today).minusDays(7)
            while (true) {
                val km = (0..6).sumOf { byDay[weekStart.plusDays(it.toLong())] ?: 0.0 }
                if (km < goalKm) break
                n++
                weekStart = weekStart.minusDays(7)
            }
            // This week counts too when the goal is already reached.
            val thisWeek = (0..6).sumOf { byDay[Week.startOf(today).plusDays(it.toLong())] ?: 0.0 }
            if (thisWeek >= goalKm) n + 1 else n
        } else null
        return Streaks(current, longest, goalWeeks)
    }

    // ---------------- Monthly trend ----------------

    data class Month(val month: YearMonth, val km: Double, val count: Int, val avgPace: Double?)

    /** The last [months] months, oldest first, for one type (null = all types for km). */
    fun monthly(workouts: List<WorkoutEntity>, months: Int = 12, now: YearMonth = YearMonth.now()): List<Month> =
        (months - 1 downTo 0).map { back ->
            val m = now.minusMonths(back.toLong())
            val l = workouts.filter { YearMonth.from(day(it.startTime)) == m }
            val dist = l.sumOf { it.distanceM }
            Month(
                m, dist / 1000.0, l.size,
                if (dist >= 50) l.sumOf { it.durationMs } / 1000.0 / (dist / 1000.0) else null
            )
        }
}
