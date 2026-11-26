package com.example.walkrun

import com.example.walkrun.TestData.track
import com.example.walkrun.TestData.workout
import com.example.walkrun.data.BestEfforts
import com.example.walkrun.data.RecordBook
import com.example.walkrun.data.RecordKind
import com.example.walkrun.data.SplitUnit
import com.example.walkrun.data.Splits
import com.example.walkrun.data.Trend
import com.example.walkrun.data.TrendCalculator
import com.example.walkrun.tracking.Intervals
import com.example.walkrun.util.Geo
import com.example.walkrun.util.TimeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitsTest {
    @Test
    fun kmSplitsFollowThePaceOfEachKm() {
        val s = Splits.of(track(3.0, listOf(300.0, 360.0, 420.0)), SplitUnit.KM)
        assertEquals(3, s.size)
        assertEquals(300.0, s[0].pace, 0.5)
        assertEquals(360.0, s[1].pace, 0.5)
        assertEquals(420.0, s[2].pace, 0.5)
    }

    @Test
    fun partialLastKmIsKeptOnlyFrom200m() {
        assertEquals(3, Splits.of(track(2.5, listOf(360.0)), SplitUnit.KM).size)
        val s = Splits.of(track(2.1, listOf(360.0)), SplitUnit.KM)
        assertEquals(2, s.size) // 100 m left: too short to show.
        assertEquals(0.5, Splits.of(track(2.5, listOf(360.0)), SplitUnit.KM).last().fraction, 0.01)
    }

    @Test
    fun tenMinuteBlocksHaveTheSamePaceAsTheTrack() {
        // 6:00 per km for 4 km = 24 minutes = 2 full blocks and a 4-minute block.
        val s = Splits.of(track(4.0, listOf(360.0)), SplitUnit.TEN_MIN)
        assertEquals(3, s.size)
        s.forEach { assertEquals(360.0, it.pace, 1.0) }
        assertEquals(0.4, s.last().fraction, 0.01)
    }

    @Test
    fun compareAveragesTheSameSplitOfTheHistory() {
        val cur = Splits.of(track(2.0, listOf(300.0)), SplitUnit.KM)
        val h1 = Splits.of(track(2.0, listOf(330.0)), SplitUnit.KM)
        val h2 = Splits.of(track(1.0, listOf(390.0)), SplitUnit.KM) // Only km 1.
        val rows = Splits.compare(cur, listOf(h1, h2))
        assertEquals(360.0, rows[0].baselinePace!!, 0.5) // (330 + 390) / 2
        assertEquals(2, rows[0].baselineCount)
        assertEquals(330.0, rows[1].baselinePace!!, 0.5)
        assertTrue(rows[0].changePct!! > 0) // Faster than the history = positive.
    }
}

class BestEffortsTest {
    @Test
    fun findsTheFastestKmAnywhereInTheWorkout() {
        // km 2 is the fastest: 4:00.
        val best = BestEfforts.best(track(3.0, listOf(360.0, 240.0, 360.0)), 1000.0)!!
        assertEquals(240_000.0, best.toDouble(), 500.0)
    }

    @Test
    fun evenPaceGivesThatPaceForEveryDistance() {
        val pts = track(6.0, listOf(330.0))
        assertEquals(330_000.0, BestEfforts.best(pts, 1000.0)!!.toDouble(), 500.0)
        assertEquals(1_650_000.0, BestEfforts.best(pts, 5000.0)!!.toDouble(), 1000.0)
    }

    @Test
    fun shorterThanTheDistanceGivesNull() {
        assertNull(BestEfforts.best(track(0.9, listOf(360.0)), 1000.0))
        assertNull(BestEfforts.best(emptyList(), 1000.0))
    }

    @Test
    fun fillSetsAllDistances() {
        val w = BestEfforts.fill(workout(distanceM = 6000.0), track(6.0, listOf(300.0)))
        assertNotNull(w.best1kMs)
        assertNotNull(w.best5kMs)
        assertNull(w.best10kMs)
        assertEquals(1_500_000.0, w.best5kMs!!.toDouble(), 1000.0)
        assertTrue(w.bestsDone)
    }
}

