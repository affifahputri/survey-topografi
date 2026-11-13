package com.example.surveytopografi

import kotlin.math.*

data class Point3D(val x: Double, val y: Double, val z: Double)

object Trilateration {
    // Menghaluskan RSSI menggunakan moving average
    private val rssiHistory = mutableMapOf<String, MutableList<Int>>()

    fun getSmoothedRssi(anchorId: String, newRssi: Int, windowSize: Int = 5): Double {
        val list = rssiHistory.getOrPut(anchorId) { mutableListOf() }
        list.add(newRssi)
        if (list.size > windowSize) {
            list.removeAt(0)
        }
        return list.average()
    }

    // Konversi RSSI (dBm) menjadi Jarak (Meter)
    fun rssiToDistance(rssi: Double, txPower: Int = -59, pathLossExponent: Double = 2.5): Double {
        return 10.0.pow((txPower - rssi) / (10.0 * pathLossExponent))
    }

    // Perhitungan Posisi Rover menggunakan 2 Anchor (Perpotongan 2 Lingkaran)
    fun calculateTwoAnchorIntersection(
        p1: Point3D, r1: Double,
        p2: Point3D, r2: Double,
        roverZ: Double,
        hintLon: Double? = null,
        hintLat: Double? = null
    ): Point3D {
        val latScale = 111320.0
        val lonScale = 111320.0 * cos(Math.toRadians(p1.y))

        val dx = (p2.x - p1.x) * lonScale
        val dy = (p2.y - p1.y) * latScale
        val d = hypot(dx, dy)

        if (d < 1e-4) {
            return Point3D(p1.x, p1.y, roverZ)
        }

        var d1 = r1
        var d2 = r2
        if (d > d1 + d2) {
            val scale = (d / (d1 + d2)) + 0.001
            d1 *= scale
            d2 *= scale
        } else if (d < abs(d1 - d2)) {
            if (d1 > d2) d1 = d2 + d - 0.001 else d2 = d1 + d - 0.001
        }

        val a = (d1.pow(2) - d2.pow(2) + d.pow(2)) / (2.0 * d)
        val h = sqrt(max(0.0, d1.pow(2) - a.pow(2)))

        val xMid = a * (dx / d)
        val yMid = a * (dy / d)

        val rx = -dy * (h / d)
        val ry = dx * (h / d)

        val cand1X = xMid + rx
        val cand1Y = yMid + ry
        val cand2X = xMid - rx
        val cand2Y = yMid - ry

        val lon1 = p1.x + (cand1X / lonScale)
        val lat1 = p1.y + (cand1Y / latScale)
        val lon2 = p1.x + (cand2X / lonScale)
        val lat2 = p1.y + (cand2Y / latScale)

        if (hintLon != null && hintLat != null) {
            val err1 = hypot((lon1 - hintLon) * lonScale, (lat1 - hintLat) * latScale)
            val err2 = hypot((lon2 - hintLon) * lonScale, (lat2 - hintLat) * latScale)
            return if (err1 <= err2) Point3D(lon1, lat1, roverZ) else Point3D(lon2, lat2, roverZ)
        }

        return Point3D(lon1, lat1, roverZ)
    }
}