package com.example.surveytopografi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun rssiToDistance_atTxPower_returnsOneMeter() {
        // Saat nilai RSSI sama dengan txPower (-59 dBm), jarak harus tepat 1.0 meter
        val distance = Trilateration.rssiToDistance(
            rssi = -59.0,
            txPower = -59,
            pathLossExponent = 2.5
        )
        assertEquals(1.0, distance, 0.001)
    }

    @Test
    fun getSmoothedRssi_averagesRecentValues() {
        // Menguji perataan sinyal (Moving Average) untuk meredam fluktuasi RSSI
        Trilateration.getSmoothedRssi("TEST_ANCHOR", -60, windowSize = 3)
        Trilateration.getSmoothedRssi("TEST_ANCHOR", -70, windowSize = 3)
        val avg = Trilateration.getSmoothedRssi("TEST_ANCHOR", -80, windowSize = 3)
        assertEquals(-70.0, avg, 0.01)
    }

    @Test
    fun twoAnchorIntersection_midpoint_returnsSymmetricCoordinate() {
        // Anchor 1 dan Anchor 2 terpisah sejajar di garis lintang yang sama, dengan jarak d1 == d2
        val p1 = Point3D(x = 107.575000, y = -6.873800, z = 840.0)
        val p2 = Point3D(x = 107.575100, y = -6.873800, z = 840.0)

        val result = Trilateration.calculateTwoAnchorIntersection(
            p1 = p1, r1 = 6.0,
            p2 = p2, r2 = 6.0,
            roverZ = 842.5,
            hintLon = 107.575050,
            hintLat = -6.873750
        )

        // Titik X (Longitude) hasil perpotongan harus berada tepat di tengah (107.575050)
        assertEquals(107.575050, result.x, 0.000010)
        assertEquals(842.5, result.z, 0.01)
        assertTrue(result.y != 0.0)
    }
}