class RecordsTest {
    @Test
    fun aRecordNeedsAnEarlierValueAndMustBeatAllOfThem() {
        val old1 = workout(id = 1, distanceM = 5000.0).copy(best1kMs = 330_000)
        val old2 = workout(id = 2, distanceM = 6000.0).copy(best1kMs = 320_000)
        val now = workout(id = 3, distanceM = 6500.0, durationMs = 1_000_000).copy(best1kMs = 310_000)
        val records = RecordBook.newIn(now, listOf(old1, old2))
        assertTrue(RecordKind.KM1 in records)
        assertTrue(RecordKind.LONGEST_DISTANCE in records)
        assertFalse(RecordKind.LONGEST_TIME in records) // 1,000,000 ms is shorter than 1,800,000.
        assertFalse(RecordKind.KM5 in records) // No earlier 5 km value: not a record.
    }

    @Test
    fun aTieIsNotARecord() {
        val old = workout(id = 1).copy(best1kMs = 300_000)
        val now = workout(id = 2).copy(best1kMs = 300_000)
        assertFalse(RecordKind.KM1 in RecordBook.newIn(now, listOf(old)))
    }

    @Test
    fun recordBookPicksTheBestWorkoutForEachKind() {
        val a = workout(id = 1, distanceM = 8000.0).copy(best1kMs = 300_000)
        val b = workout(id = 2, distanceM = 5000.0).copy(best1kMs = 280_000)
        val book = RecordBook.of(listOf(a, b)).associate { it.kind to it.workout.id }
        assertEquals(2L, book[RecordKind.KM1])
        assertEquals(1L, book[RecordKind.LONGEST_DISTANCE])
    }
}

class TrendTest {
    @Test
    fun trendFromTheAveragePace() {
        val prev = workout(distanceM = 5000.0, durationMs = 1_800_000) // 6:00
        assertEquals(Trend.IMPROVED, TrendCalculator.compare(workout(distanceM = 5000.0, durationMs = 1_700_000), prev).first)
        assertEquals(Trend.REGRESSED, TrendCalculator.compare(workout(distanceM = 5000.0, durationMs = 1_900_000), prev).first)
        assertEquals(Trend.SAME, TrendCalculator.compare(workout(distanceM = 5000.0, durationMs = 1_810_000), prev).first)
    }

    @Test
    fun firstAndTooShort() {
        assertEquals(Trend.FIRST, TrendCalculator.compare(workout(), null).first)
        assertEquals(Trend.NOT_COMPARABLE, TrendCalculator.compare(workout(distanceM = 150.0), workout()).first)
    }
}

class IntervalsTest {
    private val iv = Intervals(workMs = 120_000, restMs = 60_000, repeats = 3)

    @Test
    fun phasesInOrder() {
        assertEquals(480_000L, iv.totalMs) // 3 x 120 + 2 x 60 s
        iv.at(0).let { assertTrue(it.work); assertEquals(1, it.round); assertEquals(120_000L, it.remainingMs) }
        iv.at(130_000).let { assertFalse(it.work); assertEquals(1, it.round); assertEquals(50_000L, it.remainingMs) }
        iv.at(180_000).let { assertTrue(it.work); assertEquals(2, it.round) }
        iv.at(479_000).let { assertTrue(it.work); assertEquals(3, it.round); assertEquals(1_000L, it.remainingMs) }
    }

    @Test
    fun noRestAfterTheLastRound() {
        assertNull(iv.at(480_000).remainingMs)
        assertNull(iv.at(1_000_000).remainingMs)
    }

    @Test
    fun phaseIndexChangesAtEachSwitch() {
        val indexes = (0 until 480 step 10).map { iv.at(it * 1000L).index }.distinct()
        assertEquals(listOf(0, 1, 2, 3, 4), indexes)
    }
}

class UtilTest {
    @Test
    fun geoDistance() {
        assertEquals(TestData.M_PER_DEG_LAT, Geo.distanceM(32.0, 34.8, 33.0, 34.8), 1.0)
        assertEquals(0.0, Geo.distanceM(32.0, 34.8, 32.0, 34.8), 0.001)
        // 10 m east at latitude 32.
        val dLon = 10.0 / (TestData.M_PER_DEG_LAT * Math.cos(Math.toRadians(32.0)))
        assertEquals(10.0, Geo.distanceM(32.0, 34.8, 32.0, 34.8 + dLon), 0.01)
    }

    @Test
    fun timeText() {
        assertEquals("2:05", TimeText.mmss(125))
        assertEquals("0:30", TimeText.mmss(30))
        assertEquals(125, TimeText.parse("2:05"))
        assertEquals(125, TimeText.parse(" 2:5 "))
        assertEquals(120, TimeText.parse("2"))
        assertNull(TimeText.parse("2:60"))
        assertNull(TimeText.parse("a:10"))
        assertNull(TimeText.parse(""))
        assertNull(TimeText.parse("1:2:3"))
    }
}
