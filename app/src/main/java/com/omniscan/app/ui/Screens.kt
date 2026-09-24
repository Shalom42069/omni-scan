package com.omniscan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniscan.app.model.NfcTag
import com.omniscan.app.scan.NfcReader
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
private fun ScanHeader(
    label: String,
    count: Int,
    scanning: Boolean,
    buttonText: String,
    onScan: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(1.dp))
            Text("   "); CountPill(count)
        }
        Button(onClick = onScan) {
            if (scanning) {
                CircularProgressIndicator(
                    modifier = Modifier.height(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colors.onPrimary
                )
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null)
            }
            Spacer(Modifier.height(1.dp)); Text("  $buttonText")
        }
    }
}

/* ----------------------------- WLAN ----------------------------- */
@Composable
fun WifiScreen(vm: ScanViewModel) {
    val aps by vm.wifi.results.collectAsState()
    val scanning by vm.wifi.scanning.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScanHeader("WLAN", aps.size, scanning, "Scan") { vm.wifi.triggerScan() }
        if (aps.isEmpty()) EmptyHint("Noch keine Netze. Tippe auf \"Scan\". " +
            "Hinweis: Android drosselt WLAN-Scans — bei häufigem Scannen kommen " +
            "gecachte Ergebnisse.")
        LazyColumn(Modifier.fillMaxSize()) {
            items(aps) { ap ->
                SectionCard(title = ap.ssid, subtitle = ap.bssid) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        SignalBadge(ap.rssi)
                        Text("${ap.band} · Kanal ${ap.channel}",
                            style = MaterialTheme.typography.caption,
                            fontFamily = FontFamily.Monospace)
                    }
                    InfoRow("Sicherheit", ap.security)
                    InfoRow("Frequenz", "${ap.frequencyMhz} MHz")
                    InfoRow("Capabilities", ap.capabilities)
                }
            }
        }
    }
}

/* --------------------------- Bluetooth -------------------------- */
@Composable
fun BluetoothScreen(vm: ScanViewModel) {
    val devs by vm.bluetooth.results.collectAsState()
    val scanning by vm.bluetooth.scanning.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScanHeader("Bluetooth", devs.size, scanning, "Scan") {
            vm.bluetooth.clear(); vm.bluetooth.start()
        }
        if (!vm.bluetooth.available)
            EmptyHint("Kein Bluetooth-Adapter gefunden.")
        else if (!vm.bluetooth.enabled)
            EmptyHint("Bluetooth ist aus — bitte in den Schnelleinstellungen aktivieren.")
        else if (devs.isEmpty())
            EmptyHint("Noch nichts gefunden. Tippe auf \"Scan\" (Classic-Discovery + BLE laufen parallel).")
        LazyColumn(Modifier.fillMaxSize()) {
            items(devs) { d ->
                SectionCard(title = d.name ?: "(ohne Namen)", subtitle = d.address) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        SignalBadge(d.rssi)
                        Text(d.type, style = MaterialTheme.typography.caption,
                            fontFamily = FontFamily.Monospace)
                    }
                    InfoRow("Bindung", d.bondState)
                    d.manufacturer?.let { InfoRow("Hersteller-Daten", it) }
                    if (d.services.isNotEmpty())
                        InfoRow("Services", d.services.joinToString(", "))
                    InfoRow("Zuletzt", timeFmt.format(Date(d.lastSeen)))
                }
            }
        }
    }
}

/* ------------------------------ NFC ----------------------------- */
@Composable
fun NfcScreen(nfc: NfcReader?) {
    val emptyFlow = remember { MutableStateFlow<List<NfcTag>>(emptyList()) }
    val history by (nfc?.history ?: emptyFlow).collectAsState()
    Column(Modifier.fillMaxSize()) {
        Text("NFC", style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(14.dp))
        when {
            nfc == null || !nfc.available ->
                EmptyHint("Dieses Gerät hat kein NFC.")
            !nfc.enabled ->
                EmptyHint("NFC ist deaktiviert — bitte in den Einstellungen einschalten.")
            history.isEmpty() ->
                EmptyHint("Bereit. Halte einen NFC-Tag (13,56 MHz) an die Rückseite.\n\n" +
                    "Hinweis: 125-kHz-RFID (z. B. viele Zutritts-Fobs) kann das Handy " +
                    "hardwarebedingt nicht lesen.")
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(history) { tag ->
                SectionCard(title = "UID ${tag.uid}",
                    subtitle = timeFmt.format(Date(tag.timestamp))) {
                    InfoRow("Techs", tag.techList.joinToString(", "))
                    tag.maxSizeBytes?.let { InfoRow("Kapazität", "$it Bytes") }
                    tag.writable?.let { InfoRow("Beschreibbar", if (it) "ja" else "nein") }
                    if (tag.ndefRecords.isEmpty())
                        InfoRow("NDEF", "keine Records")
                    else tag.ndefRecords.forEachIndexed { i, r ->
                        InfoRow("Record ${i + 1}", r)
                    }
                }
            }
        }
    }
}

