package com.example.walkrun.tracking

/**
 * Finds each full km during the workout and its time.
 * The moment a km is reached is interpolated between the two GPS points around it.
 * Distance and time are active values (pauses are not included).
 */
class KmSplitter {
    data class Km(val number: Int, val timeMs: Long, val previousMs: Long?)

    private var nextKm = 1
    private var lastCrossMs = 0.0
    private var lastSplitMs: Long? = null
    private var prevD = 0.0
    private var prevT = 0.0

    /** Returns the km reached by this point (usually none, sometimes one). */
    fun add(distanceM: Double, elapsedMs: Long): List<Km> {
        val out = mutableListOf<Km>()
        while (distanceM >= nextKm * 1000.0) {
            val target = nextKm * 1000.0
            val f = if (distanceM > prevD) (target - prevD) / (distanceM - prevD) else 1.0
            val cross = prevT + f.coerceIn(0.0, 1.0) * (elapsedMs - prevT)
            val split = (cross - lastCrossMs).toLong()
            out += Km(nextKm, split, lastSplitMs)
            lastSplitMs = split
            lastCrossMs = cross
            nextKm++
        }
        prevD = distanceM
        prevT = elapsedMs.toDouble()
        return out
    }

    companion object {
        /** "קילומטר 3 ב-8 דקות ו-20 שניות. מהיר ב-10 שניות מהקילומטר הקודם." */
        fun speech(km: Km, duration: (Long) -> String): String {
            val sb = StringBuilder("קילומטר ${km.number} ב-${duration(km.timeMs)}.")
            km.previousMs?.let { prev ->
                val diffSec = ((km.timeMs - prev) / 1000.0).let { kotlin.math.round(it).toLong() }
                sb.append(
                    when {
                        diffSec <= -3 -> " מהיר ב-${-diffSec} שניות מהקילומטר הקודם."
                        diffSec >= 3 -> " איטי ב-$diffSec שניות מהקילומטר הקודם."
                        else -> " כמו הקילומטר הקודם."
                    }
                )
            }
            return sb.toString()
        }
    }
}
