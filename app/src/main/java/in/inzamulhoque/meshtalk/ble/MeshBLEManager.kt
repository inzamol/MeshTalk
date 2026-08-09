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
    private var myShortId: ByteArray,
    private val onPeerDiscovered: (String, ByteArray, Int) -> Unit, // deviceAddress, peerShortId, rssi
) {
    private var isScanning = false
    private var isAdvertising = false
    private var currentScanLowPower: Boolean? = null
    private var currentAdsLowPower: Boolean? = null

    @SuppressLint("MissingPermission")
    fun startAdvertising(lowPower: Boolean = false) {
        if (isAdvertising && currentAdsLowPower == lowPower) return
        Log.d("MeshBLEManager", "Starting advertising. lowPower: $lowPower")
        
        if (isAdvertising) {
            stopAdvertising() 
        }
        
        // ... (permission checks)
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
            .setAdvertiseMode(if (lowPower) AdvertiseSettings.ADVERTISE_MODE_LOW_POWER else AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(if (lowPower) AdvertiseSettings.ADVERTISE_TX_POWER_LOW else AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
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
            currentAdsLowPower = lowPower
        } catch (e: Exception) {
            Log.e("MeshBLEManager", "Failed to start advertising", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        Log.d("MeshBLEManager", "Stopping advertising")
        if (!isAdvertising) return
        try {
            bluetoothAdapter.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.e("MeshBLEManager", "Failed to stop advertising", e)
        }
        isAdvertising = false
    }

    @SuppressLint("MissingPermission")
    fun startScanning(lowPower: Boolean = false) {
        if (isScanning && currentScanLowPower == lowPower) return
        Log.d("MeshBLEManager", "Starting scanning. lowPower: $lowPower")
        
        if (isScanning) {
            stopScanning() 
        }

        // ... (permission checks)
        if (!PermissionUtils.hasPermission(context, Manifest.permission.BLUETOOTH_SCAN)) {
            Log.e("MeshBLEManager", "Missing BLUETOOTH_SCAN permission")
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e("MeshBLEManager", "BluetoothLeScanner is null!")
            return
        }

        // Hardware filtering: Only wake the CPU for MeshTalk service data
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(MeshConstants.SERVICE_UUID))
            .build()

        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(if (lowPower) ScanSettings.SCAN_MODE_LOW_POWER else ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_STICKY)

        // Use hardware batching if available (report results every 5 seconds) to save power
        if (bluetoothAdapter.isOffloadedScanBatchingSupported) {
            settingsBuilder.setReportDelay(5000)
        }

        val settings = settingsBuilder.build()

        try {
            scanner.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            currentScanLowPower = lowPower
        } catch (e: Exception) {
            Log.e("MeshBLEManager", "Failed to start scan", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        Log.d("MeshBLEManager", "Stopping scanning")
        if (!isScanning) return
        try {
            bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e("MeshBLEManager", "Failed to stop scan", e)
        }
        isScanning = false
    }

    fun updateAdvertisingId(newId: ByteArray) {
        myShortId = newId
        if (isAdvertising) {
            // Restart with the current power mode
            startAdvertising(lowPower = currentAdsLowPower ?: true)
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
            processScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            Log.d("MeshBLEManager", "Received ${results.size} batched scan results")
            results.forEach { processScanResult(it) }
        }

        private fun processScanResult(result: ScanResult) {
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
