package com.omniscan.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.omniscan.app.scan.NfcReader
import com.omniscan.app.scan.Permissions
import com.omniscan.app.ui.BluetoothScreen
import com.omniscan.app.ui.CellScreen
import com.omniscan.app.ui.GattExplorerScreen
import com.omniscan.app.ui.LocationScreen
import com.omniscan.app.ui.NfcScreen
import com.omniscan.app.ui.PortScanScreen
import com.omniscan.app.ui.SecurityToolboxScreen
import com.omniscan.app.ui.VpnMonitorScreen
import com.omniscan.app.vpn.OmniVpnService
import com.omniscan.app.ui.OmniScanTheme
import com.omniscan.app.ui.ScanViewModel
import com.omniscan.app.ui.SensorScreen
import com.omniscan.app.ui.UsbScreen
import com.omniscan.app.ui.WifiScreen

class MainActivity : ComponentActivity() {

    private var nfc: NfcReader? = null
    private lateinit var scanViewModel: ScanViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfc = NfcReader(this)
        scanViewModel = ViewModelProvider(this)[ScanViewModel::class.java]
        setContent {
            OmniScanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    AppRoot(nfc, scanViewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfc?.enableReader()
    }

    override fun onPause() {
        super.onPause()
        nfc?.disableReader()
    }
}

private val TABS = listOf(
    "WLAN", "Bluetooth", "NFC", "Mobilfunk", "Standort", "Ports", "GATT", "Security", "VPN", "USB", "Sensoren"
)

@Composable
private fun AppRoot(nfc: NfcReader?, vm: ScanViewModel) {
    val context = LocalContext.current
    var selected by remember { mutableIntStateOf(0) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Ergebnis egal — Screens reagieren selbst auf fehlende Rechte */ }

    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            context.startService(Intent(context, OmniVpnService::class.java))
        }
    }

    LaunchedEffect(Unit) {
        if (!Permissions.allGranted(context)) {
            permLauncher.launch(Permissions.required())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OmniScan") },
                actions = {
                    IconButton(onClick = { permLauncher.launch(Permissions.required()) }) {
                        Icon(Icons.Filled.Lock, contentDescription = "Berechtigungen")
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Column {
                ScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) {
                    TABS.forEachIndexed { i, title ->
                        Tab(
                            selected = selected == i,
                            onClick = { selected = i },
                            text = { Text(title) }
                        )
                    }
                }
                when (selected) {
                    0 -> WifiScreen(vm)
                    1 -> BluetoothScreen(vm)
                    2 -> NfcScreen(nfc)
                    3 -> CellScreen(vm)
                    4 -> LocationScreen(vm)
                    5 -> PortScanScreen(vm)
                    6 -> GattExplorerScreen(vm)
                    7 -> SecurityToolboxScreen(vm)
                    8 -> VpnMonitorScreen(onRequestStart = {
                        val intent = VpnService.prepare(context)
                        if (intent != null) vpnPrepareLauncher.launch(intent)
                        else context.startService(Intent(context, OmniVpnService::class.java))
                    })
                    9 -> UsbScreen(vm)
                    10 -> SensorScreen(vm)
                }
            }
        }
    }
}
