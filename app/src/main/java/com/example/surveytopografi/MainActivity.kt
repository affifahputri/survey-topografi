package com.example.surveytopografi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // 1. Variabel untuk komponen tampilan di layar
    private lateinit var tvMyLocation: TextView
    private lateinit var tvPeerList: TextView
    private lateinit var tvResult: TextView
    private lateinit var btnToggleSurvey: Button
    private lateinit var btnCalculate: Button

    // 2. Variabel untuk sensor GPS
    private lateinit var locationManager: LocationManager
    private var myLocation: Location? = null

    // 3. Meminta izin GPS & Bluetooth saat aplikasi pertama kali dibuka
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startGpsUpdates()
        } else {
            Toast.makeText(this, "Izin GPS & Bluetooth wajib diizinkan!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Hubungkan variabel dengan ID di activity_main.xml
        tvMyLocation = findViewById(R.id.tvMyLocation)
        tvPeerList = findViewById(R.id.tvPeerList)
        tvResult = findViewById(R.id.tvResult)
        btnToggleSurvey = findViewById(R.id.btnToggleSurvey)
        btnCalculate = findViewById(R.id.btnCalculate)

        // Siapkan layanan lokasi (GPS)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Cek izin akses saat aplikasi dibuka
        checkAndRequestPermissions()

        // Aksi tombol sementara (untuk dilanjut bareng teman-teman)
        btnToggleSurvey.setOnClickListener {
            Toast.makeText(this, "Fitur BLE siap ditambahkan di tahap berikutnya!", Toast.LENGTH_SHORT).show()
        }

        btnCalculate.setOnClickListener {
            Toast.makeText(this, "Rumus Trilaterasi siap ditambahkan di tahap berikutnya!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            startGpsUpdates()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    // Fungsi Poin 1: Membaca Koordinat & Titik Ketinggian (Altitude) dari permukaan
    @SuppressLint("MissingPermission")
    private fun startGpsUpdates() {
        val listener = LocationListener { loc ->
            myLocation = loc
            tvMyLocation.text = String.format(
                "Data GPS HP Ini:\n" +
                "• Latitude: %.6f\n" +
                "• Longitude: %.6f\n" +
                "• Titik Ketinggian (Altitude): %.2f mdpl\n" +
                "• Error/Akurasi GPS: ±%.1f meter",
                loc.latitude, loc.longitude, loc.altitude, loc.accuracy
            )
        }

        // Ambil data dari satelit GPS dan jaringan setiap 2 detik
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0.5f, listener)
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0.5f, listener)
        }
    }
}