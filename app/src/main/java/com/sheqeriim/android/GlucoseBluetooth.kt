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
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
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
    private var bondReceiver: BroadcastReceiver? = null
    private var pairingReceiver: BroadcastReceiver? = null

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

    @Suppress("MissingPermission")
    fun startPairingScan(onDevice: (String, String) -> Unit, onError: (String) -> Unit) {
        if (!hasPermission()) { onError("Leja Bluetooth mungon"); return }
        val scanner = adapter?.bluetoothLeScanner ?: run { onError("Aktivizo Bluetooth"); return }
        stopScan()
        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name ?: "BLE device"
                onDevice(name, result.device.address)
            }
            override fun onScanFailed(errorCode: Int) { onError("BLE scan error: $errorCode") }
        }
        scanner.startScan(callback)
    }

    @Suppress("MissingPermission")
    fun pairWithPin(address: String, pin: String, onStatus: (String) -> Unit) {
        if (!hasPermission()) { onStatus("Leja Bluetooth mungon"); return }
        if (pin.length != 6 || pin.any { !it.isDigit() }) {
            onStatus("Shkruaj kodin e sakte 6-shifror nga glukometri")
            return
        }
        val device = try { adapter?.getRemoteDevice(address) } catch (_: Exception) { null }
        if (device == null) { onStatus("Glukometri nuk u gjet"); return }
        stopScan()
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            onStatus("Glukometri eshte tashme i ciftuar. Perdore butonin Lidhu.")
            return
        }
        pairingReceiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_PAIRING_REQUEST) return
                val target = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (target?.address != address) return
                val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
                if (variant == BluetoothDevice.PAIRING_VARIANT_PIN) {
                    val accepted = target.setPin(pin.toByteArray(Charsets.UTF_8))
                    onStatus(if (accepted) "PIN iu dorezua Android-it; prit konfirmimin." else "Android nuk e pranoi PIN-in. Perdore dialogun e sistemit.")
                } else {
                    // A six-digit BLE passkey uses Android's secure system dialog.
                    // The public Bluetooth API cannot programmatically enter a passkey.
                    onStatus("Shkruaj kodin 6-shifror ne dritaren Bluetooth te Android-it. Kodi ne aplikacion nuk e zevendeson ate.")
                }
            }
        }
        pairingReceiver = receiver
        context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST))
        requestBond(address, onStatus)
    }

    @Suppress("MissingPermission")
    fun requestBond(address: String, onStatus: (String) -> Unit) {
        if (!hasPermission()) { onStatus("Leja Bluetooth mungon"); return }
        val device = try { adapter?.getRemoteDevice(address) } catch (_: IllegalArgumentException) { null }
        if (device == null) { onStatus("Adresa e pajisjes nuk eshte e vlefshme"); return }
        stopScan()
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            onStatus("Android raporton ciftim ekzistues per kete adrese. Rifresko listen.")
            return
        }
        bondReceiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                val changed = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (changed?.address != address) return
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                    BluetoothDevice.BOND_BONDING -> onStatus("Ciftimi ne proces. Kontrollo dialogun e Android per PIN.")
                    BluetoothDevice.BOND_BONDED -> {
                        onStatus("Ciftimi u konfirmua nga Android. Rifresko Pajisjet e ciftuara.")
                        try { context.unregisterReceiver(this) } catch (_: Exception) {}
                        bondReceiver = null
                        pairingReceiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
                        pairingReceiver = null
                    }
                    BluetoothDevice.BOND_NONE -> {
                        onStatus("Ciftimi deshtoi ose u anulua. Provo serish me kodin e ri te glukometrit.")
                        try { context.unregisterReceiver(this) } catch (_: Exception) {}
                        bondReceiver = null
                        pairingReceiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
                        pairingReceiver = null
                    }
                }
            }
        }
        bondReceiver = receiver
        context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        val started = device.createBond()
        onStatus(if (started) "Ciftimi u nis. Prit dialogun Android per kodin 6-shifror." else "Android nuk e nisi ciftimin; kontrollo Wireless ne glukometer.")
    }

    @Suppress("MissingPermission")
    fun pairedDevices(): List<Pair<String, String>> {
        if (!hasPermission()) return emptyList()
        return adapter?.bondedDevices?.map { (it.name ?: "Bluetooth") to it.address } ?: emptyList()
    }

    @Suppress("MissingPermission")
    fun connectPaired(address: String, onStatus: (String) -> Unit) {
        if (!hasPermission()) { onStatus("Bluetooth permission required"); return }
        val device = adapter?.bondedDevices?.firstOrNull { it.address == address }
        if (device == null) { onStatus("Pair the meter in Android Bluetooth settings first"); return }
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        onStatus("Po lidhem me glukometrin. Mbaje ndezur dhe mbylle mySugr gjate proves.")
        gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onStatus("Lidhja BLE deshtoi (GATT $status). Sigurohu qe mySugr nuk po sinkronizon dhe glukometri eshte ndezur. Provo serish.")
                    g.close()
                    if (gatt === g) gatt = null
                } else if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                    onStatus("Connected to paired meter; discovering glucose service…")
                    if (hasPermission()) {
                        @Suppress("MissingPermission")
                        g.discoverServices()
                    }
                } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                    onStatus("Glukometri u shkeput")
                    g.close()
                    if (gatt === g) gatt = null
                }
            }
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val service = if (status == BluetoothGatt.GATT_SUCCESS) g.getService(GLUCOSE_SERVICE) else null
                onStatus(if (status == BluetoothGatt.GATT_SUCCESS && service != null)
                    "Glucose Service 1808 found. Reading transfer requires RACP support."
                    else "Glucose Service not available on this connection")
            }
        }, BluetoothDevice.TRANSPORT_LE)
    }

    fun close() {
        bondReceiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        bondReceiver = null
        pairingReceiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        pairingReceiver = null
        stopScan()
        gatt?.close()
        gatt = null
    }
}
