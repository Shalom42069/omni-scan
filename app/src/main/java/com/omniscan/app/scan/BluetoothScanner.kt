package com.omniscan.app.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.omniscan.app.model.BtDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kombinierter Bluetooth-Scanner: klassische Geräte-Discovery + BLE-Advertising.
 * Ergebnisse werden nach MAC-Adresse zusammengeführt.
 */
@SuppressLint("MissingPermission")
class BluetoothScanner(private val context: Context) {

    private val manager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private val adapter: BluetoothAdapter? = manager?.adapter

    private val devices = LinkedHashMap<String, BtDevice>()
    private val _results = MutableStateFlow<List<BtDevice>>(emptyList())
    val results: StateFlow<List<BtDevice>> = _results.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val available: Boolean get() = adapter != null
    val enabled: Boolean get() = adapter?.isEnabled == true

    private var registered = false

    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val dev: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(
                        BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE
                    ).toInt().takeIf { it != Short.MIN_VALUE.toInt() }
                    dev?.let { mergeClassic(it, rssi) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _scanning.value = false
                }
            }
        }
    }

    private val leCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            mergeBle(result)
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { mergeBle(it) }
        }
    }

    fun start() {
        val a = adapter ?: return
        if (!a.isEnabled) return
        if (!registered) {
            val f = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            ContextCompat.registerReceiver(
                context, classicReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED
            )
            registered = true
        }
        _scanning.value = true
        // Klassische Discovery
        runCatching {
            if (a.isDiscovering) a.cancelDiscovery()
            a.startDiscovery()
        }
        // BLE-Scan
        runCatching {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            a.bluetoothLeScanner?.startScan(null, settings, leCallback)
        }
    }

    fun stop() {
        val a = adapter
        runCatching { a?.cancelDiscovery() }
        runCatching { a?.bluetoothLeScanner?.stopScan(leCallback) }
        if (registered) {
            runCatching { context.unregisterReceiver(classicReceiver) }
            registered = false
        }
        _scanning.value = false
    }

    fun clear() {
        devices.clear()
        _results.value = emptyList()
    }

    private fun mergeClassic(dev: BluetoothDevice, rssi: Int?) {
        val addr = dev.address ?: return
        val existing = devices[addr]
        devices[addr] = BtDevice(
            address = addr,
            name = dev.safeName() ?: existing?.name,
            rssi = rssi ?: existing?.rssi,
            type = typeOf(dev.safeType()),
            bondState = bondOf(dev.safeBond()),
            manufacturer = existing?.manufacturer,
            services = existing?.services ?: emptyList(),
            lastSeen = System.currentTimeMillis()
        )
        publish()
    }

    private fun mergeBle(result: ScanResult) {
        val dev = result.device
        val addr = dev.address ?: return
        val record = result.scanRecord
        val mfg = record?.manufacturerSpecificData
        val mfgHex = if (mfg != null && mfg.size() > 0) {
            val id = mfg.keyAt(0)
            val bytes = mfg.valueAt(0)
            "0x%04X:%s".format(id, bytes.toHex())
        } else null
        val services = record?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
        val existing = devices[addr]
        devices[addr] = BtDevice(
            address = addr,
            name = record?.deviceName ?: dev.safeName() ?: existing?.name,
            rssi = result.rssi,
            type = typeOf(dev.safeType()),
            bondState = bondOf(dev.safeBond()),
            manufacturer = mfgHex ?: existing?.manufacturer,
            services = if (services.isNotEmpty()) services else (existing?.services ?: emptyList()),
            lastSeen = System.currentTimeMillis()
        )
        publish()
    }

    private fun publish() {
        _results.value = devices.values
            .sortedByDescending { it.rssi ?: Int.MIN_VALUE }
    }

    private fun typeOf(t: Int): String = when (t) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "CLASSIC"
        BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "DUAL"
        else -> "UNBEKANNT"
    }

    private fun bondOf(b: Int): String = when (b) {
        BluetoothDevice.BOND_BONDED -> "gekoppelt"
        BluetoothDevice.BOND_BONDING -> "koppelt…"
        else -> "nicht gekoppelt"
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02X".format(it) }

    // Zugriffe, die ab Android 12 BLUETOOTH_CONNECT verlangen — abgesichert,
    // falls der Nutzer nur SCAN, nicht CONNECT freigegeben hat.
    private fun BluetoothDevice.safeName(): String? =
        runCatching { name }.getOrNull()
    private fun BluetoothDevice.safeType(): Int =
        runCatching { type }.getOrDefault(BluetoothDevice.DEVICE_TYPE_UNKNOWN)
    private fun BluetoothDevice.safeBond(): Int =
        runCatching { bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
}
