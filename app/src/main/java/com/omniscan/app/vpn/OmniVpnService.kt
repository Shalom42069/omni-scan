package com.omniscan.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.Socket
import java.nio.ByteBuffer

/**
 * Root-freier Per-App-Traffic-Monitor über die offizielle VpnService-API.
 *
 * Funktionsweise: Diese App wird als lokales "VPN" registriert (Android
 * routet dann den gesamten Gerätetraffic über ein TUN-Interface an uns).
 * Wir lesen jedes IP-Paket, ordnen es per ConnectivityManager.getConnectionOwnerUid()
 * einer App/UID zu, protokollieren es — und leiten es per echtem (protected)
 * Socket zum tatsächlichen Ziel weiter (User-Space-NAT), damit das Gerät
 * währenddessen normal online bleibt. TCP ist dabei bewusst vereinfacht
 * (siehe TcpSession) — kein root, keine Kernel-Änderungen nötig.
 */
class OmniVpnService : VpnService() {

    private var tunInterface: ParcelFileDescriptor? = null
    private var running = false
    private var loopThread: Thread? = null

    companion object {
        const val ACTION_STOP = "com.omniscan.app.vpn.STOP"
        private const val CHANNEL_ID = "omniscan_vpn"
        private const val NOTIF_ID = 42
        private const val CLIENT_IP = "10.0.0.2"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (running) return
        val builder = Builder()
            .setSession("OmniScan Monitor")
            .addAddress(CLIENT_IP, 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .setMtu(1500)
        tunInterface = builder.establish() ?: return
        running = true
        VpnMonitor.setRunning(true)
        showForegroundNotification()

        loopThread = Thread { runLoop() }.apply { isDaemon = true; start() }
    }

    private fun stopVpn() {
        running = false
        VpnMonitor.setRunning(false)
        UdpSession.sessions.values.forEach { it.close() }
        UdpSession.sessions.clear()
        TcpSession.sessions.values.forEach { it.close() }
        TcpSession.sessions.clear()
        runCatching { tunInterface?.close() }
        tunInterface = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    private fun runLoop() {
        val fd = tunInterface ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)

        while (running) {
            val len = try { input.read(buf) } catch (_: Exception) { break }
            if (len <= 0) continue
            val packet = ByteBuffer.wrap(buf, 0, len)
            try {
                handlePacket(packet, len, output)
            } catch (_: Exception) {
                // einzelnes fehlerhaftes Paket überspringen, Loop weiterlaufen lassen
            }
        }
    }

    private fun handlePacket(buf: ByteBuffer, len: Int, output: FileOutputStream) {
        if (PacketUtil.ipv4Version(buf) != 4) return // IPv6 wird in dieser Version nicht behandelt
        val ipHeaderLen = PacketUtil.ihl(buf)
        val proto = PacketUtil.protocol(buf)
        val srcIp = PacketUtil.srcIp(buf)
        val dstIp = PacketUtil.dstIp(buf)
        val srcIpStr = PacketUtil.ipToString(srcIp)
        val dstIpStr = PacketUtil.ipToString(dstIp)

        when (proto) {
            PacketUtil.PROTO_UDP -> {
                val srcPort = PacketUtil.srcPort(buf, ipHeaderLen)
                val dstPort = PacketUtil.dstPort(buf, ipHeaderLen)
                val payloadOffset = ipHeaderLen + 8
                val payload = ByteArray(len - payloadOffset)
                for (i in payload.indices) payload[i] = buf.get(payloadOffset + i)

                val key = "UDP:$srcPort:$dstIpStr:$dstPort"
                val session = UdpSession.sessions.getOrPut(key) {
                    val socket = DatagramSocket()
                    protect(socket)
                    UdpSession(
                        key, srcIp, srcPort, dstIp, dstPort, socket, output,
                        onData = { out, inn ->
                            VpnMonitor.recordConnection(
                                this, "UDP", srcPort, dstIpStr, dstPort, srcIpStr, out, inn
                            )
                        },
                        onClosed = { UdpSession.sessions.remove(it) }
                    )
                }
                session.send(payload)
            }
            PacketUtil.PROTO_TCP -> {
                val srcPort = PacketUtil.srcPort(buf, ipHeaderLen)
                val dstPort = PacketUtil.dstPort(buf, ipHeaderLen)
                val flags = PacketUtil.tcpFlags(buf, ipHeaderLen)
                val seq = PacketUtil.tcpSeq(buf, ipHeaderLen)
                val dataOffset = PacketUtil.tcpDataOffset(buf, ipHeaderLen)
                val payloadOffset = ipHeaderLen + dataOffset
                val payloadLen = (len - payloadOffset).coerceAtLeast(0)
                val payload = ByteArray(payloadLen)
                for (i in payload.indices) payload[i] = buf.get(payloadOffset + i)

                val key = "TCP:$srcPort:$dstIpStr:$dstPort"
                if (flags and PacketUtil.TCP_SYN != 0 && flags and PacketUtil.TCP_ACK == 0) {
                    // Neue Verbindung
                    TcpSession.sessions[key]?.close()
                    val session = TcpSession(
                        key, srcIp, srcPort, dstIp, dstPort, output,
                        protect = { sock: Socket -> protect(sock) },
                        onData = { out, inn ->
                            VpnMonitor.recordConnection(
                                this, "TCP", srcPort, dstIpStr, dstPort, srcIpStr, out, inn
                            )
                        },
                        onClosed = { TcpSession.sessions.remove(it) }
                    )
                    TcpSession.sessions[key] = session
                    VpnMonitor.recordConnection(this, "TCP", srcPort, dstIpStr, dstPort, srcIpStr, 0, 0)
                    session.beginHandshake(seq)
                } else {
                    TcpSession.sessions[key]?.onClientSegment(seq, flags, payload)
                }
            }
            else -> { /* ICMP etc. — nicht weitergeleitet, nur ignoriert */ }
        }
    }

    private fun showForegroundNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "OmniScan VPN-Monitor", NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        val stopIntent = Intent(this, OmniVpnService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("OmniScan Traffic-Monitor aktiv")
            .setContentText("Protokolliert Verbindungen pro App. Tippen zum Beenden.")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentIntent(stopPending)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }
}
