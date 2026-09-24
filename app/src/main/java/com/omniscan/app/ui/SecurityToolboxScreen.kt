package com.omniscan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omniscan.app.scan.SecurityTools

@Composable
fun SecurityToolboxScreen(vm: ScanViewModel) {
    var sub by remember { mutableIntStateOf(0) }
    val subTabs = listOf("Hash/Encoding", "JWT", "TLS-Check", "App-Audit")

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = sub) {
            subTabs.forEachIndexed { i, title ->
                Tab(selected = sub == i, onClick = { sub = i }, text = { Text(title) })
            }
        }
        when (sub) {
            0 -> HashEncodingPanel()
            1 -> JwtPanel()
            2 -> TlsPanel(vm)
            3 -> AppAuditPanel(vm)
        }
    }
}

@Composable
private fun HashEncodingPanel() {
    var input by remember { mutableStateOf("") }
    val hashes = remember(input) { if (input.isNotBlank()) SecurityTools.hashAll(input) else emptyList() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Text eingeben") },
            modifier = Modifier.fillMaxWidth()
        )
        if (hashes.isNotEmpty()) {
            SectionCard(title = "Hashes") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    hashes.forEach { h ->
                        Text("${h.algorithm}: ${h.hex}", style = MaterialTheme.typography.body2)
                    }
                }
            }
        }
        Divider()
        Text("Base64", style = MaterialTheme.typography.subtitle1)
        Text("Encode: ${if (input.isNotBlank()) SecurityTools.base64Encode(input) else ""}",
            style = MaterialTheme.typography.caption)
        Text("Decode (Eingabe als Base64 interpretiert): ${if (input.isNotBlank()) SecurityTools.base64Decode(input) else ""}",
            style = MaterialTheme.typography.caption)
        Divider()
        Text("Hex", style = MaterialTheme.typography.subtitle1)
        Text("Encode: ${if (input.isNotBlank()) SecurityTools.hexEncode(input) else ""}",
            style = MaterialTheme.typography.caption)
        Divider()
        Text("URL-Encoding", style = MaterialTheme.typography.subtitle1)
        Text("Encode: ${if (input.isNotBlank()) SecurityTools.urlEncode(input) else ""}",
            style = MaterialTheme.typography.caption)
    }
}

@Composable
private fun JwtPanel() {
    var token by remember { mutableStateOf("") }
    val decoded = remember(token) { if (token.isNotBlank()) SecurityTools.decodeJwt(token) else null }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("JWT einfügen (header.payload.signature)") },
            modifier = Modifier.fillMaxWidth()
        )
        if (decoded == null) {
            if (token.isNotBlank()) EmptyHint("Kein gültiges JWT-Format erkannt.")
        } else {
            SectionCard(title = "Header (Alg: ${decoded.algorithm ?: "?"})") {
                Text(decoded.headerJson, style = MaterialTheme.typography.body2)
            }
            SectionCard(title = "Payload") {
                Text(decoded.payloadJson, style = MaterialTheme.typography.body2)
            }
            if (decoded.signatureHex != null) {
                SectionCard(title = "Signatur (Hex, unverifiziert)") {
                    Text(decoded.signatureHex, style = MaterialTheme.typography.caption)
                }
            }
            EmptyHint("Hinweis: Die Signatur wird nur angezeigt, nicht verifiziert — dafür bräuchte man Secret/Public Key.")
        }
    }
}

@Composable
private fun TlsPanel(vm: ScanViewModel) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    val loading by vm.tlsLoading.collectAsState()
    val result by vm.tlsResult.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = host, onValueChange = { host = it },
            label = { Text("Host (z. B. example.com)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = port, onValueChange = { port = it },
            label = { Text("Port") }, singleLine = true
        )
        Button(onClick = {
            val p = port.toIntOrNull() ?: 443
            if (host.isNotBlank()) vm.inspectTls(host.trim(), p)
        }) { Text("Zertifikat prüfen") }

        if (loading) CircularProgressIndicator()

        result?.let { r ->
            if (r.error != null) {
                SectionCard(title = "Fehler") { Text(r.error, style = MaterialTheme.typography.body2) }
            } else {
                SectionCard(title = "Verbindung: ${r.protocol} / ${r.cipherSuite}") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        r.chain.forEachIndexed { i, cert ->
                            Column {
                                Text("Zertifikat #${i + 1}${if (cert.isExpired) "  ⚠ ABGELAUFEN" else ""}",
                                    style = MaterialTheme.typography.subtitle1)
                                Text("Subject: ${cert.subject}", style = MaterialTheme.typography.caption)
                                Text("Issuer: ${cert.issuer}", style = MaterialTheme.typography.caption)
                                Text("Gültig: ${cert.notBefore} – ${cert.notAfter}", style = MaterialTheme.typography.caption)
                                Text("Sig-Alg: ${cert.sigAlgorithm}", style = MaterialTheme.typography.caption)
                                Text("SHA-256: ${cert.sha256Fingerprint}", style = MaterialTheme.typography.caption)
                            }
                            if (i < r.chain.lastIndex) Divider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppAuditPanel(vm: ScanViewModel) {
    val loading by vm.auditLoading.collectAsState()
    val apps by vm.appAudit.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.runAppAudit(includeSystemApps = false) }) { Text("Nutzer-Apps prüfen") }
            OutlinedButton(onClick = { vm.runAppAudit(includeSystemApps = true) }) { Text("+ System-Apps") }
        }
        if (loading) CircularProgressIndicator()

        if (apps.isEmpty() && !loading) {
            EmptyHint("Noch nicht geprüft. Tippe auf \"Nutzer-Apps prüfen\".")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps) { app ->
                    SectionCard(title = "${app.appName}${if (app.isSystemApp) " (System)" else ""}") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(app.packageName, style = MaterialTheme.typography.caption, color = MaterialTheme.mutedText)
                            if (app.grantedDangerous.isNotEmpty()) {
                                Text("Gewährt: ${app.grantedDangerous.joinToString(", ")}",
                                    style = MaterialTheme.typography.body2)
                            }
                            if (app.deniedDangerous.isNotEmpty()) {
                                Text("Verweigert: ${app.deniedDangerous.joinToString(", ")}",
                                    style = MaterialTheme.typography.caption, color = MaterialTheme.mutedText)
                            }
                        }
                    }
                }
            }
        }
    }
}
