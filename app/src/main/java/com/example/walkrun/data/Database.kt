package com.example.walkrun.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Insert
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Query("SELECT * FROM workouts ORDER BY startTime DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkout(id: Long): WorkoutEntity?

    // Order of recording. The GPS time (column "time") can go backwards on some devices.
    @Query("SELECT * FROM track_points WHERE workoutId = :id ORDER BY id")
    suspend fun getPoints(id: Long): List<TrackPointEntity>

    @Query(
        "SELECT * FROM workouts WHERE type = :type AND id != :excludeId AND startTime < :before " +
            "AND distanceM >= :minM ORDER BY startTime DESC LIMIT 1"
    )
    suspend fun previousOfType(type: String, excludeId: Long, before: Long, minM: Double): WorkoutEntity?

    @Query(
        "SELECT * FROM workouts WHERE (id = :routeId OR routeId = :routeId) AND id != :excludeId " +
            "AND startTime < :before AND distanceM >= :minM ORDER BY startTime DESC LIMIT 1"
    )
    suspend fun previousOnRoute(routeId: Long, excludeId: Long, before: Long, minM: Double): WorkoutEntity?

    /** Workouts of one type that started in [from, before). Newest first. */
    @Query(
        "SELECT * FROM workouts WHERE type = :type AND startTime >= :from AND startTime < :before " +
            "ORDER BY startTime DESC"
    )
    suspend fun history(type: String, from: Long, before: Long): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE type = :type")
    suspend fun ofType(type: String): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE bestsDone = 0")
    suspend fun withoutBests(): List<WorkoutEntity>

    /** All workouts that started in [from, before). Oldest first. */
    @Query("SELECT * FROM workouts WHERE startTime >= :from AND startTime < :before ORDER BY startTime")
    suspend fun between(from: Long, before: Long): List<WorkoutEntity>

    /** Sets the title of every workout on the route that starts with workout [rootId]. */
    @Query("UPDATE workouts SET title = :title WHERE id = :rootId OR routeId = :rootId")
    suspend fun setRouteTitle(rootId: Long, title: String?)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [WorkoutEntity::class, TrackPointEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        /** Version 2: route title. Existing workouts are kept. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN title TEXT")
            }
        }

        /** Version 3: best times and personal records. The values are filled in later from the points. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("best1kMs", "best5kMs", "best10kMs", "bestHalfMs").forEach {
                    db.execSQL("ALTER TABLE workouts ADD COLUMN $it INTEGER")
                }
                db.execSQL("ALTER TABLE workouts ADD COLUMN bestsDone INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workouts ADD COLUMN newRecords TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "walkrun.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
