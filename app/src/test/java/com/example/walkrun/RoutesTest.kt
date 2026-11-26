package com.example.walkrun

import com.example.walkrun.TestData.point
import com.example.walkrun.TestData.track
import com.example.walkrun.TestData.workout
import com.example.walkrun.data.Analysis
import com.example.walkrun.data.SegmentMatcher
import com.example.walkrun.report.GpxExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SegmentMatcherTest {
    private val base = track(3.0, listOf(360.0, 300.0, 360.0))
    private val segment = SegmentMatcher.create("km 2", base, 1000.0, 2000.0)!!

    @Test
    fun createdSegmentHasTheChosenLength() {
        assertEquals(1000.0, segment.lengthM, 10.0)
        assertNull(SegmentMatcher.create("short", base, 1000.0, 1030.0)) // Under 50 m.
    }

    @Test
    fun theSameWorkoutMatchesWithItsOwnTime() {
        assertEquals(300_000.0, SegmentMatcher.bestTime(segment, base)!!.toDouble(), 3_000.0)
    }

    @Test
    fun anotherWorkoutOnTheSameRouteGetsItsTime() {
        val other = track(3.0, listOf(330.0)) // Same line, 5:30 all the way.
        assertEquals(330_000.0, SegmentMatcher.bestTime(segment, other)!!.toDouble(), 3_000.0)
    }

    @Test
    fun aWorkoutElsewhereDoesNotMatch() {
        assertNull(SegmentMatcher.bestTime(segment, track(3.0, listOf(300.0), lon0 = 34.9)))
    }

    @Test
    fun theOppositeDirectionDoesNotMatch() {
        val back = base.reversed().mapIndexed { i, p ->
            p.copy(distanceM = 3000.0 - p.distanceM, elapsedMs = i * 3_000L)
        }
        assertNull(SegmentMatcher.bestTime(segment, back))
    }

    @Test
    fun aWorkoutThatStopsInsideTheSegmentDoesNotMatch() {
        assertNull(SegmentMatcher.bestTime(segment, track(1.6, listOf(300.0))))
    }

    @Test
    fun twoPassesGiveTheFasterOne() {
        // Out and back twice on the same line would be complex; two laps of a loop are simulated by
        // a second pass that starts again at the start point after the first one.
        val lap1 = track(2.2, listOf(360.0))
        val lap2 = track(2.2, listOf(300.0), startElapsedMs = lap1.last().elapsedMs + 60_000, startDistanceM = 2300.0)
        val time = SegmentMatcher.bestTime(segment, lap1 + lap2)!!
        assertEquals(300_000.0, time.toDouble(), 3_000.0)
    }
}

class AnalysisTest {
    private val zone = ZoneId.systemDefault()
    private fun on(day: LocalDate, km: Double = 5.0) =
        workout(startTime = day.atTime(8, 0).atZone(zone).toInstant().toEpochMilli(), distanceM = km * 1000)

    @Test
    fun streakCountsDaysInARowUpToToday() {
        val today = LocalDate.of(2026, 11, 26)
        val w = listOf(on(today), on(today.minusDays(1)), on(today.minusDays(2)), on(today.minusDays(5)))
        val s = Analysis.streaks(w, 0.0, today)
        assertEquals(3, s.currentDays)
        assertEquals(3, s.longestDays)
        assertNull(s.goalWeeks)
    }

    @Test
    fun noWorkoutTodayYetKeepsYesterdaysStreak() {
        val today = LocalDate.of(2026, 11, 26)
        val s = Analysis.streaks(listOf(on(today.minusDays(1)), on(today.minusDays(2))), 0.0, today)
        assertEquals(2, s.currentDays)
    }

    @Test
    fun weeksAtGoalAreCountedBackFromLastWeek() {
        val today = LocalDate.of(2026, 11, 26) // Thursday. Week starts Sunday 22/11.
        val w = listOf(
            on(LocalDate.of(2026, 11, 16), 12.0), // Week of 15/11: 12 km
            on(LocalDate.of(2026, 11, 10), 11.0), // Week of 8/11: 11 km
            on(LocalDate.of(2026, 11, 3), 4.0)    // Week of 1/11: 4 km (below the goal)
        )
        assertEquals(2, Analysis.streaks(w, 10.0, today).goalWeeks)
    }

    @Test
    fun pacePiecesAreAbout100mWithTheirPace() {
        val pieces = Analysis.pacePieces(track(1.0, listOf(360.0)))
        assertEquals(10, pieces.size)
        pieces.forEach { assertEquals(360.0, it.second, 1.0) }
    }

    @Test
    fun aPauseStartsANewPiece() {
        val a = track(0.15, listOf(360.0), segment = 0)
        val b = track(0.15, listOf(360.0), segment = 1, startElapsedMs = 200_000, startDistanceM = 150.0)
        // Each segment: one full 100 m piece and one 50 m rest piece; nothing joins across the pause.
        assertEquals(4, Analysis.pacePieces(a + b).size)
    }

    @Test
    fun heatColors() {
        val green = Analysis.heatColor(300.0, 360.0)
        val red = Analysis.heatColor(420.0, 360.0)
        val yellow = Analysis.heatColor(360.0, 360.0)
        assertEquals(0xFF2E7D32.toInt(), green)
        assertEquals(0xFFC62828.toInt(), red)
        assertEquals(0xFFF9A825.toInt(), yellow)
    }

    @Test
    fun elevationProfileIsSmoothedAndNeedsAltitude() {
        assertTrue(Analysis.elevationProfile(track(1.0, listOf(360.0))).isEmpty())
        val pts = (0..20).map { point(32.0 + it * 0.0001, 34.8, it * 10.0, it * 3_600L, alt = if (it == 10) 50.0 else 10.0) }
        val prof = Analysis.elevationProfile(pts)
        assertEquals(21, prof.size)
        assertTrue(prof[10].second < 50.0) // The single jump is smoothed.
    }

    @Test
    fun monthlyTotals() {
        val nov = java.time.YearMonth.of(2026, 11)
        val w = listOf(on(LocalDate.of(2026, 11, 2), 5.0), on(LocalDate.of(2026, 11, 20), 7.0), on(LocalDate.of(2026, 10, 5), 3.0))
        val m = Analysis.monthly(w, 2, nov)
        assertEquals(listOf(10, 11), m.map { it.month.monthValue })
        assertEquals(3.0, m[0].km, 0.001)
        assertEquals(12.0, m[1].km, 0.001)
        assertEquals(2, m[1].count)
    }
}

class GpxExportTest {
    @Test
    fun oneTrackSegmentPerPauseAndRunningType() {
        val pts = track(0.1, listOf(300.0), segment = 0) + track(0.1, listOf(300.0), segment = 1, startElapsedMs = 100_000)
        val gpx = GpxExport.build(workout(type = "RUN"), pts)
        assertEquals(2, Regex("<trkseg>").findAll(gpx).count())
        assertEquals(pts.size, Regex("<trkpt ").findAll(gpx).count())
        assertTrue(gpx.contains("<type>running</type>"))
        assertTrue(gpx.startsWith("<?xml"))
    }

    @Test
    fun titleIsEscaped() {
        val gpx = GpxExport.build(workout().copy(title = "Park <&> loop"), track(0.05, listOf(300.0)))
        assertTrue(gpx.contains("Park &lt;&amp;&gt; loop"))
        assertNotNull(gpx)
    }
}
