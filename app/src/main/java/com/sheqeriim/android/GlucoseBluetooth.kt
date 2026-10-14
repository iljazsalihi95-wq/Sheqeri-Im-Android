package com.sheqeriim.android

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.UUID

/**
 * Initial BLE discovery layer. This is NOT yet a verified Accu-Chek Guide integration.
 * A paired device and Glucose Service (0x1808) do not guarantee interoperability.
 * Production sync requires testing characteristic notifications, RACP and measurement parsing.
 */
class GlucoseBluetooth(private val context: Context) {
    companion object {
        val GLUCOSE_SERVICE: UUID = UUID.fromString("00001808-0000-1000-8000-00805f9b34fb")
        val GLUCOSE_MEASUREMENT: UUID = UUID.fromString("00002a18-0000-1000-8000-00805f9b34fb")
        val RECORD_ACCESS_CONTROL_POINT: UUID = UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb")
    }
    private val manager get() = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = manager?.adapter
    private var callback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
        (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
         context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)

    @Suppress("MissingPermission")
    fun pairedDeviceNames(): List<String> {
        if (!hasPermission()) return emptyList()
        return adapter?.bondedDevices?.map { it.name ?: "Unknown BLE device" } ?: emptyList()
    }

    @Suppress("MissingPermission")
    fun startScan(onDevice: (String) -> Unit, onError: (String) -> Unit) {
        if (!hasPermission()) { onError("Bluetooth permission required"); return }
        val scanner = adapter?.bluetoothLeScanner ?: run { onError("Enable Bluetooth"); return }
        stopScan()
        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onDevice(result.device.name ?: result.device.address)
            }
            override fun onScanFailed(errorCode: Int) { onError("BLE scan failed: $errorCode") }
        }
        scanner.startScan(callback)
    }

    @Suppress("MissingPermission")
    fun stopScan() {
        if (!hasPermission()) return
        callback?.let { adapter?.bluetoothLeScanner?.stopScan(it) }
        callback = null
    }

    fun close() {
        stopScan()
        gatt?.close()
        gatt = null
    }
}
