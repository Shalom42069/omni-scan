package com.omniscan.app.vpn

import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Eine NAT-Session für UDP: leitet Datagramme vom Gerät (via TUN) zum echten
 * Ziel weiter und schreibt Antworten als neu gebaute IP/UDP-Pakete zurück
 * ins TUN. Läuft als eigener Thread pro Flow (typisch DNS, QUIC, Spiele).
 */
class UdpSession(
    val key: String,
    private val clientIp: ByteArray,
    private val clientPort: Int,
    private val destIp: ByteArray,
    private val destPort: Int,
    private val socket: DatagramSocket,
    private val tunOut: FileOutputStream,
    private val onData: (out: Int, inn: Int) -> Unit,
    private val onClosed: (String) -> Unit
) {
    @Volatile var lastActive = System.currentTimeMillis()
    private var readerThread: Thread? = null

    fun send(payload: ByteArray) {
        runCatching {
            socket.send(java.net.DatagramPacket(
                payload, payload.size,
                InetSocketAddress(InetAddress.getByAddress(destIp), destPort)
            ))
            lastActive = System.currentTimeMillis()
            onData(payload.size, 0)
        }
        ensureReaderStarted()
    }

    private fun ensureReaderStarted() {
        if (readerThread != null) return
        readerThread = Thread {
            val buf = ByteArray(65535)
            try {
                while (true) {
                    val packet = java.net.DatagramPacket(buf, buf.size)
                    socket.receive(packet) // blockiert, bis Antwort da ist oder Socket schließt
                    lastActive = System.currentTimeMillis()
                    val payload = buf.copyOf(packet.length)
                    val response = PacketUtil.buildUdpPacket(
                        srcIp = destIp, srcPort = destPort,
                        dstIp = clientIp, dstPort = clientPort,
                        payload = payload
                    )
                    synchronized(tunOut) {
                        tunOut.write(response.array(), 0, response.limit())
                    }
                    onData(0, payload.size)
                }
            } catch (_: Exception) {
                // Socket geschlossen oder Timeout — Session beenden
            } finally {
                onClosed(key)
            }
        }.apply { isDaemon = true; start() }
    }

    fun close() {
        runCatching { socket.close() }
    }

    companion object {
        val sessions = ConcurrentHashMap<String, UdpSession>()
    }
}
