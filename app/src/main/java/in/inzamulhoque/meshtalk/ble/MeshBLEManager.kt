package `in`.inzamulhoque.meshtalk.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.os.ParcelUuid
import android.util.Log

class MeshBLEManager(
    private val bluetoothAdapter: BluetoothAdapter,
    private val onPeerDiscovered: (String) -> Unit // deviceAddress
) {
    private var isScanning = false
    private var isAdvertising = false

    fun startAdvertising() {
        Log.d("MeshBLEManager", "Requesting to start advertising. isAdvertising: $isAdvertising")
        if (isAdvertising) return
        
        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e("MeshBLEManager", "BluetoothLeAdvertiser is null!")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(MeshConstants.SERVICE_UUID))
            .build()

        try {
            advertiser.startAdvertising(settings, data, advertiseCallback)
            isAdvertising = true
        } catch (e: SecurityException) {
            Log.e("MeshBLEManager", "Permission denied for advertising", e)
        }
    }

    fun stopAdvertising() {
        Log.d("MeshBLEManager", "Stopping advertising")
        if (!isAdvertising) return
        try {
            bluetoothAdapter.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
        } catch (e: SecurityException) {
            Log.e("MeshBLEManager", "Permission denied for stopping advertising", e)
        }
    }

    fun startScanning() {
        Log.d("MeshBLEManager", "Requesting to start scanning. isScanning: $isScanning")
        if (isScanning) return

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e("MeshBLEManager", "BluetoothLeScanner is null!")
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(MeshConstants.SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()

        try {
            scanner.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
        } catch (e: SecurityException) {
            Log.e("MeshBLEManager", "Permission denied for scanning", e)
        }
    }

    fun stopScanning() {
        Log.d("MeshBLEManager", "Stopping scanning")
        if (!isScanning) return
        try {
            bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
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
