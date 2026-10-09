package com.glowalerts.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

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

    /**
     * Three clear modes from the angle the phone is turned to (like a clock hand): within about
     * 55° of upright or upside down = bottom; otherwise left or right. It has to turn about 15°
     * past a boundary before switching, so holding it diagonally doesn't make it flip back and
     * forth. Lying flat (or close) = bottom.
     */
    override fun onSensorChanged(event: SensorEvent) {
        // Readings point "up": x > 0 means the right edge is up (left edge down).
        val x = event.values[0]
        val y = event.values[1]
        val upright = hypot(x, y)
        if (upright < 4f) {
            // Lying flat or nearly: strike like normal.
            landing = BOTTOM
            return
        }
        // 0° = upright, +90° = left edge down, -90° = right edge down, ±180° = upside down.
        val angle = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
        val tilt = abs(angle).let { if (it > 90f) 180f - it else it } // 0 = up/down, 90 = sideways
        val sidewaysNow = landing != BOTTOM
        val sideways = if (sidewaysNow) tilt > 40f else tilt > 55f
        landing = when {
            !sideways -> BOTTOM
            angle > 0 -> LEFT
            else -> RIGHT
        }
    }

    /**
     * Where to land in screen terms. [landing] is measured against the phone itself; if the
     * screen has turned to landscape too (an app that rotates), its "bottom" already is the
     * side facing down, so convert.
     */
    fun screenLanding(context: Context): Int {
        val mode = landing
        if (mode == BOTTOM) return BOTTOM
        val rotation = runCatching {
            context.getSystemService(android.hardware.display.DisplayManager::class.java)
                .getDisplay(android.view.Display.DEFAULT_DISPLAY).rotation
        }.getOrDefault(0)
        // Phone edges going round: 0 bottom, 1 left, 2 top, 3 right. A turned screen's bottom
        // is the phone's edge number [rotation].
        val phoneEdge = if (mode == LEFT) 1 else 3
        return when (((phoneEdge - rotation) % 4 + 4) % 4) {
            1 -> LEFT
            3 -> RIGHT
            else -> BOTTOM
        }
    }

    /** For the settings screen. */
    fun label(mode: Int = landing) = when (mode) {
        LEFT -> "Strike left"
        RIGHT -> "Strike right"
        else -> "Strike bottom"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
