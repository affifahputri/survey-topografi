package com.example.surveytopografi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatusPermission: TextView
    private lateinit var btnModeAnchor: Button
    private lateinit var btnModeRover: Button

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            tvStatusPermission.text = "Status Izin Sensor: Semua Izin (GPS & BLE) Aktif ✅"
        } else {
            tvStatusPermission.text = "Status Izin Sensor: Ada izin yang belum diaktifkan ⚠️"
            Toast.makeText(this, "Harap izinkan GPS & Bluetooth agar aplikasi berjalan!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatusPermission = findViewById(R.id.tvStatusPermission)
        btnModeAnchor = findViewById(R.id.btnModeAnchor)
        btnModeRover = findViewById(R.id.btnModeRover)

        checkAndRequestPermissions()

        // Pindah ke AnchorActivity (Bagian Affifah)
        btnModeAnchor.setOnClickListener {
            val intent = Intent(this, AnchorActivity::class.java)
            startActivity(intent)
        }

        // Pindah ke RoverActivity (Bagian Izah)
        btnModeRover.setOnClickListener {
            val intent = Intent(this, RoverActivity::class.java)
            startActivity(intent)
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
            tvStatusPermission.text = "Status Izin Sensor: Semua Izin (GPS & BLE) Aktif ✅"
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}