/* --------------------------- Mobilfunk -------------------------- */
@Composable
fun CellScreen(vm: ScanViewModel) {
    val cells by vm.cell.results.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScanHeader("Mobilfunk", cells.size, false, "Aktualisieren") { vm.cell.refresh() }
        if (vm.cell.networkOperator.isNotBlank())
            Text("Netz: ${vm.cell.networkOperator}",
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(horizontal = 14.dp))
        if (cells.isEmpty())
            EmptyHint("Keine Zellinfos. Tippe \"Aktualisieren\". " +
                "Braucht Telefon- + Standort-Freigabe und eine SIM.")
        LazyColumn(Modifier.fillMaxSize()) {
            items(cells) { c ->
                SectionCard(
                    title = c.type + if (c.registered) "  ●" else "",
                    subtitle = if (c.registered) "dienende Zelle" else "Nachbarzelle"
                ) {
                    SignalBadge(c.signalDbm)
                    InfoRow("Identität", c.identity)
                    if (c.extra.isNotBlank()) InfoRow("Details", c.extra)
                }
            }
        }
    }
}

/* --------------------------- GPS/GNSS --------------------------- */
@Composable
fun LocationScreen(vm: ScanViewModel) {
    val loc by vm.location.location.collectAsState()
    val sats by vm.location.satellites.collectAsState()
    val used = sats.count { it.usedInFix }
    Column(Modifier.fillMaxSize()) {
        Text("Standort & Satelliten", style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp))
        SectionCard(title = "Position",
            subtitle = if (vm.location.gpsEnabled) "GPS aktiv" else "GPS aus") {
            if (loc == null) EmptyHint("Warte auf Fix … (freier Himmel hilft)")
            else loc?.let {
                InfoRow("Breite", "%.6f".format(it.lat))
                InfoRow("Länge", "%.6f".format(it.lon))
                InfoRow("Höhe", "%.1f m".format(it.altitudeM))
                InfoRow("Genauigkeit", "±%.1f m".format(it.accuracyM))
                InfoRow("Speed", "%.1f m/s".format(it.speedMs))
                InfoRow("Quelle", it.provider)
            }
        }
        Text("  Satelliten: $used im Fix / ${sats.size} sichtbar",
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(sats) { s ->
                SectionCard(title = "${s.constellation}  SV ${s.svid}",
                    subtitle = if (s.usedInFix) "im Fix verwendet" else "sichtbar") {
                    InfoRow("C/N0", "%.1f dB-Hz".format(s.cn0))
                    InfoRow("Elevation", "%.0f°".format(s.elevationDeg))
                    InfoRow("Azimut", "%.0f°".format(s.azimuthDeg))
                }
            }
        }
    }
}

/* ------------------------------ USB ----------------------------- */
@Composable
fun UsbScreen(vm: ScanViewModel) {
    val devs by vm.usb.results.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScanHeader("USB", devs.size, false, "Aktualisieren") { vm.usb.refresh() }
        if (devs.isEmpty())
            EmptyHint("Keine USB-Geräte. Schließe etwas per USB-OTG an und tippe \"Aktualisieren\".")
        LazyColumn(Modifier.fillMaxSize()) {
            items(devs) { d ->
                SectionCard(title = d.product ?: d.deviceName,
                    subtitle = "VID 0x%04X · PID 0x%04X".format(d.vendorId, d.productId)) {
                    d.manufacturer?.let { InfoRow("Hersteller", it) }
                    InfoRow("Klasse", d.deviceClass)
                    InfoRow("Interfaces", "${d.interfaceCount}")
                    InfoRow("Pfad", d.deviceName)
                }
            }
        }
    }
}

/* ---------------------------- Sensoren -------------------------- */
@Composable
fun SensorScreen(vm: ScanViewModel) {
    val sensors by vm.sensors.results.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScanHeader("Sensoren", sensors.size, false, "Aktualisieren") { vm.sensors.refresh() }
        if (sensors.isEmpty()) EmptyHint("Tippe \"Aktualisieren\".")
        LazyColumn(Modifier.fillMaxSize()) {
            items(sensors) { s ->
                SectionCard(title = s.name, subtitle = s.type) {
                    InfoRow("Hersteller", s.vendor)
                    InfoRow("Messbereich", "%.3f".format(s.maxRange))
                    InfoRow("Auflösung", "%.5f".format(s.resolution))
                    InfoRow("Strom", "%.2f mA".format(s.powerMa))
                    InfoRow("Wake-Up", if (s.wakeUp) "ja" else "nein")
                }
            }
        }
    }
}
