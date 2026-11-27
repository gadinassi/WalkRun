package com.example.walkrun

import com.example.walkrun.data.RouteGeometry
import com.example.walkrun.tracking.HeartRateMonitor
import com.example.walkrun.tracking.HrZones
import com.example.walkrun.tracking.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class RouteGeometryTest {
    private val lat0 = 32.0
    private val lon0 = 34.8
    private val mLat = TestData.M_PER_DEG_LAT
    private val mLon = TestData.M_PER_DEG_LAT * cos(Math.toRadians(lat0))

    /** A point [north] and [east] meters from the start, every 10 m along each straight part. */
    private fun at(north: Double, east: Double) = LatLon(lat0 + north / mLat, lon0 + east / mLon)
    private fun line(from: Pair<Double, Double>, to: Pair<Double, Double>): List<LatLon> {
        val n = 20
        return (0..n).map { i -> at(from.first + (to.first - from.first) * i / n, from.second + (to.second - from.second) * i / n) }
    }

    /** 200 m north, then 200 m east (right turn), then 200 m north again (left turn). */
    private val route = line(0.0 to 0.0, 200.0 to 0.0) + line(200.0 to 0.0, 200.0 to 200.0).drop(1) +
        line(200.0 to 200.0, 400.0 to 200.0).drop(1)

    @Test
    fun encodeDecodeKeepsThePoints() {
        val back = RouteGeometry.decode(RouteGeometry.encode(route))
        assertEquals(route.size, back.size)
        assertEquals(route[5].lat, back[5].lat, 1e-6)
        assertTrue(RouteGeometry.decode("junk;1,2;x").size == 1)
    }

    @Test
    fun lengthAndBearing() {
        assertEquals(600.0, RouteGeometry.length(route), 2.0)
        assertEquals(0.0, RouteGeometry.bearing(at(0.0, 0.0), at(100.0, 0.0)), 0.5)
        assertEquals(90.0, RouteGeometry.bearing(at(0.0, 0.0), at(0.0, 100.0)), 0.5)
        assertEquals(-90.0, RouteGeometry.turnAngle(10.0, 280.0), 0.001) // Wraps around north.
    }

    @Test
    fun turnsRightThenLeftAtTheirDistance() {
        val turns = RouteGeometry.turns(route)
        assertEquals(2, turns.size)
        assertTrue(turns[0].right)
        assertEquals(200.0, turns[0].atM, 15.0)
        assertFalse(turns[1].right)
        assertEquals(400.0, turns[1].atM, 15.0)
        assertEquals("פנה ימינה", turns[0].speech)
        assertEquals("פנה שמאלה", turns[1].speech)
    }

    @Test
    fun aStraightRouteHasNoTurns() {
        assertTrue(RouteGeometry.turns(line(0.0 to 0.0, 500.0 to 0.0)).isEmpty())
    }

    @Test
    fun projectionGivesDistanceAlongAndOff() {
        // 300 m along the route = 100 m into the eastern part; 20 m north of it.
        val p = RouteGeometry.project(route, at(220.0, 100.0))!!
        assertEquals(300.0, p.alongM, 3.0)
        assertEquals(20.0, p.offM, 1.0)
        assertNull(RouteGeometry.project(listOf(at(0.0, 0.0)), at(1.0, 1.0)))
    }
}

class HeartRateTest {
    @Test
    fun parsesEightAndSixteenBitValues() {
        assertEquals(72, HeartRateMonitor.parse(byteArrayOf(0x00, 72)))
        assertEquals(150, HeartRateMonitor.parse(byteArrayOf(0x00, 150.toByte()))) // Unsigned byte.
        assertEquals(260, HeartRateMonitor.parse(byteArrayOf(0x01, 0x04, 0x01))) // 0x0104 little endian.
        assertNull(HeartRateMonitor.parse(byteArrayOf(0x01, 0x04)))
        assertNull(HeartRateMonitor.parse(byteArrayOf()))
    }

    @Test
    fun zonesFromMaxHeartRate() {
        val max = HrZones.maxHr(40) // 180
        assertEquals(180, max)
        assertEquals(0, HrZones.zone(80, max))  // 44%
        assertEquals(1, HrZones.zone(95, max))  // 53%
        assertEquals(3, HrZones.zone(130, max)) // 72%
        assertEquals(5, HrZones.zone(170, max)) // 94%
    }

    @Test
    fun timeInZonesSkipsGaps() {
        val max = 200
        // 10 s in zone 2 (120 bpm = 60%), then a 60 s gap (pause), then 10 s in zone 4 (160 = 80%).
        val samples = listOf(0L to 120, 5_000L to 120, 10_000L to 160, 70_000L to 160, 80_000L to 160)
        val z = HrZones.timeInZones(samples, max)
        assertEquals(10_000L, z[1])
        assertEquals(10_000L, z[3])
        assertEquals(20_000L, z.sum())
    }
}
