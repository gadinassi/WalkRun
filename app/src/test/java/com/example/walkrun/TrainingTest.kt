package com.example.walkrun

import com.example.walkrun.data.ShoeWear
import com.example.walkrun.data.TrainingPlan
import com.example.walkrun.tracking.Intervals
import com.example.walkrun.tracking.KmSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KmSplitterTest {
    @Test
    fun eachKmWithItsTimeAndThePreviousOne() {
        val k = KmSplitter()
        val found = mutableListOf<KmSplitter.Km>()
        // Points every 10 m: km 1 at 6:00, km 2 at 5:30.
        var t = 0L
        for (i in 0..200) {
            val d = i * 10.0
            if (i > 0) t += if (d <= 1000) 3_600 else 3_300
            found += k.add(d, t)
        }
        assertEquals(2, found.size)
        assertEquals(1, found[0].number)
        assertEquals(360_000L, found[0].timeMs)
        assertNull(found[0].previousMs)
        assertEquals(330_000L, found[1].timeMs)
        assertEquals(360_000L, found[1].previousMs)
    }

    @Test
    fun crossingIsInterpolatedBetweenTwoPoints() {
        val k = KmSplitter()
        k.add(0.0, 0)
        k.add(900.0, 300_000)
        // 1000 m is at 1/2 of the way from 900 to 1100: 300,000 + 50,000.
        val km = k.add(1100.0, 400_000).single()
        assertEquals(350_000L, km.timeMs)
    }

    @Test
    fun aGpsJumpOverTwoKmGivesBoth() {
        val k = KmSplitter()
        k.add(0.0, 0)
        assertEquals(listOf(1, 2), k.add(2000.0, 600_000).map { it.number })
    }

    @Test
    fun speech() {
        val dur = { ms: Long -> "${ms / 1000} שניות" }
        assertEquals("קילומטר 1 ב-360 שניות.", KmSplitter.speech(KmSplitter.Km(1, 360_000, null), dur))
        assertTrue(KmSplitter.speech(KmSplitter.Km(2, 350_000, 360_000), dur).endsWith("מהיר ב-10 שניות מהקילומטר הקודם."))
        assertTrue(KmSplitter.speech(KmSplitter.Km(2, 372_000, 360_000), dur).endsWith("איטי ב-12 שניות מהקילומטר הקודם."))
        assertTrue(KmSplitter.speech(KmSplitter.Km(2, 361_000, 360_000), dur).endsWith("כמו הקילומטר הקודם."))
    }
}

class WarmupIntervalsTest {
    private val iv = Intervals(workMs = 60_000, restMs = 90_000, repeats = 2, warmupMs = 300_000)

    @Test
    fun warmupComesFirstThenTheRounds() {
        assertEquals(300_000L + 2 * 60_000 + 90_000, iv.totalMs)
        iv.at(0).let { assertTrue(it.warmup); assertEquals(0, it.index); assertEquals(300_000L, it.remainingMs) }
        iv.at(300_000).let { assertTrue(it.work); assertEquals(1, it.index); assertEquals(1, it.round) }
        iv.at(370_000).let { assertFalse(it.work); assertFalse(it.warmup); assertEquals(2, it.index) }
        iv.at(450_000).let { assertTrue(it.work); assertEquals(2, it.round); assertEquals(3, it.index) }
        assertNull(iv.at(510_000).remainingMs)
    }

    @Test
    fun oneRoundWithoutRest() {
        val run30 = Intervals(1_800_000, 0, 1, 300_000)
        assertEquals(2_100_000L, run30.totalMs)
        assertTrue(run30.at(400_000).work)
        assertNull(run30.at(2_100_000).remainingMs)
    }
}

class TrainingPlanTest {
    @Test
    fun eightWeeksOfThreeWorkouts() {
        assertEquals(24, TrainingPlan.sessions.size)
        assertEquals((1..8).toList(), TrainingPlan.sessions.map { it.week }.distinct())
        assertEquals(setOf(1, 2, 3), TrainingPlan.sessions.map { it.day }.toSet())
    }

    @Test
    fun runTimeNeverGoesDownAndEndsWith30Minutes() {
        val perWeek = TrainingPlan.sessions.filter { it.day == 1 }.map { it.runSec * it.repeats }
        assertTrue(perWeek.zipWithNext().all { (a, b) -> b >= a })
        val last = TrainingPlan.sessions.last()
        assertEquals(1, last.repeats)
        assertEquals(1800, last.runSec)
        assertEquals("ריצה 30:00 ברצף", last.description)
    }

    @Test
    fun nextIsTheFirstNotDone() {
        assertEquals("1-1", TrainingPlan.next(emptySet())!!.key)
        assertEquals("1-3", TrainingPlan.next(setOf("1-1", "1-2"))!!.key)
        assertEquals("1-2", TrainingPlan.next(setOf("1-1", "1-3"))!!.key) // A skipped one comes first.
        assertNull(TrainingPlan.next(TrainingPlan.sessions.map { it.key }.toSet()))
    }

    @Test
    fun descriptionAndTotalTime() {
        val s = TrainingPlan.byKey("1-1")!!
        assertEquals("8 × ריצה 1:00 / הליכה 1:30", s.description)
        // 5 + 8 x 1 + 7 x 1.5 + 5 = 28.5 -> 29 minutes.
        assertEquals(29, s.totalMin)
    }
}

class ShoeWearTest {
    @Test
    fun crossingTheLimitOnlyOnce() {
        assertTrue(ShoeWear.crossedLimit(totalAfterM = 601_000.0, workoutM = 5_000.0, limitKm = 600.0))
        assertFalse(ShoeWear.crossedLimit(totalAfterM = 599_000.0, workoutM = 5_000.0, limitKm = 600.0))
        // Already over the limit before this workout: no new reminder.
        assertFalse(ShoeWear.crossedLimit(totalAfterM = 610_000.0, workoutM = 5_000.0, limitKm = 600.0))
        assertEquals(0.5, ShoeWear.used(300_000.0, 600.0), 0.0001)
    }
}
