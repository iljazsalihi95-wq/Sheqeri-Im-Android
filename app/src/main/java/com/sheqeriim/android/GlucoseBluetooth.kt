package com.sheqeriim.android

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import java.util.Calendar
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
        val started = try { device.createBond() } catch (e: SecurityException) {
            onStatus("Android nuk lejoi çiftimin: ${e.message}")
            false
        }
        if (!started) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            bondReceiver = null
            pairingReceiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
            pairingReceiver = null
        }
        onStatus(if (started) "Ciftimi u nis. Prit dialogun Android per kodin 6-shifror." else "Android nuk e nisi ciftimin; kontrollo Wireless ne glukometer.")
    }

    @Suppress("MissingPermission")
    fun pairedDevices(): List<Pair<String, String>> {
        if (!hasPermission()) return emptyList()
        return adapter?.bondedDevices?.map { (it.name ?: "Bluetooth") to it.address } ?: emptyList()
    }

    private val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private var activeMeterId: String? = null
    private var onMeasurement: ((Int, Long, String, Int) -> Unit)? = null
    private var syncStep = 0

    /** Subscribe to glucose measurements, then request all stored records via RACP. */
    @Suppress("MissingPermission")
    fun connectPaired(
        address: String,
        onStatus: (String) -> Unit,
        onReading: (Int, Long, String, Int) -> Unit = { _, _, _, _ -> }
    ) {
        if (!hasPermission()) { onStatus("Leja Bluetooth mungon"); return }
        val device = adapter?.bondedDevices?.firstOrNull { it.address == address }
        if (device == null) { onStatus("Çifto glukometrin në Android së pari"); return }
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        activeMeterId = address
        onMeasurement = onReading
        syncStep = 0
        onStatus("Duke u lidhur me Accu-Chek Guide…")
        gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onStatus("Lidhja BLE dështoi: GATT $status")
                    g.close()
                    if (gatt === g) gatt = null
                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    onStatus("Lidhur. Po kërkoj shërbimin e glukozës…")
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    onStatus("Glukometri u shkëput")
                    g.close()
                    if (gatt === g) gatt = null
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val service = if (status == BluetoothGatt.GATT_SUCCESS) g.getService(GLUCOSE_SERVICE) else null
                val measurement = service?.getCharacteristic(GLUCOSE_MEASUREMENT)
                val racp = service?.getCharacteristic(RECORD_ACCESS_CONTROL_POINT)
                if (measurement == null || racp == null) {
                    onStatus("Shërbimi i glukozës ose RACP mungon në këtë lidhje")
                    return
                }
                syncStep = 1
                subscribe(g, measurement, onStatus)
            }

            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onStatus("Aktivizimi i njoftimeve BLE dështoi: $status")
                    return
                }
                val service = g.getService(GLUCOSE_SERVICE) ?: return
                if (syncStep == 1) {
                    syncStep = 2
                    val racp = service.getCharacteristic(RECORD_ACCESS_CONTROL_POINT) ?: return
                    subscribe(g, racp, onStatus)
                } else if (syncStep == 2) {
                    syncStep = 3
                    val racp = service.getCharacteristic(RECORD_ACCESS_CONTROL_POINT) ?: return
                    // Report Stored Records / All Records: opcode=1, operator=1.
                    racp.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    racp.value = byteArrayOf(0x01, 0x01)
                    if (g.writeCharacteristic(racp)) onStatus("Po kërkoj matjet e ruajtura…")
                    else onStatus("Glukometri nuk pranoi kërkesën RACP")
                }
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                handleCharacteristic(characteristic.uuid, characteristic.value ?: return, onStatus)
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                handleCharacteristic(characteristic.uuid, value, onStatus)
            }
        }, BluetoothDevice.TRANSPORT_LE)
    }

    @Suppress("MissingPermission")
    private fun subscribe(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, onStatus: (String) -> Unit) {
        val descriptor = characteristic.getDescriptor(cccd)
        if (descriptor == null || !g.setCharacteristicNotification(characteristic, true)) {
            onStatus("Nuk mund të aktivizohen njoftimet BLE")
            return
        }
        descriptor.value = if (characteristic.uuid == RECORD_ACCESS_CONTROL_POINT)
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (!g.writeDescriptor(descriptor)) onStatus("Shkrimi i konfigurimit BLE dështoi")
    }

    private fun handleCharacteristic(uuid: UUID, bytes: ByteArray, onStatus: (String) -> Unit) {
        if (uuid == RECORD_ACCESS_CONTROL_POINT) {
            if (bytes.size >= 4 && (bytes[0].toInt() and 255) == 6) {
                val result = bytes[3].toInt() and 255
                onStatus(if (result == 1) "Transferimi i matjeve përfundoi" else "RACP përfundoi me kod $result")
            }
            return
        }
        if (uuid != GLUCOSE_MEASUREMENT || bytes.size < 10) return
        val flags = bytes[0].toInt() and 255
        val seq = (bytes[1].toInt() and 255) or ((bytes[2].toInt() and 255) shl 8)
        fun u(i: Int) = bytes[i].toInt() and 255
        val year = u(3) or (u(4) shl 8)
        if (year !in 2000..2100) return
        val calendar = Calendar.getInstance().apply {
            isLenient = false
            set(year, u(5) - 1, u(6), u(7), u(8), u(9))
            set(Calendar.MILLISECOND, 0)
        }
        var offset = 10
        try {
            if ((flags and 0x01) != 0) {
                if (bytes.size < offset + 2) return
                val minutes = (u(offset) or (u(offset + 1) shl 8)).toShort().toInt()
                calendar.add(Calendar.MINUTE, minutes)
                offset += 2
            }
            if ((flags and 0x02) == 0 || bytes.size < offset + 3) {
                onStatus("Matja #$seq pa vlerë glukoze; u anashkalua")
                return
            }
            val raw = u(offset) or (u(offset + 1) shl 8)
            var mantissa = raw and 0x0fff
            if ((mantissa and 0x0800) != 0) mantissa -= 0x1000
            var exponent = (raw ushr 12) and 0x0f
            if ((exponent and 0x08) != 0) exponent -= 16
            val concentration = mantissa * Math.pow(10.0, exponent.toDouble())
            // Bluetooth glucose unit bit: 0=kg/L, 1=mol/L.
            val mgDl = if ((flags and 0x04) != 0) concentration * 180155.88 else concentration * 100000.0
            val rounded = kotlin.math.round(mgDl).toInt()
            if (rounded !in 20..600) {
                onStatus("Matja #$seq jashtë intervalit të vlefshëm")
                return
            }
            onMeasurement?.invoke(rounded, calendar.timeInMillis, activeMeterId ?: "", seq)
        } catch (_: Exception) {
            onStatus("Data ose formati i matjes #$seq nuk u njoh")
        }
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
