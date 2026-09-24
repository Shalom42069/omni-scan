package com.omniscan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omniscan.app.model.GattServiceInfo

/**
 * BLE-GATT-Explorer: Verbindung zu einem BLE-Gerät per MAC-Adresse,
 * Auslesen aller Services/Characteristics, lesen/schreiben wo erlaubt.
 * Adressen findet man im "Bluetooth"-Tab (dort antippen zum Kopieren
 * ist nicht implementiert — die Adresse einfach von dort abschreiben).
 */
@Composable
fun GattExplorerScreen(vm: ScanViewModel) {
    val explorer = vm.gattExplorer
    val state by explorer.connectionState.collectAsState()
    val services by explorer.services.collectAsState()
    val log by explorer.log.collectAsState()

    var address by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("BLE-GATT-Explorer", style = MaterialTheme.typography.h6)
        Text(
            "Status: $state",
            style = MaterialTheme.typography.caption,
            color = mutedText()
        )

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("MAC-Adresse (z. B. AA:BB:CC:DD:EE:FF)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (address.isNotBlank()) explorer.connect(address.trim()) }) {
                Text("Verbinden")
            }
            OutlinedButton(onClick = { explorer.disconnect() }) {
                Text("Trennen")
            }
        }

        if (services.isEmpty()) {
            EmptyHint("Noch keine Services. Verbinde dich mit einem BLE-Gerät — " +
                "funktioniert nur, wenn das Gerät keine Kopplung/Authentifizierung verlangt.")
        } else {
            SectionCard(title = "Services & Characteristics (${services.size})") {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(services) { svc ->
                        GattServiceRow(svc, onRead = { charUuid ->
                            explorer.readCharacteristic(svc.uuid, charUuid)
                        }, onNotify = { charUuid ->
                            explorer.enableNotify(svc.uuid, charUuid)
                        }, onWrite = { charUuid, hex ->
                            explorer.writeCharacteristicHex(svc.uuid, charUuid, hex)
                        })
                    }
                }
            }
        }

        if (log.isNotEmpty()) {
            SectionCard(title = "Log") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    log.takeLast(15).forEach { line ->
                        Text(line, style = MaterialTheme.typography.caption)
                    }
                }
            }
        }
    }
}

@Composable
private fun GattServiceRow(
    svc: GattServiceInfo,
    onRead: (String) -> Unit,
    onNotify: (String) -> Unit,
    onWrite: (String, String) -> Unit
) {
    Column {
        Text("Service ${svc.uuid}", style = MaterialTheme.typography.subtitle1)
        svc.characteristics.forEach { ch ->
            var writeHex by remember { mutableStateOf("") }
            Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                Text(
                    "  ${ch.uuid}  [${ch.properties.joinToString(", ")}]",
                    style = MaterialTheme.typography.body2
                )
                if (ch.lastValueHex != null) {
                    Text(
                        "  Wert: ${ch.lastValueHex}",
                        style = MaterialTheme.typography.caption,
                        color = mutedText()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ch.properties.contains("READ")) {
                        OutlinedButton(onClick = { onRead(ch.uuid) }) { Text("Lesen") }
                    }
                    if (ch.properties.contains("NOTIFY") || ch.properties.contains("INDICATE")) {
                        OutlinedButton(onClick = { onNotify(ch.uuid) }) { Text("Notify") }
                    }
                }
                if (ch.properties.contains("WRITE") || ch.properties.contains("WRITE_NR")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = writeHex,
                            onValueChange = { writeHex = it },
                            label = { Text("Hex (z. B. 01 FF)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = { onWrite(ch.uuid, writeHex) }) { Text("Senden") }
                    }
                }
            }
        }
    }
}

@Composable
private fun mutedText() = MaterialTheme.mutedText
