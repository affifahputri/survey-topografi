package com.example.surveytopografi

import kotlin.math.*

data class Point2D(val lat: Double, val lon: Double)

object Trilateration {

    private const val EARTH_RADIUS = 6371000.0 // Jari-jari bumi dalam meter

    // Menyimpan riwayat RSSI tiap Anchor untuk menghaluskan sinyal (Moving Average)
    private val rssiHistory = mutableMapOf<String, MutableList<Int>>()

    fun getSmoothedRssi(anchorId: String, newRssi: Int, windowSize: Int = 5): Double {
        val list = rssiHistory.getOrPut(anchorId) { mutableListOf() }
        list.add(newRssi)
        if (list.size > windowSize) {
            list.removeAt(0)
        }
        return list.average()
    }

    // 1. Konversi RSSI Bluetooth (dBm) menjadi Jarak (Meter) - Rumus Log-Distance Path Loss
    fun rssiToDistance(rssi: Double, txPower: Int = -59, pathLossExponent: Double = 2.5): Double {
        return 10.0.pow((txPower - rssi) / (10.0 * pathLossExponent))
    }

    // 2. Hitung posisi (Latitude, Longitude) dari 3 titik patokan dan jaraknya (d1, d2, d3)
    fun calculatePosition(
        p1: Point2D, d1: Double,
        p2: Point2D, d2: Double,
        p3: Point2D, d3: Double
    ): Point2D {
        // Konversi derajat Latitude/Longitude ke koordinat kartesius lokal (meter) dengan p1 sebagai titik (0,0)
        val lat0 = Math.toRadians(p1.lat)
        val lon0 = Math.toRadians(p1.lon)

        fun toXY(p: Point2D): Pair<Double, Double> {
            val x = EARTH_RADIUS * (Math.toRadians(p.lon) - lon0) * cos(lat0)
            val y = EARTH_RADIUS * (Math.toRadians(p.lat) - lat0)
            return Pair(x, y)
        }

        val (x1, y1) = Pair(0.0, 0.0)
        val (x2, y2) = toXY(p2)
        val (x3, y3) = toXY(p3)

        // Persamaan linear Trilaterasi 2D: A*x + B*y = C dan D*x + E*y = F
        val A = 2 * (x2 - x1)
        val B = 2 * (y2 - y1)
        val C = d1.pow(2) - d2.pow(2) - x1.pow(2) + x2.pow(2) - y1.pow(2) + y2.pow(2)

        val D = 2 * (x3 - x2)
        val E = 2 * (y3 - y2)
        val F = d2.pow(2) - d3.pow(2) - x2.pow(2) + x3.pow(2) - y2.pow(2) + y3.pow(2)

        val denominator = (A * E - B * D)

        // Jika ke-3 titik segaris (kolinear) atau jarak tidak berpotongan, gunakan rata-rata berbobot jarak
        if (abs(denominator) < 1e-5) {
            val w1 = 1.0 / max(d1, 0.5)
            val w2 = 1.0 / max(d2, 0.5)
            val w3 = 1.0 / max(d3, 0.5)
            val totalW = w1 + w2 + w3
            return Point2D(
                (p1.lat * w1 + p2.lat * w2 + p3.lat * w3) / totalW,
                (p1.lon * w1 + p2.lon * w2 + p3.lon * w3) / totalW
            )
        }

        val x = (C * E - F * B) / denominator
        val y = (A * F - D * C) / denominator

        // Kembalikan dari meter (x, y) ke derajat Latitude & Longitude
        val resLat = Math.toDegrees(lat0 + (y / EARTH_RADIUS))
        val resLon = Math.toDegrees(lon0 + (x / (EARTH_RADIUS * cos(lat0))))

        return Point2D(resLat, resLon)
    }
}