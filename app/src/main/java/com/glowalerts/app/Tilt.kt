package com.glowalerts.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * Which way the phone is being held, so the lightning lands at the bottom of the screen as you
 * see it. Upright, upside down and lying flat all count as upright.
 */
object Tilt : SensorEventListener {
    const val BOTTOM = 0
    /** Turned sideways with the screen's left edge down. */
    const val LEFT = 1
    /** Turned sideways with the screen's right edge down. */
    const val RIGHT = 2

    @Volatile
    var landing = BOTTOM
        private set

    private var started = false

    fun start(context: Context) {
        if (started) return
        val sm = context.applicationContext.getSystemService(SensorManager::class.java) ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        started = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Readings point "up": x > 0 means the right edge is up (left edge down).
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        landing = when {
            abs(z) > 8f -> BOTTOM // lying flat or nearly
            abs(x) > abs(y) * 1.2f && abs(x) > 4f -> if (x > 0) LEFT else RIGHT
            abs(y) > abs(x) * 0.8f -> BOTTOM
            else -> landing // in between: keep what it was
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
