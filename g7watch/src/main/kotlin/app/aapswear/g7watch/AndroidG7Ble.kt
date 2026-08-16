package app.aapswear.g7watch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import app.aapswear.g7.G7GattClient
import app.aapswear.g7.G7GattProfile
import app.aapswear.g7.G7ProtocolResearchRequired
import app.aapswear.g7.G7Scanner
import app.aapswear.g7.G7Sensor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun interface G7DeviceMatcher {
    fun matches(device: BluetoothDevice, knownSensor: G7Sensor?): Boolean
}

internal class KnownG7DeviceMatcher : G7DeviceMatcher {
    @SuppressLint("MissingPermission")
    override fun matches(device: BluetoothDevice, knownSensor: G7Sensor?): Boolean {
        // TODO(G7-PROTOCOL): initial-setup advertising/name matching needs hardware validation.
        return knownSensor?.deviceAddress?.equals(device.address, ignoreCase = true) == true
    }
}

internal class AndroidG7Scanner(
    private val context: Context,
    private val matcher: G7DeviceMatcher = KnownG7DeviceMatcher(),
) : G7Scanner {
    @SuppressLint("MissingPermission")
    override suspend fun findKnownSensor(sensor: G7Sensor?, timeoutMs: Long): G7Sensor? {
        require(context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) { "BLUETOOTH_SCAN permission missing" }
        if (sensor?.deviceAddress == null) throw G7ProtocolResearchRequired("TODO(G7-PROTOCOL): initial G7 device matching is not validated")
        val scanner = context.getSystemService(BluetoothManager::class.java).adapter?.bluetoothLeScanner ?: return null
        return suspendCancellableCoroutine { continuation ->
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (!matcher.matches(result.device, sensor) || !continuation.isActive) return
                    scanner.stopScan(this)
                    continuation.resume(sensor.copy(deviceAddress = result.device.address, deviceName = result.device.name ?: sensor.deviceName))
                }
                override fun onScanFailed(errorCode: Int) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("BLE scan failed: $errorCode"))
                }
            }
            scanner.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(G7GattProfile.researchServiceUuid)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(),
                callback,
            )
            val timeout = android.os.Handler(context.mainLooper).postDelayed({
                scanner.stopScan(callback)
                if (continuation.isActive) continuation.resume(null)
            }, timeoutMs.coerceIn(1_000L, 30_000L))
            continuation.invokeOnCancellation { scanner.stopScan(callback) }
        }
    }
}

internal class AndroidG7GattClient(private val context: Context) : G7GattClient {
    private var gatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    override suspend fun connect(sensor: G7Sensor) {
        require(context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) { "BLUETOOTH_CONNECT permission missing" }
        val address = sensor.deviceAddress ?: throw IllegalArgumentException("Known G7 address required")
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter ?: throw IllegalStateException("Bluetooth unavailable")
        val device = adapter.getRemoteDevice(address)
        suspendCancellableCoroutine<Unit> { continuation ->
            gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (!continuation.isActive) return
                    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) continuation.resume(Unit)
                    else continuation.resumeWithException(IllegalStateException("GATT connection failed: $status/$newState"))
                }
            }, BluetoothDevice.TRANSPORT_LE)
            continuation.invokeOnCancellation { runCatching { gatt?.close() } }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun discoverServices() {
        val current = gatt ?: throw IllegalStateException("GATT not connected")
        if (!current.discoverServices()) throw IllegalStateException("GATT service discovery could not start")
        // TODO(G7-PROTOCOL): characteristic UUIDs and required ordering must be hardware validated.
    }

    override suspend fun enableNotifications() {
        throw G7ProtocolResearchRequired("TODO(G7-PROTOCOL): G7 notification characteristics are not validated")
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        gatt?.disconnect(); gatt?.close(); gatt = null
    }
}
