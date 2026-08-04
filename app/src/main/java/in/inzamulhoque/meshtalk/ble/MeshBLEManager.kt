package `in`.inzamulhoque.meshtalk.ble

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import `in`.inzamulhoque.meshtalk.util.PermissionUtils

class MeshBLEManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val myShortId: ByteArray,
    private val onPeerDiscovered: (String, ByteArray, Int) -> Unit, // deviceAddress, peerShortId, rssi
) {
    private var isScanning = false
    private var isAdvertising = false

    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        Log.d("MeshBLEManager", "Requesting to start advertising. isAdvertising: $isAdvertising")
        if (isAdvertising) return
        
        // Permission check
        if (!PermissionUtils.hasPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)) {
            Log.e("MeshBLEManager", "Missing BLUETOOTH_ADVERTISE permission")
            return
        }

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
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(MeshConstants.SERVICE_UUID))
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(ParcelUuid(MeshConstants.SERVICE_UUID), myShortId)
            .build()

        try {
            advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
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

    @SuppressLint("MissingPermission")
    fun startScanning() {
        Log.d("MeshBLEManager", "Requesting to start scanning. isScanning: $isScanning")
        if (isScanning) return

        // Permission check
        if (!PermissionUtils.hasPermission(context, Manifest.permission.BLUETOOTH_SCAN)) {
            Log.e("MeshBLEManager", "Missing BLUETOOTH_SCAN permission")
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e("MeshBLEManager", "BluetoothLeScanner is null!")
            return
        }

        // Relaxing filter: Some devices don't match 128-bit UUIDs in the hardware filter correctly
        val filter = ScanFilter.Builder().build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
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
            val scanRecord = result.scanRecord ?: return
            val serviceUuid = ParcelUuid(MeshConstants.SERVICE_UUID)
            val uuids = scanRecord.serviceUuids ?: emptyList()
            
            if (uuids.contains(serviceUuid)) {
                val shortId = scanRecord.serviceData[serviceUuid] ?: byteArrayOf()
                Log.d("MeshBLEManager", "Mesh peer seen: ${result.device.address}, RSSI: ${result.rssi}")
                onPeerDiscovered(result.device.address, shortId, result.rssi)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("MeshBLEManager", "Scan failed: $errorCode")
            isScanning = false
        }
    }
}
