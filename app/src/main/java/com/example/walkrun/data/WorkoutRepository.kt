package com.example.walkrun.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        clearEffortCache()
    }

    /** Empty or blank text removes the title. */
    suspend fun setRouteTitle(workout: WorkoutEntity, title: String) {
        dao.setRouteTitle(workout.routeRootId, title.trim().ifEmpty { null })
    }

    suspend fun history(type: String, from: Long, before: Long): List<WorkoutEntity> =
        dao.history(type, from, before)

    suspend fun between(from: Long, before: Long): List<WorkoutEntity> = dao.between(from, before)

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

    /** Calculates the best times of workouts saved before version 3. Runs one time per workout. */
    suspend fun backfillBests() {
        for (w in dao.withoutBests()) {
            val points = dao.getPoints(w.id)
            dao.updateWorkout(withContext(Dispatchers.Default) { BestEfforts.fill(w, points) })
        }
    }

    suspend fun ofType(type: String): List<WorkoutEntity> = dao.ofType(type)

    suspend fun allOnce(): List<WorkoutEntity> = dao.allOnce()

    // ---------------- Shoes ----------------

    val shoes: Flow<List<ShoeEntity>> = dao.observeShoes()

    /** Meters per shoe id. */
    val shoeTotals: Flow<Map<Long, Double>> = dao.observeShoeTotals().map { l -> l.associate { it.shoeId to it.meters } }

    suspend fun addShoe(s: ShoeEntity): Long = dao.insertShoe(s)
    suspend fun updateShoe(s: ShoeEntity) = dao.updateShoe(s)
    suspend fun getShoe(id: Long): ShoeEntity? = dao.getShoe(id)
    suspend fun shoeMeters(id: Long): Double = dao.shoeMeters(id)
    suspend fun setWorkoutShoe(workoutId: Long, shoeId: Long?) = dao.setWorkoutShoe(workoutId, shoeId)

    /** The workouts stay; they lose the link to this shoe. */
    suspend fun deleteShoe(id: Long) = db.withTransaction {
        dao.clearShoe(id)
        dao.deleteShoe(id)
    }

    // ---------------- Planned routes ----------------

    val plannedRoutes: Flow<List<PlannedRouteEntity>> = dao.observePlannedRoutes()
    suspend fun addPlannedRoute(r: PlannedRouteEntity): Long = dao.insertPlannedRoute(r)
    suspend fun getPlannedRoute(id: Long): PlannedRouteEntity? = dao.getPlannedRoute(id)
    suspend fun deletePlannedRoute(id: Long) = dao.deletePlannedRoute(id)

    // ---------------- Segments ----------------

    val segments: Flow<List<SegmentEntity>> = dao.observeSegments()

    suspend fun addSegment(s: SegmentEntity): Long = dao.insertSegment(s).also { clearEffortCache() }

    suspend fun deleteSegment(id: Long) {
        dao.deleteSegment(id)
        clearEffortCache()
    }

    /** Best time of each workout on the segment, fastest first. Cached until a workout or segment changes. */
    private val effortCache = HashMap<Long, List<SegmentEffort>>()

    private fun clearEffortCache() = synchronized(effortCache) { effortCache.clear() }

    suspend fun efforts(s: SegmentEntity): List<SegmentEffort> {
        synchronized(effortCache) { effortCache[s.id] }?.let { return it }
        val result = withContext(Dispatchers.Default) {
            dao.allOnce().mapNotNull { w ->
                SegmentMatcher.bestTime(s, dao.getPoints(w.id))?.let { SegmentEffort(w, it) }
            }.sortedBy { it.timeMs }
        }
        synchronized(effortCache) { effortCache[s.id] = result }
        return result
    }

    /** Segments where [w] set the best time (and at least one earlier pass existed). */
    suspend fun segmentRecords(w: WorkoutEntity): List<Pair<SegmentEntity, Long>> =
        dao.segmentsOnce().mapNotNull { s ->
            val all = efforts(s)
            val mine = all.firstOrNull { it.workout.id == w.id } ?: return@mapNotNull null
            if (all.size > 1 && all.first().workout.id == w.id) s to mine.timeMs else null
        }

    /** Saves the workout and its points, then calculates the trend and the new personal records. */
    suspend fun save(workout: WorkoutEntity, points: List<TrackPointEntity>): Long {
        backfillBests() // The record check needs the best times of all earlier workouts.
        val withBests = withContext(Dispatchers.Default) { BestEfforts.fill(workout, points) }
        val id = db.withTransaction {
            val newId = dao.insertWorkout(withBests)
            if (points.isNotEmpty()) dao.insertPoints(points.map { it.copy(workoutId = newId) })
            newId
        }
        val others = dao.ofType(workout.type).filter { it.id != id }
        val records = RecordBook.newIn(withBests, others)
        val saved = withBests.copy(id = id, newRecords = records.joinToString(",") { it.name }.ifEmpty { null })
        val route = saved.routeId
        // Skip workouts too short to compare (for example, stopped right after start).
        val minM = TrendCalculator.MIN_DISTANCE_M
        val previous = if (route != null) {
            dao.previousOnRoute(route, id, saved.startTime, minM)
        } else {
            dao.previousOfType(saved.type, id, saved.startTime, minM)
        }
        clearEffortCache() // The new workout can have segment times.
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
