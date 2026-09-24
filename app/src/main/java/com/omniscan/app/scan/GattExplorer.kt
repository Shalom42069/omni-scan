package com.omniscan.app.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.omniscan.app.model.GattCharInfo
import com.omniscan.app.model.GattServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Verbindet sich zu einem BLE-Gerät und liest dessen komplette GATT-Struktur
 * (Services + Characteristics) aus. Rein clientseitig — funktioniert bei jedem
 * Gerät, das keine Authentifizierung fürs Verbinden verlangt (viele billige
 * IoT-Geräte, Sensoren, Tracker etc.), ganz ohne root.
 */
@SuppressLint("MissingPermission")
class GattExplorer(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    private var gatt: BluetoothGatt? = null

    private val _connectionState = MutableStateFlow("getrennt")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    private val _services = MutableStateFlow<List<GattServiceInfo>>(emptyList())
    val services: StateFlow<List<GattServiceInfo>> = _services.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private fun addLog(line: String) {
        _log.value = (_log.value + line).takeLast(100)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = "verbunden"
                    addLog("Verbunden — starte Service-Discovery…")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = "getrennt"
                    addLog("Verbindung getrennt (status=$status)")
                }
                else -> _connectionState.value = "wechsle… ($newState)"
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val list = g.services.map { svc -> svc.toInfo() }
            _services.value = list
            addLog("Discovery fertig: ${list.size} Services, " +
                "${list.sumOf { it.characteristics.size }} Characteristics")
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val hex = characteristic.value?.toHex() ?: "(leer)"
            addLog("Read ${characteristic.uuid}: $hex")
            updateCharValue(characteristic.uuid.toString(), hex)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            addLog("Write ${characteristic.uuid}: status=$status")
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val hex = characteristic.value?.toHex() ?: "(leer)"
            addLog("Notify ${characteristic.uuid}: $hex")
            updateCharValue(characteristic.uuid.toString(), hex)
        }
    }

    private fun updateCharValue(uuid: String, hex: String) {
        _services.value = _services.value.map { svc ->
            svc.copy(characteristics = svc.characteristics.map { c ->
                if (c.uuid == uuid) c.copy(lastValueHex = hex) else c
            })
        }
    }

    fun connect(address: String) {
        disconnect()
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            addLog("Ungültige Adresse: $address")
            return
        }
        _connectionState.value = "verbinde…"
        gatt = device.connectGatt(context, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
        addLog("Verbindungsversuch zu $address …")
    }

    fun disconnect() {
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
        _services.value = emptyList()
        _connectionState.value = "getrennt"
    }

    @Suppress("DEPRECATION")
    fun readCharacteristic(serviceUuid: String, charUuid: String) {
        val g = gatt ?: return
        val svc = g.getService(java.util.UUID.fromString(serviceUuid)) ?: return
        val ch = svc.getCharacteristic(java.util.UUID.fromString(charUuid)) ?: return
        g.readCharacteristic(ch)
    }

    @Suppress("DEPRECATION")
    fun writeCharacteristicHex(serviceUuid: String, charUuid: String, hex: String) {
        val g = gatt ?: return
        val svc = g.getService(java.util.UUID.fromString(serviceUuid)) ?: return
        val ch = svc.getCharacteristic(java.util.UUID.fromString(charUuid)) ?: return
        val bytes = hexToBytes(hex) ?: run {
            addLog("Ungültiger Hex-String: $hex")
            return
        }
        ch.value = bytes
        val ok = g.writeCharacteristic(ch)
        addLog("Write angestoßen (${bytes.size} Bytes): $ok")
    }

    fun enableNotify(serviceUuid: String, charUuid: String) {
        val g = gatt ?: return
        val svc = g.getService(java.util.UUID.fromString(serviceUuid)) ?: return
        val ch = svc.getCharacteristic(java.util.UUID.fromString(charUuid)) ?: return
        g.setCharacteristicNotification(ch, true)
        // Client Characteristic Configuration Descriptor (Standard-UUID)
        val cccd = ch.getDescriptor(
            java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        )
        if (cccd != null) {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptorEnableNotification
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
        addLog("Notify aktiviert für $charUuid")
    }

    private fun BluetoothGattService.toInfo(): GattServiceInfo = GattServiceInfo(
        uuid = uuid.toString(),
        characteristics = characteristics.map { c ->
            GattCharInfo(
                uuid = c.uuid.toString(),
                properties = propsOf(c.properties)
            )
        }
    )

    private fun propsOf(props: Int): List<String> {
        val list = mutableListOf<String>()
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) list += "READ"
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) list += "WRITE"
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) list += "WRITE_NR"
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) list += "NOTIFY"
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) list += "INDICATE"
        return list
    }

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

    private fun hexToBytes(hex: String): ByteArray? {
        val clean = hex.replace(" ", "").replace("0x", "", ignoreCase = true)
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        return runCatching {
            ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    companion object {
        private val BluetoothGattDescriptorEnableNotification =
            byteArrayOf(0x01, 0x00)
    }
}
