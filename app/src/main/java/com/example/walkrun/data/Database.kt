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

    @Query("SELECT * FROM workouts ORDER BY startTime")
    suspend fun allOnce(): List<WorkoutEntity>

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

    @Insert
    suspend fun insertSegment(s: SegmentEntity): Long

    @Query("SELECT * FROM route_segments ORDER BY createdAt")
    fun observeSegments(): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM route_segments ORDER BY createdAt")
    suspend fun segmentsOnce(): List<SegmentEntity>

    @Query("DELETE FROM route_segments WHERE id = :id")
    suspend fun deleteSegment(id: Long)

    @Insert
    suspend fun insertShoe(s: ShoeEntity): Long

    @Update
    suspend fun updateShoe(s: ShoeEntity)

    @Query("SELECT * FROM shoes ORDER BY retired, addedAt DESC")
    fun observeShoes(): Flow<List<ShoeEntity>>

    @Query("SELECT * FROM shoes WHERE id = :id")
    suspend fun getShoe(id: Long): ShoeEntity?

    @Query("SELECT shoeId, SUM(distanceM) AS meters FROM workouts WHERE shoeId IS NOT NULL GROUP BY shoeId")
    fun observeShoeTotals(): Flow<List<ShoeTotal>>

    @Query("SELECT COALESCE(SUM(distanceM), 0) FROM workouts WHERE shoeId = :shoeId")
    suspend fun shoeMeters(shoeId: Long): Double

    @Query("UPDATE workouts SET shoeId = :shoeId WHERE id = :workoutId")
    suspend fun setWorkoutShoe(workoutId: Long, shoeId: Long?)

    @Query("UPDATE workouts SET shoeId = NULL WHERE shoeId = :shoeId")
    suspend fun clearShoe(shoeId: Long)

    @Query("DELETE FROM shoes WHERE id = :id")
    suspend fun deleteShoe(id: Long)
}

@Database(
    entities = [WorkoutEntity::class, TrackPointEntity::class, SegmentEntity::class, ShoeEntity::class],
    version = 5,
    exportSchema = false
)
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

        /** Version 4: route segments (the times on them are calculated from the points, not stored). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS route_segments (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                        "startLat REAL NOT NULL, startLon REAL NOT NULL, endLat REAL NOT NULL, endLon REAL NOT NULL, " +
                        "lengthM REAL NOT NULL, createdAt INTEGER NOT NULL)"
                )
            }
        }

        /** Version 5: shoes, and the shoe of each workout. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS shoes (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, limitKm REAL NOT NULL, " +
                        "retired INTEGER NOT NULL, addedAt INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE workouts ADD COLUMN shoeId INTEGER")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "walkrun.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
