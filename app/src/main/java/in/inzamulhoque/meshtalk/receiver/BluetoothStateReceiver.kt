package `in`.inzamulhoque.meshtalk.receiver

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import `in`.inzamulhoque.meshtalk.service.MeshForegroundService

class BluetoothStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            when (state) {
                BluetoothAdapter.STATE_ON -> {
                    Log.d("BluetoothReceiver", "Bluetooth turned ON, restarting mesh service")
                    try {
                        MeshForegroundService.start(context)
                    } catch (e: Exception) {
                        Log.e("BluetoothReceiver", "Failed to start service from background", e)
                    }
                }
                BluetoothAdapter.STATE_OFF -> {
                    Log.d("BluetoothReceiver", "Bluetooth turned OFF, stopping mesh service")
                    // We keep the service alive but it will stop BLE operations internally via MeshNetworkManager
                }
            }
        }
    }
}
