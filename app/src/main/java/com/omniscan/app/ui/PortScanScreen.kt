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
import androidx.compose.material.Button
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniscan.app.scan.PortScanner

/**
 * Port-Scanner-Screen: TCP-Connect-Scan gegen eine IP/Hostname im
 * gleichen Netz. Kein SYN-Scan (braucht Raw-Sockets/root) — dafür
 * funktioniert das hier ohne jede Sonderrechte.
 */
@Composable
fun PortScanScreen(vm: ScanViewModel) {
    var host by remember { mutableStateOf("192.168.1.1") }
    var portsText by remember { mutableStateOf("häufige Ports") }
    var customPorts by remember { mutableStateOf("1-1024") }
    val results by vm.portScanner.results.collectAsState()
    val scanning by vm.portScanner.scanning.collectAsState()
    val progress by vm.portScanner.progress.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Text(
            "Port-Scanner",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(14.dp, 14.dp, 14.dp, 4.dp)
        )
        Text(
            "TCP-Connect-Scan (kein SYN-Scan/Root nötig) — nur im eigenen Netz einsetzen.",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.mutedText,
            modifier = Modifier.padding(horizontal = 14.dp)
        )

        Column(Modifier.padding(14.dp)) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Ziel (IP oder Hostname)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = customPorts,
                onValueChange = { customPorts = it },
                label = { Text("Ports (z.B. 1-1024 oder 22,80,443)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val ports = parsePorts(customPorts).ifEmpty { PortScanner.COMMON_PORTS }
                        vm.startPortScan(host.trim(), ports)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (scanning) "Scan läuft…" else "Scan starten")
                }
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { vm.startPortScan(host.trim(), PortScanner.COMMON_PORTS) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Nur häufige Ports (${PortScanner.COMMON_PORTS.size})")
            }
        }

        if (scanning) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            )
            Spacer(Modifier.height(6.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Offene Ports",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.SemiBold
            )
            CountPill(results.size)
        }

        if (results.isEmpty() && !scanning) {
            EmptyHint("Noch kein Scan gelaufen, oder keine offenen Ports gefunden.")
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(results) { r ->
                SectionCard(
                    title = "Port ${r.port} — ${r.service}",
                    subtitle = "${r.latencyMs} ms"
                ) {
                    if (r.banner != null) {
                        InfoRow("Banner", r.banner)
                    } else {
                        InfoRow("Banner", "keine Antwort gelesen")
                    }
                }
            }
        }
    }
}

/** Parst "22,80,443" oder "1-1024" oder eine Mischung "22,80,1000-1010". */
private fun parsePorts(text: String): List<Int> {
    val result = mutableSetOf<Int>()
    text.split(",").forEach { part ->
        val trimmed = part.trim()
        if (trimmed.isEmpty()) return@forEach
        if (trimmed.contains("-")) {
            val (a, b) = trimmed.split("-", limit = 2)
            val start = a.trim().toIntOrNull()
            val end = b.trim().toIntOrNull()
            if (start != null && end != null && start <= end) {
                for (p in start..minOf(end, start + 4000)) result.add(p)
            }
        } else {
            trimmed.toIntOrNull()?.let { result.add(it) }
        }
    }
    return result.filter { it in 1..65535 }.sorted()
}
