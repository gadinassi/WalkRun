package com.example.walkrun.data

import com.example.walkrun.util.Format

/**
 * Personal records. Time records: the fastest time for a distance anywhere inside a workout
 * (not only from the start). Records are kept per workout type.
 */
enum class RecordKind(val label: String, val speech: String, val distanceM: Double?) {
    KM1("1 ק\"מ", "קילומטר אחד", 1000.0),
    KM5("5 ק\"מ", "5 קילומטר", 5000.0),
    KM10("10 ק\"מ", "10 קילומטר", 10000.0),
    HALF("חצי מרתון", "חצי מרתון", 21097.5),
    LONGEST_DISTANCE("המרחק הארוך", "המרחק הארוך ביותר", null),
    LONGEST_TIME("האימון הארוך", "האימון הארוך ביותר", null);

    val isTime: Boolean get() = distanceM != null

    /** The value of this record in [w], for comparison. Smaller is better for time records. */
    fun value(w: WorkoutEntity): Double? = when (this) {
        LONGEST_DISTANCE -> w.distanceM
        LONGEST_TIME -> w.durationMs.toDouble()
        else -> w.bestMs(this)?.toDouble()
    }

    fun better(a: Double, b: Double): Boolean = if (isTime) a < b else a > b

    /** Text for the screen and the email. */
    fun display(w: WorkoutEntity): String = when (this) {
        LONGEST_DISTANCE -> "${Format.km(w.distanceM)} ק\"מ"
        LONGEST_TIME -> Format.duration(w.durationMs)
        else -> w.bestMs(this)?.let { ms ->
            "${Format.duration(ms)} (קצב ${Format.pace(ms / 1000.0 / (distanceM!! / 1000.0))})"
        } ?: "--"
    }

    /** Text for speech. */
    fun spoken(w: WorkoutEntity): String = when (this) {
        LONGEST_DISTANCE -> "${Format.kmSpeech(w.distanceM)} קילומטר"
        LONGEST_TIME -> Format.durationSpeech(w.durationMs)
        else -> Format.durationSpeech(w.bestMs(this) ?: 0)
    }
}

object BestEfforts {
    /**
     * Fastest active time (ms) to cover [targetM] anywhere in the workout.
     * Uses cumulative distance and active time of each point; pauses are not included in either.
     * The start point is interpolated between two GPS points.
     */
    fun best(points: List<TrackPointEntity>, targetM: Double): Long? {
        if (points.size < 2) return null
        val d = DoubleArray(points.size) { points[it].distanceM }
        val t = DoubleArray(points.size) { points[it].elapsedMs.toDouble() }
        if (d.last() - d.first() < targetM) return null
        var best = Double.MAX_VALUE
        var i = 0
        for (j in 1 until d.size) {
            val s = d[j] - targetM
            if (s < d[0]) continue
            while (i + 1 < j && d[i + 1] <= s) i++
            val span = d[i + 1] - d[i]
            val ts = if (span > 0) t[i] + (s - d[i]) / span * (t[i + 1] - t[i]) else t[i]
            val time = t[j] - ts
            if (time > 0 && time < best) best = time
        }
        return if (best == Double.MAX_VALUE) null else best.toLong()
    }

    fun fill(w: WorkoutEntity, points: List<TrackPointEntity>): WorkoutEntity = w.copy(
        best1kMs = best(points, RecordKind.KM1.distanceM!!),
        best5kMs = best(points, RecordKind.KM5.distanceM!!),
        best10kMs = best(points, RecordKind.KM10.distanceM!!),
        bestHalfMs = best(points, RecordKind.HALF.distanceM!!),
        bestsDone = true
    )
}

/** One record: the best value of a kind, and the workout that holds it. */
data class PersonalRecord(val kind: RecordKind, val workout: WorkoutEntity)

object RecordBook {
    /** Records of one type from [workouts] (all of the same type). */
    fun of(workouts: List<WorkoutEntity>): List<PersonalRecord> = RecordKind.entries.mapNotNull { k ->
        workouts.filter { k.value(it) != null && it.distanceM > 0 }
            .reduceOrNull { a, b -> if (k.better(k.value(b)!!, k.value(a)!!)) b else a }
            ?.let { PersonalRecord(k, it) }
    }

    /**
     * Records that [w] beats, compared with [others] of the same type.
     * A kind with no earlier value is not a record (the first workout would set all of them).
     */
    fun newIn(w: WorkoutEntity, others: List<WorkoutEntity>): List<RecordKind> = RecordKind.entries.filter { k ->
        val mine = k.value(w) ?: return@filter false
        val prev = others.mapNotNull { k.value(it) }
        prev.isNotEmpty() && prev.all { k.better(mine, it) }
    }
}
