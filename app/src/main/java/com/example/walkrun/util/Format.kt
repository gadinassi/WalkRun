package com.example.walkrun.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object Format {

    fun duration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun km(meters: Double): String = String.format(Locale.US, "%.2f", meters / 1000.0)

    fun pace(secPerKm: Double?): String {
        if (secPerKm == null || !secPerKm.isFinite() || secPerKm <= 0 || secPerKm > 3600) return "--:--"
        val t = secPerKm.roundToInt()
        return String.format(Locale.US, "%d:%02d", t / 60, t % 60)
    }

    fun avgPace(durationMs: Long, distanceM: Double): Double? =
        if (distanceM < 50) null else durationMs / 1000.0 / (distanceM / 1000.0)

    fun paceFromSpeed(mps: Float): Double? = if (mps < 0.3f) null else 1000.0 / mps

    fun date(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))

    // ---- Text for speech ----

    fun kmSpeech(meters: Double): String = String.format(Locale.US, "%.1f", meters / 1000.0)

    fun durationSpeech(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val parts = mutableListOf<String>()
        if (h > 0) parts += "$h שעות"
        if (m > 0) parts += "$m דקות"
        if (s > 0 || parts.isEmpty()) parts += "$s שניות"
        return parts.joinToString(" ו-")
    }

    /** Under 1 km in meters (rounded to 10 m), else km with one decimal. */
    fun distanceSpeech(meters: Double): String =
        if (meters < 1000) "${((meters / 10).roundToInt() * 10).coerceAtLeast(10)} מטר"
        else "${kmSpeech(meters)} קילומטר"

    fun paceSpeech(secPerKm: Double): String {
        val t = secPerKm.roundToInt()
        return "${t / 60} דקות ו-${t % 60} שניות"
    }
}
