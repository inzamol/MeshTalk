package `in`.inzamulhoque.meshtalk.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.util.Log

/**
 * Uses the low-power Significant Motion Sensor to detect when the device is moved.
 * This is much more battery efficient than using the raw Accelerometer.
 */
class MovementDetector(context: Context, private val onMovementDetected: () -> Unit) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
    
    private val triggerListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            Log.d("MovementDetector", "Significant motion detected")
            onMovementDetected()
            
            // Significant Motion Sensor is a one-shot sensor. 
            // We must re-register after every trigger.
            requestTrigger()
        }
    }
    
    fun start() {
        if (motionSensor == null) {
            Log.w("MovementDetector", "Significant Motion sensor not available on this device")
            return
        }
        requestTrigger()
    }
    
    private fun requestTrigger() {
        motionSensor?.let {
            sensorManager.requestTriggerSensor(triggerListener, it)
        }
    }
    
    fun stop() {
        motionSensor?.let {
            sensorManager.cancelTriggerSensor(triggerListener, it)
        }
    }
}
