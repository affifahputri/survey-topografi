package com.example.surveytopografi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.ParcelUuid
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

class RoverActivity : AppCompatActivity() {

    private lateinit var tvRoverGps: TextView
    private lateinit var btnScanBle: Button
    private lateinit var tvDetectedAnchors: TextView
    private lateinit var etA1Lat: EditText
    private lateinit var etA1Lon: EditText
    private lateinit var etA2Lat: EditText
    private lateinit var etA2Lon: EditText
    private lateinit var btnFillDummyAnchor: Button
    private lateinit var btnCalculateTrilateration: Button
    private lateinit var tvTrilaterationResult: TextView

    private lateinit var locationManager: LocationManager
    private var roverLocation: Location? = null

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var isScanning = false

    // UUID yang sama persis dengan AnchorActivity buatan Affifah
    private val SERVICE_UUID: UUID = UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")

    // Menyimpan jarak (meter) hasil konversi RSSI dari tiap Anchor yang terdeteksi
    private val detectedDistances = mutableMapOf<String, Double>()
    private val detectedRssi = mutableMapOf<String, Double>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rover)

        tvRoverGps = findViewById(R.id.tvRoverGps)
        btnScanBle = findViewById(R.id.btnScanBle)
        tvDetectedAnchors = findViewById(R.id.tvDetectedAnchors)
        etA1Lat = findViewById(R.id.etA1Lat)
        etA1Lon = findViewById(R.id.etA1Lon)
        etA2Lat = findViewById(R.id.etA2Lat)
        etA2Lon = findViewById(R.id.etA2Lon)
        btnFillDummyAnchor = findViewById(R.id.btnFillDummyAnchor)
        btnCalculateTrilateration = findViewById(R.id.btnCalculateTrilateration)
        tvTrilaterationResult = findViewById(R.id.tvTrilaterationResult)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bleScanner = bluetoothAdapter?.bluetoothLeScanner

        startRoverGps()

        btnScanBle.setOnClickListener {
            if (!isScanning) startBleScan() else stopBleScan()
        }

        // Memudahkan simulasi jika ditest di Emulator atau belum input manual
        btnFillDummyAnchor.setOnClickListener {
            val baseLat = roverLocation?.latitude ?: -6.917500
            val baseLon = roverLocation?.longitude ?: 107.619100
            etA1Lat.setText(String.format(Locale.US, "%.6f", baseLat + 0.00004))
            etA1Lon.setText(String.format(Locale.US, "%.6f", baseLon - 0.00003))
            etA2Lat.setText(String.format(Locale.US, "%.6f", baseLat - 0.00003))
            etA2Lon.setText(String.format(Locale.US, "%.6f", baseLon + 0.00004))
            Toast.makeText(this, "Koordinat simulasi Anchor 1 & 2 terisi!", Toast.LENGTH_SHORT).show()
        }

        btnCalculateTrilateration.setOnClickListener {
            performTrilateration()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRoverGps() {
        val listener = LocationListener { loc ->
            updateGpsUi(loc)
        }
        val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (lastGps != null) updateGpsUi(lastGps)

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, listener)
        }
    }

    private fun updateGpsUi(loc: Location) {
        roverLocation = loc
        tvRoverGps.text = String.format(
            Locale.US,
            "GPS Rover (Titik 3):\n• Latitude: %.6f\n• Longitude: %.6f\n• Ketinggian (z): %.2f mdpl (±%.1f m)",
            loc.latitude, loc.longitude, loc.altitude, loc.accuracy
        )
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result ?: return

            val serviceData = result.scanRecord?.getServiceData(ParcelUuid(SERVICE_UUID))
            val anchorId = if (serviceData != null) {
                String(serviceData, StandardCharsets.UTF_8)
            } else {
                result.device?.address ?: "UNKNOWN_ANCHOR"
            }

            // Haluskan RSSI dan ubah jadi Jarak (meter) menggunakan class Trilateration
            val smoothedRssi = Trilateration.getSmoothedRssi(anchorId, result.rssi)
            val distance = Trilateration.rssiToDistance(smoothedRssi)

            detectedRssi[anchorId] = smoothedRssi
            detectedDistances[anchorId] = distance

            updateAnchorListUi()
        }
    }

    private fun updateAnchorListUi() {
        if (detectedDistances.isEmpty()) {
            tvDetectedAnchors.text = "Anchor BLE Terdeteksi:\nBelum ada sinyal Anchor..."
            return
        }
        val sb = StringBuilder("Anchor BLE Terdeteksi:\n")
        for ((id, dist) in detectedDistances) {
            val rssi = detectedRssi[id] ?: 0.0
            sb.append(String.format(Locale.US, "• %s | RSSI: %.1f dBm -> Jarak: %.2f m\n", id, rssi, dist))
        }
        tvDetectedAnchors.text = sb.toString().trim()
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (bleScanner == null) {
            Toast.makeText(this, "Bluetooth BLE Scanner tidak tersedia (Aktifkan Bluetooth HP)", Toast.LENGTH_LONG).show()
            return
        }
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(listOf(filter), settings, scanCallback)
        isScanning = true
        btnScanBle.text = "Hentikan Scan BLE"
        btnScanBle.setBackgroundColor(Color.DKGRAY)
        Toast.makeText(this, "Memindai sinyal Anchor di sekitar...", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        bleScanner?.stopScan(scanCallback)
        isScanning = false
        btnScanBle.text = "Mulai Scan Sinyal Anchor (BLE)"
        btnScanBle.setBackgroundColor(Color.parseColor("#800020"))
    }

    private fun performTrilateration() {
        val a1Lat = etA1Lat.text.toString().toDoubleOrNull()
        val a1Lon = etA1Lon.text.toString().toDoubleOrNull()
        val a2Lat = etA2Lat.text.toString().toDoubleOrNull()
        val a2Lon = etA2Lon.text.toString().toDoubleOrNull()

        if (a1Lat == null || a1Lon == null || a2Lat == null || a2Lon == null) {
            Toast.makeText(this, "Isi koordinat Anchor 1 & 2 dulu (atau klik tombol Isi Otomatis)!", Toast.LENGTH_SHORT).show()
            return
        }

        val myLat = roverLocation?.latitude ?: -6.917500
        val myLon = roverLocation?.longitude ?: 107.619100
        val myAlt = roverLocation?.altitude ?: 700.0

        // Ambil jarak dari BLE yang terdeteksi (atau gunakan 3.5m & 4.2m jika sedang simulasi tanpa HP teman)
        val distList = detectedDistances.values.toList()
        val d1 = if (distList.isNotEmpty()) distList[0] else 3.5
        val d2 = if (distList.size >= 2) distList[1] else 4.2
        val d3 = (roverLocation?.accuracy?.toDouble() ?: 2.0).coerceAtMost(5.0)

        val resultPoint = Trilateration.calculatePosition(
            Point2D(a1Lat, a1Lon), d1,
            Point2D(a2Lat, a2Lon), d2,
            Point2D(myLat, myLon), d3
        )

        tvTrilaterationResult.text = String.format(
            Locale.US,
            "Hasil Perhitungan Trilaterasi:\n" +
                    "• X (Longitude): %.6f\n" +
                    "• Y (Latitude): %.6f\n" +
                    "• Z (Ketinggian): %.2f mdpl\n" +
                    "• Jarak BLE: d1=%.2fm, d2=%.2fm",
            resultPoint.lon, resultPoint.lat, myAlt, d1, d2
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isScanning) stopBleScan()
    }
}