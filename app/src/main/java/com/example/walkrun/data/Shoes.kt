package com.example.walkrun.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A pair of shoes. Its distance is the sum of the workouts done with it. */
@Entity(tableName = "shoes")
data class ShoeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Replace after this distance. 600 km is a common value for running shoes. */
    val limitKm: Double = 600.0,
    /** Retired shoes keep their history but are not offered for new workouts. */
    val retired: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

/** Result row of the distance-per-shoe query. */
data class ShoeTotal(val shoeId: Long, val meters: Double)

object ShoeWear {
    /** Share of the limit used, 0..1+ . */
    fun used(meters: Double, limitKm: Double): Double = if (limitKm <= 0) 0.0 else meters / 1000.0 / limitKm

    /** True when this workout took the shoe over its limit (it was below before the workout). */
    fun crossedLimit(totalAfterM: Double, workoutM: Double, limitKm: Double): Boolean =
        limitKm > 0 && totalAfterM >= limitKm * 1000 && totalAfterM - workoutM < limitKm * 1000
}
