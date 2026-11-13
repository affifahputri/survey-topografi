package com.example.surveytopografi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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
import java.nio.ByteBuffer
import java.util.Locale

@SuppressLint("MissingPermission", "SetTextI18n")
class RoverActivity : AppCompatActivity() {

    private var bleScanner: BluetoothLeScanner? = null
    private var isScanning = false
    private lateinit var locationManager: LocationManager
    private lateinit var barometerHelper: BarometerHelper

    private val detectedDistances = mutableMapOf<String, Double>()
    private val detectedRssi = mutableMapOf<String, Int>()
    private var isBaroCalibrated = false

    private var currentAltitude: Double = 0.0
    private var currentPressure: Float = 0f
    private var lastGpsLocation: Location? = null
    private var lastCalculatedPoint: SurveyPoint? = null
    private val savedPoints = mutableListOf<SurveyPoint>()

    // Semua komponen UI dideklarasikan di luar onCreate agar bisa diakses semua fungsi
    private lateinit var tvRoverGps: TextView
    private lateinit var btnScanBle: Button
    private lateinit var tvDetectedAnchors: TextView
    private lateinit var etA1Lat: EditText
    private lateinit var etA1Lon: EditText
    private lateinit var etA2Lat: EditText
    private lateinit var etA2Lon: EditText
    private lateinit var tvResult: TextView
    private lateinit var tvSavedPoints: TextView

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
        tvResult = findViewById(R.id.tvTrilaterationResult)
        tvSavedPoints = findViewById(R.id.tvSavedPoints)

