package com.omniscan.app.model

/** Ein gefundener WLAN-Access-Point. */
data class WifiAp(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val band: String,
    val security: String,
    val capabilities: String
)

/** Ein Bluetooth-Gerät (Classic oder BLE). */
data class BtDevice(
    val address: String,
    val name: String?,
    val rssi: Int?,
    val type: String,          // CLASSIC / BLE / DUAL / UNKNOWN
    val bondState: String,
    val manufacturer: String?, // aus BLE-Advertising, hex
    val services: List<String>,
    val lastSeen: Long
)

/** Ein per NFC gelesener Tag. */
data class NfcTag(
    val uid: String,
    val techList: List<String>,
    val ndefRecords: List<String>,
    val maxSizeBytes: Int?,
    val writable: Boolean?,
    val timestamp: Long
)

/** Eine Mobilfunkzelle. */
data class CellRecord(
    val type: String,          // LTE / NR / GSM / WCDMA / CDMA
    val registered: Boolean,
    val identity: String,      // z.B. "MCC=262 MNC=02 TAC=1234 CI=567890"
    val signalDbm: Int?,
    val extra: String
)

/** Standort + GNSS. */
data class LocationInfo(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val altitudeM: Double,
    val speedMs: Float,
    val provider: String,
    val timestamp: Long
)

data class Satellite(
    val svid: Int,
    val constellation: String,
    val cn0: Float,            // Signal-Rausch-Verhältnis dB-Hz
    val usedInFix: Boolean,
    val elevationDeg: Float,
    val azimuthDeg: Float
)

/** Ein angeschlossenes USB-Gerät. */
data class UsbDeviceInfo(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturer: String?,
    val product: String?,
    val deviceClass: String,
    val interfaceCount: Int
)

/** Ein im Gerät verbauter Sensor. */
data class SensorInfo(
    val name: String,
    val vendor: String,
    val type: String,
    val maxRange: Float,
    val resolution: Float,
    val powerMa: Float,
    val wakeUp: Boolean
)
