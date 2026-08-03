package `in`.inzamulhoque.meshtalk.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.os.ParcelUuid
import android.util.Log

class MeshBLEManager(
    private val bluetoothAdapter: BluetoothAdapter,
    private val onPeerDiscovered: (String) -> Unit // deviceAddress
) {
    private val advertiser: BluetoothLeAdvertiser? = bluetoothAdapter.bluetoothLeAdvertiser
    private val scanner: BluetoothLeScanner? = bluetoothAdapter.bluetoothLeScanner

    private var isScanning = false
    private var isAdvertising = false

    fun startAdvertising() {
        if (isAdvertising) return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(MeshConstants.SERVICE_UUID))
            .build()

        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
            isAdvertising = true
        } catch (e: SecurityException) {
            Log.e("MeshBLEManager", "Permission denied for advertising", e)
        }
    }

    fun stopAdvertising() {
        if (!isAdvertising) return
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
        } catch (e: SecurityException) {
            Log.e("MeshBLEManager", "Permission denied for stopping advertising", e)
        }
    }

    fun startScanning() {
        if (isScanning) return
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(MeshConstants.SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner?.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
        } catch (e: SecurityException) {
            Log.e("MeshBLEManager", "Permission denied for scanning", e)
        }
    }

    fun stopScanning() {
        if (!isScanning) return
        try {
            scanner?.stopScan(scanCallback)
            isScanning = false
        } catch (e: SecurityException) {
            Log.e("MeshBLEManager", "Permission denied for stopping scanning", e)
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("MeshBLEManager", "Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("MeshBLEManager", "Advertising failed: $errorCode")
            isAdvertising = false
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d("MeshBLEManager", "Peer discovered: ${result.device.address}")
            onPeerDiscovered(result.device.address)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("MeshBLEManager", "Scan failed: $errorCode")
            isScanning = false
        }
    }
}
