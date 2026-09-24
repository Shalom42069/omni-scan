package com.omniscan.app.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import com.omniscan.app.model.VpnConnectionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Prozessweiter, geteilter Zustand des VPN-Traffic-Monitors — der Service
 * und die UI (ViewModel/Compose) leben in unterschiedlichen Komponenten,
 * teilen sich aber diese Tabelle der aktuell/zuletzt beobachteten
 * Verbindungen.
 */
object VpnMonitor {
    private val table = ConcurrentHashMap<String, VpnConnectionInfo>()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _connections = MutableStateFlow<List<VpnConnectionInfo>>(emptyList())
    val connections: StateFlow<List<VpnConnectionInfo>> = _connections.asStateFlow()

    fun setRunning(value: Boolean) {
        _running.value = value
        if (!value) {
            table.clear()
            _connections.value = emptyList()
        }
    }

    fun recordConnection(
        context: Context,
        protocol: String,
        localPort: Int,
        remoteIp: String,
        remotePort: Int,
        clientLocalIp: String,
        bytesOutDelta: Int,
        bytesInDelta: Int
    ) {
        val key = "$protocol:$localPort:$remoteIp:$remotePort"
        val existing = table[key]
        if (existing != null) {
            existing.bytesOut += bytesOutDelta
            existing.bytesIn += bytesInDelta
            existing.lastSeen = System.currentTimeMillis()
        } else {
            val (uid, pkg, appName) = resolveOwner(context, protocol, localPort, remoteIp, remotePort, clientLocalIp)
            table[key] = VpnConnectionInfo(
                protocol = protocol,
                localPort = localPort,
                remoteIp = remoteIp,
                remotePort = remotePort,
                appName = appName,
                packageName = pkg,
                uid = uid,
                bytesOut = bytesOutDelta.toLong(),
                bytesIn = bytesInDelta.toLong()
            )
        }
        publish()
    }

    private fun publish() {
        _connections.value = table.values
            .sortedByDescending { it.lastSeen }
            .take(300)
    }

    private data class Owner(val uid: Int, val pkg: String?, val appName: String?)

    private fun resolveOwner(
        context: Context, protocol: String, localPort: Int,
        remoteIp: String, remotePort: Int, clientLocalIp: String
    ): Owner {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Owner(-1, null, null)
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val proto = if (protocol == "TCP") android.system.OsConstants.IPPROTO_TCP
                        else android.system.OsConstants.IPPROTO_UDP
            val local = InetSocketAddress(clientLocalIp, localPort)
            val remote = InetSocketAddress(remoteIp, remotePort)
            val uid = cm.getConnectionOwnerUid(proto, local, remote)
            if (uid < 0) return Owner(-1, null, null)
            val pm = context.packageManager
            val pkgs = pm.getPackagesForUid(uid)
            val pkg = pkgs?.firstOrNull()
            val appName = pkg?.let {
                runCatching {
                    val info = pm.getApplicationInfo(it, 0)
                    pm.getApplicationLabel(info).toString()
                }.getOrNull()
            }
            Owner(uid, pkg, appName)
        }.getOrDefault(Owner(-1, null, null))
    }
}
