package com.example.walkrun.tracking

import com.example.walkrun.data.Sex
import com.example.walkrun.data.UserProfile
import com.example.walkrun.data.WorkoutType

/**
 * Calories = active part (ACSM metabolic equations, net VO2) + resting part (BMR, Mifflin-St Jeor).
 * These values are estimates, not measurements.
 */
object Metrics {

    fun bmrKcalPerDay(p: UserProfile): Double =
        10.0 * p.weightKg + 6.25 * p.heightCm - 5.0 * p.age + (if (p.sex == Sex.MALE) 5.0 else -161.0)

    fun restingKcal(p: UserProfile, elapsedMs: Long): Double =
        bmrKcalPerDay(p) / 1440.0 * (elapsedMs / 60000.0)

    /** [grade] is rise/run (0.05 = 5%). Only uphill grade is used. */
    fun activeKcal(type: WorkoutType, speedMps: Double, grade: Double, dtSec: Double, weightKg: Double): Double {
        val vm = speedMps * 60.0 // m/min
        val running = vm > 134.0 || (type == WorkoutType.RUN && vm > 80.0)
        val vo2 = if (running) 0.2 * vm + 0.9 * vm * grade + 3.5 else 0.1 * vm + 1.8 * vm * grade + 3.5
        val netVo2 = (vo2 - 3.5).coerceAtLeast(0.0)
        return netVo2 * weightKg / 1000.0 * 5.0 * (dtSec / 60.0)
    }

    /** Estimated stride length. Used only if no step sensor data is available. */
    fun strideM(p: UserProfile, type: WorkoutType): Double =
        p.heightCm / 100.0 * (if (type == WorkoutType.RUN) 0.65 else 0.415)
}

/** Counts climb only when the altitude change is more than [threshold] meters. This removes noise. */
class ElevationGainTracker(private val threshold: Double) {
    private var reference: Double? = null
    var gain = 0.0
        private set

    fun add(altitude: Double) {
        val ref = reference
        if (ref == null) {
            reference = altitude
            return
        }
        val d = altitude - ref
        if (d >= threshold) {
            gain += d
            reference = altitude
        } else if (d <= -threshold) {
            reference = altitude
        }
    }

    /** Call after a pause, so altitude change during the pause is not counted. */
    fun reset() {
        reference = null
    }
}
