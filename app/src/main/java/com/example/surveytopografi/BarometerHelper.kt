package com.example.surveytopografi

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location

class BarometerHelper(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private var currentPressure: Float? = null
    var isBarometerAvailable: Boolean = pressureSensor != null
        private set

    fun startListening() {
        pressureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PRESSURE) {
            currentPressure = event.values[0]
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Mengambil ketinggian Z (Barometer dengan Fallback otomatis ke GPS Altitude jika sensor tidak ada)
    fun getAltitudeZ(gpsLocation: Location?): Double {
        return if (isBarometerAvailable && currentPressure != null) {
            SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, currentPressure!!)
                .toDouble()
        } else {
            gpsLocation?.altitude ?: 0.0
        }
    }
}