        val btnFillDummy = findViewById<Button>(R.id.btnFillDummyAnchor)
        val btnCalculate = findViewById<Button>(R.id.btnCalculateTrilateration)
        val btnSavePoint = findViewById<Button>(R.id.btnSavePoint)
        val btnExportCsv = findViewById<Button>(R.id.btnExportCsv)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bleScanner = bluetoothManager.adapter?.bluetoothLeScanner
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        barometerHelper = BarometerHelper(this) { altitude, pressure ->
            currentAltitude = altitude
            currentPressure = pressure
            updateSensorDisplay()
        }
        barometerHelper.start()

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 2000L, 1f, gpsListener
            )
        } catch (_: Exception) {}

        btnScanBle.setOnClickListener {
            if (!isScanning) startBleScan() else stopBleScan()
        }

        btnFillDummy.setOnClickListener {
            val baseLat = lastGpsLocation?.latitude ?: -6.873797
            val baseLon = lastGpsLocation?.longitude ?: 107.575120
            etA1Lat.setText(String.format(Locale.US, "%.6f", baseLat + 0.000030))
            etA1Lon.setText(String.format(Locale.US, "%.6f", baseLon - 0.000030))
            etA2Lat.setText(String.format(Locale.US, "%.6f", baseLat - 0.000030))
            etA2Lon.setText(String.format(Locale.US, "%.6f", baseLon + 0.000030))

            detectedDistances["ANCHOR_1"] = 3.50
            detectedDistances["ANCHOR_2"] = 4.20
            detectedRssi["ANCHOR_1"] = -68
            detectedRssi["ANCHOR_2"] = -71
            updateBleDisplay()
            Toast.makeText(this, "Data simulasi 2 Anchor diterapkan", Toast.LENGTH_SHORT).show()
        }

        btnCalculate.setOnClickListener { performTrilateration() }

        btnSavePoint.setOnClickListener {
            val pt = lastCalculatedPoint
            if (pt == null) {
                Toast.makeText(this, "Hitung posisi trilaterasi terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val newPoint = pt.copy(pointNumber = savedPoints.size + 1)
            savedPoints.add(newPoint)
            updateSavedPointsList()
            Toast.makeText(this, "Titik #${newPoint.pointNumber} berhasil disimpan!", Toast.LENGTH_SHORT).show()
        }

        btnExportCsv.setOnClickListener {
            val path = ExportHelper.exportToCsv(this, savedPoints)
            if (path != null) {
                Toast.makeText(this, "File CSV disimpan di: $path", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Belum ada titik survey yang disimpan!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performTrilateration() {
        val a1Lat = etA1Lat.text.toString().toDoubleOrNull()
        val a1Lon = etA1Lon.text.toString().toDoubleOrNull()
        val a2Lat = etA2Lat.text.toString().toDoubleOrNull()
        val a2Lon = etA2Lon.text.toString().toDoubleOrNull()

        if (a1Lat == null || a1Lon == null || a2Lat == null || a2Lon == null) {
            Toast.makeText(this, "Koordinat Anchor 1 dan Anchor 2 wajib diisi!", Toast.LENGTH_SHORT).show()
            return
        }

        val d1 = detectedDistances["ANCHOR_1"]
        val d2 = detectedDistances["ANCHOR_2"]

        if (d1 == null || d2 == null) {
            Toast.makeText(this, "Sinyal BLE ANCHOR_1 & ANCHOR_2 belum lengkap! Scan atau gunakan Simulasi.", Toast.LENGTH_LONG).show()
            return
        }

        val elevZ = if (barometerHelper.hasSensor && currentAltitude != 0.0) {
            currentAltitude
        } else {
            lastGpsLocation?.altitude ?: 800.0
        }

        val p1 = Point3D(a1Lon, a1Lat, elevZ)
        val p2 = Point3D(a2Lon, a2Lat, elevZ)

        val result = Trilateration.calculateTwoAnchorIntersection(
            p1 = p1, r1 = d1,
            p2 = p2, r2 = d2,
            roverZ = elevZ,
            hintLon = lastGpsLocation?.longitude,
            hintLat = lastGpsLocation?.latitude
        )

        lastCalculatedPoint = SurveyPoint(
            pointNumber = savedPoints.size + 1,
            longitude = result.x,
            latitude = result.y,
            altitude = result.z,
            d1 = d1,
            d2 = d2
        )

        tvResult.text = String.format(
            Locale.US,
            "Hasil Perhitungan Trilaterasi (2 Anchor):\n" +
                    "• X (Longitude): %.6f\n" +
                    "• Y (Latitude): %.6f\n" +
                    "• Z (Elevasi): %.2f mdpl\n" +
                    "• Jarak BLE: d1=%.2fm, d2=%.2fm",
            result.x, result.y, result.z, d1, d2
        )
    }

    private fun startBleScan() {
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter?.isEnabled != true) {
            Toast.makeText(this, "Bluetooth belum aktif!", Toast.LENGTH_SHORT).show()
            return
        }
        bleScanner = adapter.bluetoothLeScanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        // Tanpa filter hardware: penyaringan Anchor dilakukan di scanCallback lewat service data
        bleScanner?.startScan(null, settings, scanCallback)
        isScanning = true
        tvDetectedAnchors.text = "Anchor BLE Terdeteksi:\nMemindai... (belum ada Anchor)"
        btnScanBle.text = "Hentikan Scan BLE"
    }

    private fun stopBleScan() {
        bleScanner?.stopScan(scanCallback)
        isScanning = false
        btnScanBle.text = "Mulai Scan Sinyal Anchor (BLE)"
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Hanya proses paket yang membawa service data Anchor kita
            val serviceData = result.scanRecord?.getServiceData(ParcelUuid(AnchorActivity.SURVEY_UUID))
                ?: return
            if (serviceData.size < 13) return

            val rssi = result.rssi
            val deviceKey = result.device?.address ?: "ANCHOR"
            val smoothedRssi = Trilateration.getSmoothedRssi(deviceKey, rssi)
            val dist = Trilateration.rssiToDistance(smoothedRssi)

            run {
                val buffer = ByteBuffer.wrap(serviceData)
                val anchorNum = buffer.get().toInt()
                val lat = buffer.int / 1e6
                val lon = buffer.int / 1e6
                val alt = buffer.float.toDouble()

                val idKey = "ANCHOR_$anchorNum"
                detectedDistances[idKey] = dist
                detectedRssi[idKey] = rssi

                if (!isBaroCalibrated && alt > 0) {
                    barometerHelper.calibrateWithReferenceAltitude(alt)
                    isBaroCalibrated = true
                }

                if (anchorNum == 1) {
                    etA1Lat.setText(String.format(Locale.US, "%.6f", lat))
                    etA1Lon.setText(String.format(Locale.US, "%.6f", lon))
                } else if (anchorNum == 2) {
                    etA2Lat.setText(String.format(Locale.US, "%.6f", lat))
                    etA2Lon.setText(String.format(Locale.US, "%.6f", lon))
                }
            }
            updateBleDisplay()
        }
    }

    private fun updateBleDisplay() {
        if (detectedDistances.isEmpty()) return
        val lines = detectedDistances.keys.sorted().map { key ->
            val rssi = detectedRssi[key] ?: 0
            val dist = detectedDistances[key] ?: 0.0
            "• $key | RSSI: $rssi dBm -> Jarak: ${String.format(Locale.US, "%.2f", dist)} m"
        }
        tvDetectedAnchors.text = "Anchor BLE Terdeteksi:\n" + lines.joinToString("\n")
    }

    private fun updateSensorDisplay() {
        val baroInfo = if (barometerHelper.hasSensor) {
            String.format(Locale.US, "%.2f mdpl (%.1f hPa)", currentAltitude, currentPressure)
        } else {
            "${String.format(Locale.US, "%.2f", lastGpsLocation?.altitude ?: 0.0)} mdpl (Fallback GPS)"
        }
        val gpsText = lastGpsLocation?.let {
            String.format(Locale.US, "%.6f, %.6f (±%.1fm)", it.latitude, it.longitude, it.accuracy)
        } ?: "Mencari sinyal GPS..."

        tvRoverGps.text = "Sensor Rover:\n• Elevasi (z): $baroInfo\n• GPS Referensi: $gpsText"
    }

    private fun updateSavedPointsList() {
        val sb = StringBuilder("Daftar Titik Survey Tersimpan (${savedPoints.size} titik):\n")
        savedPoints.forEach {
            sb.append(
                String.format(
                    Locale.US,
                    "#%d: X=%.6f, Y=%.6f, Z=%.2fm\n",
                    it.pointNumber, it.longitude, it.latitude, it.altitude
                )
            )
        }
        tvSavedPoints.text = sb.toString().trim()
    }

    private val gpsListener = LocationListener { loc ->
        lastGpsLocation = loc
        if (!isBaroCalibrated && loc.hasAltitude() && loc.altitude != 0.0) {
            barometerHelper.calibrateWithReferenceAltitude(loc.altitude)
            isBaroCalibrated = true
        }
        updateSensorDisplay()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isScanning) {
            try { bleScanner?.stopScan(scanCallback) } catch (_: Exception) {}
        }
        try { locationManager.removeUpdates(gpsListener) } catch (_: Exception) {}
        try { barometerHelper.stop() } catch (_: Exception) {}
    }
}