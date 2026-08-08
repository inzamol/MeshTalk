package `in`.inzamulhoque.meshtalk.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class MovementDetector(context: Context, private val onMovementDetected: () -> Unit) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private val threshold = 0.5f // Sensitivity threshold for movement
    
    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }
    
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            val deltaX = Math.abs(lastX - x)
            val deltaY = Math.abs(lastY - y)
            val deltaZ = Math.abs(lastZ - z)
            
            // Basic delta check to detect significant movement
            if (deltaX > threshold || deltaY > threshold || deltaZ > threshold) {
                onMovementDetected()
            }
            
            lastX = x
            lastY = y
            lastZ = z
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
