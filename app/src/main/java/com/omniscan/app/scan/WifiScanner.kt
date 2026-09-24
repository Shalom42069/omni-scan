package com.omniscan.app.scan

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.omniscan.app.model.WifiAp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Scannt umliegende WLAN-Access-Points.
 *
 * Hinweis Android-Throttling: Ab Android 9 ist startScan() gedrosselt
 * (Foreground: wenige Scans / 2 min). Die App löst Scans daher nur auf
 * Nutzeraktion aus und zeigt zusätzlich immer die zuletzt gecachten Ergebnisse.
 */
class WifiScanner(private val context: Context) {

    private val wifi = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _results = MutableStateFlow<List<WifiAp>>(emptyList())
    val results: StateFlow<List<WifiAp>> = _results.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            _scanning.value = false
            publishCached()
        }
    }

    fun start() {
        if (!registered) {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            registered = true
        }
        publishCached()  // sofort das anzeigen, was das System gecacht hat
        triggerScan()
    }

    fun stop() {
        if (registered) {
            runCatching { context.unregisterReceiver(receiver) }
            registered = false
        }
    }

    @Suppress("DEPRECATION")
    fun triggerScan() {
        _scanning.value = true
        val ok = runCatching { wifi.startScan() }.getOrDefault(false)
        // Auch bei gedrosseltem/abgelehntem Scan die gecachten Ergebnisse zeigen
        publishCached()
        if (!ok) _scanning.value = false
    }

    @SuppressLint("MissingPermission")
    private fun publishCached() {
        val list = runCatching { wifi.scanResults }.getOrDefault(emptyList())
        _results.value = list.map { it.toAp() }
            .sortedByDescending { it.rssi }
    }

    @Suppress("DEPRECATION")
    private fun ScanResult.toAp(): WifiAp {
        val ssidRaw = SSID ?: ""
        val ssid = if (ssidRaw.isBlank()) "<versteckt>" else ssidRaw
        val ch = channelForFrequency(frequency)
        return WifiAp(
            ssid = ssid,
            bssid = BSSID ?: "",
            rssi = level,
            frequencyMhz = frequency,
            channel = ch,
            band = bandForFrequency(frequency),
            security = securityOf(capabilities ?: ""),
            capabilities = capabilities ?: ""
        )
    }

    private fun securityOf(caps: String): String = when {
        caps.contains("WPA3") || caps.contains("SAE") -> "WPA3"
        caps.contains("WPA2") || caps.contains("RSN") -> "WPA2"
        caps.contains("WPA") -> "WPA"
        caps.contains("WEP") -> "WEP"
        else -> "Offen"
    }

    private fun bandForFrequency(freq: Int): String = when {
        freq in 2400..2500 -> "2.4 GHz"
        freq in 4900..5900 -> "5 GHz"
        freq in 5925..7125 -> "6 GHz"
        freq >= 58000 -> "60 GHz"
        else -> "?"
    }

    private fun channelForFrequency(freq: Int): Int = when {
        freq == 2484 -> 14
        freq in 2412..2472 -> (freq - 2412) / 5 + 1
        freq in 5160..5885 -> (freq - 5000) / 5
        freq in 5955..7115 -> (freq - 5950) / 5
        else -> -1
    }
}
