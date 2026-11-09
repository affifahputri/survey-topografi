package com.example.surveytopografi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.graphics.Color
import android.location.Location
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

import com.example.surveytopografi.R
class AnchorActivity : AppCompatActivity() {

    private lateinit var etAnchorId: EditText
    private lateinit var etLatitude: EditText
    private lateinit var etLongitude: EditText
    private lateinit var etAltitude: EditText
    private lateinit var btnGetGps: Button
    private lateinit var btnStartAdvertising: Button
    private lateinit var tvBroadcastStatus: TextView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false

    private val SERVICE_UUID: UUID = UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anchor)

        etAnchorId = findViewById(R.id.etAnchorId)
        etLatitude = findViewById(R.id.etLatitude)
        etLongitude = findViewById(R.id.etLongitude)
        etAltitude = findViewById(R.id.etAltitude)
        btnGetGps = findViewById(R.id.btnGetGps)
        btnStartAdvertising = findViewById(R.id.btnStartAdvertising)
        tvBroadcastStatus = findViewById(R.id.tvBroadcastStatus)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser

        btnGetGps.setOnClickListener {
            fillLocationFromGps()
        }

        btnStartAdvertising.setOnClickListener {
            if (!isAdvertising) {
                startAdvertising()
            } else {
                stopAdvertising()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fillLocationFromGps() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc: Location? = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        if (loc != null) {
            etLatitude.setText(String.format(Locale.US, "%.6f", loc.latitude))
            etLongitude.setText(String.format(Locale.US, "%.6f", loc.longitude))
            etAltitude.setText(String.format(Locale.US, "%.2f", loc.altitude))
            Toast.makeText(this, "Koordinat berhasil diambil dari GPS!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Sinyal GPS belum terbaca, silakan input manual.", Toast.LENGTH_SHORT).show()
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            isAdvertising = true
            tvBroadcastStatus.text = "Status Pemancar: Sedang Memancarkan Sinyal BLE ✅"
            btnStartAdvertising.text = "Hentikan Siaran BLE"
            btnStartAdvertising.setBackgroundColor(Color.DKGRAY)
            Toast.makeText(this@AnchorActivity, "Sinyal Anchor aktif terpancar!", Toast.LENGTH_SHORT).show()
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            isAdvertising = false
            tvBroadcastStatus.text = "Gagal memancarkan sinyal (Kode: $errorCode) ⚠️"
            Toast.makeText(this@AnchorActivity, "Gagal memancarkan sinyal BLE", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (advertiser == null) {
            tvBroadcastStatus.text = "HP / Emulator tidak mendukung BLE Advertising!"
            Toast.makeText(this, "Fitur BLE Advertiser tidak didukung di perangkat ini.", Toast.LENGTH_LONG).show()
            return
        }

        val anchorName = etAnchorId.text.toString().trim().ifEmpty { "ANCHOR" }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID), anchorName.toByteArray(StandardCharsets.UTF_8))
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
        isAdvertising = false
        tvBroadcastStatus.text = "Status Pemancar: Diam (Dimatikan)"
        btnStartAdvertising.text = "Mulai Siarkan Sinyal (BLE Broadcast)"
        btnStartAdvertising.setBackgroundColor(Color.parseColor("#800020"))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isAdvertising) {
            stopAdvertising()
        }
    }
}