package com.example.lab5.compass

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface

/**
 * Компас: акселерометр + магнітометр → азимут (стандартний підхід Android).
 */
class CompassSensorRepository(
    private val sensorManager: SensorManager,
) : SensorEventListener {

    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var azimuthDeg = 0f
    private var azimuthInitialized = false

    var displayRotation: Int = Surface.ROTATION_0
    var onReading: ((CompassReading) -> Unit)? = null

    private var listening = false

    fun start() {
        if (listening) return
        listening = true
        azimuthInitialized = false
        hasGravity = false
        hasGeomagnetic = false

        val rate = SensorManager.SENSOR_DELAY_UI
        accelerometer?.let { sensorManager.registerListener(this, it, rate) }
        magnetometer?.let { sensorManager.registerListener(this, it, rate) }
    }

    fun stop() {
        if (!listening) return
        listening = false
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                hasGravity = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                hasGeomagnetic = true
            }
            else -> return
        }

        if (!hasGravity || !hasGeomagnetic) return

        val inclination = FloatArray(9)
        if (!SensorManager.getRotationMatrix(rotationMatrix, inclination, gravity, geomagnetic)) {
            return
        }

        remapForDisplay(rotationMatrix, displayRotation, remappedMatrix)
        SensorManager.getOrientation(remappedMatrix, orientation)

        var degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (degrees < 0f) degrees += 360f

        degrees = smoothAngle(degrees)
        azimuthDeg = degrees

        onReading?.invoke(CompassReading(azimuthDeg = azimuthDeg))
    }

    private fun smoothAngle(targetDeg: Float): Float {
        if (!azimuthInitialized) {
            azimuthInitialized = true
            return targetDeg
        }
        val delta = shortestAngleDelta(azimuthDeg, targetDeg)
        return normalizeDegrees(azimuthDeg + delta * SMOOTHING)
    }

    private fun remapForDisplay(inR: FloatArray, rotation: Int, outR: FloatArray) {
        when (rotation) {
            Surface.ROTATION_0 -> SensorManager.remapCoordinateSystem(
                inR, SensorManager.AXIS_X, SensorManager.AXIS_Y, outR,
            )
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                inR, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, outR,
            )
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                inR, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, outR,
            )
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                inR, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, outR,
            )
            else -> System.arraycopy(inR, 0, outR, 0, 9)
        }
    }

    private fun shortestAngleDelta(fromDeg: Float, toDeg: Float): Float {
        var delta = (toDeg - fromDeg) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    private fun normalizeDegrees(deg: Float): Float {
        var d = deg % 360f
        if (d < 0f) d += 360f
        return d
    }

    private companion object {
        private const val SMOOTHING = 0.2f
    }
}
