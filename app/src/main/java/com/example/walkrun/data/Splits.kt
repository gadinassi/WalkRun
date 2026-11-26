package com.example.walkrun.data

enum class SplitUnit(val label: String) {
    KM("כל ק\"מ"),
    TEN_MIN("כל 10 דקות")
}

enum class HistoryPeriod(val label: String, val days: Int?) {
    WEEK("שבוע אחרון", 7),
    MONTH("חודש אחרון", 30),
    ALL("כל הזמנים", null)
}

/**
 * One part of a workout: one km, or one 10-minute block.
 * [pace] is seconds per km. [fraction] is less than 1 only for the last, partial part.
 */
data class Split(val index: Int, val pace: Double, val fraction: Double)

/** One row of the comparison: this workout against the average of the history period. */
data class SplitRow(
    val index: Int,
    val fraction: Double,
    val pace: Double,
    val baselinePace: Double?,
    val baselineCount: Int
) {
    /** Percent. Positive = faster than the history average. */
    val changePct: Double? get() = baselinePace?.let { (it - pace) / it * 100.0 }
}

object Splits {
    private const val KM = 1000.0
    private const val BLOCK_MS = 600_000.0
    /** A last partial km shorter than 200 m is not shown: its pace is too noisy. */
    private const val MIN_PARTIAL_KM = 0.2
    /** A last partial block shorter than 2 minutes is not shown. */
    private const val MIN_PARTIAL_BLOCK = 0.2
    /** A 10-minute block with less than 10 m is a stop, not a pace. */
    private const val MIN_BLOCK_KM = 0.01

    /**
     * Uses the cumulative distance and the active time stored in each point.
     * Both exclude pauses, so a pause does not change a split.
     */
    fun of(points: List<TrackPointEntity>, unit: SplitUnit): List<Split> {
        if (points.size < 2) return emptyList()
        val d = DoubleArray(points.size) { points[it].distanceM }
        val t = DoubleArray(points.size) { points[it].elapsedMs.toDouble() }
        return if (unit == SplitUnit.KM) byKm(d, t) else byTime(d, t)
    }

    private fun byKm(d: DoubleArray, t: DoubleArray): List<Split> {
        val out = mutableListOf<Split>()
        val d0 = d.first()
        var prevT = t.first()
        var k = 1
        while (true) {
            val endD = d0 + k * KM
            if (endD <= d.last()) {
                val endT = interpolate(d, t, endD) ?: break
                out += Split(k, (endT - prevT) / 1000.0, 1.0)
                prevT = endT
                k++
            } else {
                val rest = (d.last() - (d0 + (k - 1) * KM)) / KM
                if (rest >= MIN_PARTIAL_KM) out += Split(k, (t.last() - prevT) / 1000.0 / rest, rest)
                break
            }
        }
        return out
    }

    private fun byTime(d: DoubleArray, t: DoubleArray): List<Split> {
        val out = mutableListOf<Split>()
        val t0 = t.first()
        var prevD = d.first()
        var j = 1
        while (true) {
            val endT = t0 + j * BLOCK_MS
            if (endT <= t.last()) {
                val endD = interpolate(t, d, endT) ?: break
                val km = (endD - prevD) / KM
                if (km >= MIN_BLOCK_KM) out += Split(j, BLOCK_MS / 1000.0 / km, 1.0)
                prevD = endD
                j++
            } else {
                val fraction = (t.last() - (t0 + (j - 1) * BLOCK_MS)) / BLOCK_MS
                val km = (d.last() - prevD) / KM
                if (fraction >= MIN_PARTIAL_BLOCK && km >= MIN_BLOCK_KM) {
                    out += Split(j, fraction * BLOCK_MS / 1000.0 / km, fraction)
                }
                break
            }
        }
        return out
    }

    /** Linear interpolation of y at x. [xs] must not decrease. Null if x is outside the range. */
    private fun interpolate(xs: DoubleArray, ys: DoubleArray, x: Double): Double? {
        if (x < xs.first() || x > xs.last()) return null
        var lo = 0
        var hi = xs.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (xs[mid] <= x) lo = mid else hi = mid
        }
        val x0 = xs[lo]
        val x1 = xs[hi]
        val f = if (x1 > x0) (x - x0) / (x1 - x0) else 0.0
        return ys[lo] + f * (ys[hi] - ys[lo])
    }

    /**
     * For each split of the current workout: the average pace of the same split (same km number,
     * or same 10-minute block) in the history workouts that reached it.
     */
    fun compare(current: List<Split>, history: List<List<Split>>): List<SplitRow> =
        current.map { s ->
            val same = history.mapNotNull { h -> h.firstOrNull { it.index == s.index } }
            SplitRow(
                index = s.index,
                fraction = s.fraction,
                pace = s.pace,
                baselinePace = if (same.isEmpty()) null else same.sumOf { it.pace } / same.size,
                baselineCount = same.size
            )
        }
}
