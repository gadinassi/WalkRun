package com.example.walkrun.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class WorkoutRepository(private val db: AppDatabase) {
    private val dao = db.workoutDao()

    val all: Flow<List<WorkoutEntity>> = dao.observeAll()

    suspend fun getWorkout(id: Long): WorkoutEntity? = dao.getWorkout(id)
    suspend fun getPoints(id: Long): List<TrackPointEntity> = dao.getPoints(id)

    suspend fun delete(id: Long) {
        dao.delete(id)
        synchronized(splitCache) { splitCache.keys.removeAll { it.first == id } }
    }

    /** Empty or blank text removes the title. */
    suspend fun setRouteTitle(workout: WorkoutEntity, title: String) {
        dao.setRouteTitle(workout.routeRootId, title.trim().ifEmpty { null })
    }

    suspend fun history(type: String, from: Long, before: Long): List<WorkoutEntity> =
        dao.history(type, from, before)

    /** Points of a saved workout never change, so the splits are calculated one time and kept. */
    private val splitCache = HashMap<Pair<Long, SplitUnit>, List<Split>>()

    suspend fun splits(id: Long, unit: SplitUnit): List<Split> {
        val key = id to unit
        synchronized(splitCache) { splitCache[key] }?.let { return it }
        val points = dao.getPoints(id)
        val result = withContext(Dispatchers.Default) { Splits.of(points, unit) }
        synchronized(splitCache) { splitCache[key] = result }
        return result
    }

    /** Saves the workout and its points, then calculates and stores the trend. */
    suspend fun save(workout: WorkoutEntity, points: List<TrackPointEntity>): Long {
        val id = db.withTransaction {
            val newId = dao.insertWorkout(workout)
            if (points.isNotEmpty()) dao.insertPoints(points.map { it.copy(workoutId = newId) })
            newId
        }
        val saved = workout.copy(id = id)
        val route = saved.routeId
        // Skip workouts too short to compare (for example, stopped right after start).
        val minM = TrendCalculator.MIN_DISTANCE_M
        val previous = if (route != null) {
            dao.previousOnRoute(route, id, saved.startTime, minM)
        } else {
            dao.previousOfType(saved.type, id, saved.startTime, minM)
        }
        val (trend, text) = TrendCalculator.compare(saved, previous)
        dao.updateWorkout(saved.copy(comparedToId = previous?.id, trend = trend.name, trendText = text))
        return id
    }
}

/**
 * Main metric: average pace (seconds per km).
 * A change smaller than THRESHOLD is reported as "no significant change".
 */
object TrendCalculator {
    const val MIN_DISTANCE_M = 200.0
    private const val THRESHOLD = 0.02

    fun compare(current: WorkoutEntity, previous: WorkoutEntity?): Pair<Trend, String> {
        if (previous == null) return Trend.FIRST to "אין פעילות קודמת להשוואה."
        if (current.distanceM < MIN_DISTANCE_M || previous.distanceM < MIN_DISTANCE_M) {
            return Trend.NOT_COMPARABLE to "המרחק קצר מדי להשוואה."
        }
        val curPace = current.durationMs / 1000.0 / (current.distanceM / 1000.0)
        val prevPace = previous.durationMs / 1000.0 / (previous.distanceM / 1000.0)
        val change = (prevPace - curPace) / prevPace
        val trend = when {
            change > THRESHOLD -> Trend.IMPROVED
            change < -THRESHOLD -> Trend.REGRESSED
            else -> Trend.SAME
        }
        val paceDiff = abs(curPace - prevPace).roundToInt()
        val sb = StringBuilder()
        sb.append(
            when (trend) {
                Trend.IMPROVED -> "שיפור. הקצב הממוצע מהיר ב-$paceDiff שניות לקילומטר."
                Trend.REGRESSED -> "נסיגה. הקצב הממוצע איטי ב-$paceDiff שניות לקילומטר."
                else -> "אין שינוי משמעותי בקצב הממוצע."
            }
        )
        if (current.routeId != null) {
            val timeDiffSec = (current.durationMs - previous.durationMs) / 1000.0
            sb.append(" זמן המסלול: ${signed(timeDiffSec, 0)} שניות.")
        } else {
            sb.append(" מרחק: ${signed((current.distanceM - previous.distanceM) / 1000.0, 2)} קילומטר.")
        }
        sb.append(" קלוריות: ${signed(current.calories - previous.calories, 0)}.")
        return trend to sb.toString()
    }

    private fun signed(value: Double, decimals: Int): String =
        String.format(Locale.US, "%+.${decimals}f", value)
}
