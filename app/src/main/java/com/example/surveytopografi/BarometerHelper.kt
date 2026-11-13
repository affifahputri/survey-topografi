package com.example.surveytopografi

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.pow

class BarometerHelper(
    context: Context,
    private val onAltitudeChanged: (altitude: Double, pressure: Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private var seaLevelPressure: Float = SensorManager.PRESSURE_STANDARD_ATMOSPHERE
    private var lastPressure: Float = 0f

    val hasSensor: Boolean get() = pressureSensor != null

    fun start() {
        pressureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun calibrateWithReferenceAltitude(referenceAlt: Double) {
        if (lastPressure > 0f) {
            seaLevelPressure = (lastPressure / (1.0 - (referenceAlt / 44330.0)).pow(5.255)).toFloat()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PRESSURE) {
            val pressure = event.values[0]
            lastPressure = pressure
            val altitude = SensorManager.getAltitude(seaLevelPressure, pressure).toDouble()
            onAltitudeChanged(altitude, pressure)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}