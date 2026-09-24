package com.omniscan.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omniscan.app.model.VpnConnectionInfo
import com.omniscan.app.vpn.OmniVpnService
import com.omniscan.app.vpn.VpnMonitor

/**
 * VPN-Traffic-Monitor: zeigt live, welche App zu welcher IP:Port verbindet.
 * Start/Stop wird von der Activity über den VpnService.prepare()-Flow
 * angestoßen (siehe MainActivity) — hier nur die Anzeige + der Stop-Button.
 */
@Composable
fun VpnMonitorScreen(onRequestStart: () -> Unit) {
    val context = LocalContext.current
    val running by VpnMonitor.running.collectAsState()
    val connections by VpnMonitor.connections.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("VPN-Traffic-Monitor", style = MaterialTheme.typography.h6)
        Text(
            "Zeigt pro Verbindung, welche App wohin verbindet — läuft als lokales " +
                "VPN (TUN), ohne root. TCP wird vereinfacht weitergeleitet (siehe README).",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.mutedText
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!running) {
                Button(onClick = onRequestStart) { Text("Monitor starten") }
            } else {
                Button(onClick = {
                    context.startService(
                        Intent(context, OmniVpnService::class.java).setAction(OmniVpnService.ACTION_STOP)
                    )
                }) { Text("Monitor stoppen") }
            }
        }

        if (connections.isEmpty()) {
            EmptyHint(
                if (running) "Monitor läuft — warte auf Verbindungen (andere Apps müssen aktiv Traffic senden)."
                else "Monitor gestoppt. Tippe auf \"Monitor starten\" und bestätige den Android-VPN-Dialog."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(connections) { conn -> ConnectionRow(conn) }
            }
        }
    }
}

@Composable
private fun ConnectionRow(conn: VpnConnectionInfo) {
    SectionCard(title = "${conn.appName ?: conn.packageName ?: "UID ${conn.uid}"} — ${conn.protocol}") {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${conn.remoteIp}:${conn.remotePort}  (lokaler Port ${conn.localPort})",
                style = MaterialTheme.typography.body2)
            Text("↑ ${conn.bytesOut} B   ↓ ${conn.bytesIn} B",
                style = MaterialTheme.typography.caption, color = MaterialTheme.mutedText)
            if (conn.packageName != null) {
                Text(conn.packageName, style = MaterialTheme.typography.caption, color = MaterialTheme.mutedText)
            }
        }
    }
}
