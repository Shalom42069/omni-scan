package com.omniscan.app.vpn

import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Vereinfachte User-Space-TCP-Session (kein root, kein Kernel-NAT nötig).
 * Übernimmt den 3-Way-Handshake gegenüber dem Gerät, pumpt Nutzdaten über
 * einen echten (protected) Socket zum realen Ziel und umgekehrt.
 *
 * Bewusste Vereinfachung: keine eigenen Retransmission-Timer / kein
 * Congestion-Window — für typische, verlustarme Mobil-/WLAN-Verbindungen
 * ausreichend, aber kein RFC-vollständiger TCP-Stack (siehe README).
 */
class TcpSession(
    val key: String,
    private val clientIp: ByteArray,
    private val clientPort: Int,
    private val destIp: ByteArray,
    private val destPort: Int,
    private val tunOut: FileOutputStream,
    private val protect: (java.net.Socket) -> Boolean,
    private val onData: (out: Int, inn: Int) -> Unit,
    private val onClosed: (String) -> Unit
) {
    enum class State { SYN_RCVD, ESTABLISHED, CLOSING, CLOSED }

    @Volatile var state = State.SYN_RCVD
    @Volatile var lastActive = System.currentTimeMillis()

    // Sequenznummern aus UNSERER Sicht (dem simulierten Server)
    private val ourSeq = AtomicLong(Random.nextInt().toLong() and 0xFFFFFFFFL)
    // Nächstes erwartetes Byte vom Client
    private var clientSeq: Long = 0L

    private var socket: Socket? = null
    private var connectThread: Thread? = null
    private var readerThread: Thread? = null

    fun beginHandshake(clientInitialSeq: Long) {
        clientSeq = (clientInitialSeq + 1) and 0xFFFFFFFFL
        connectThread = Thread {
            try {
                val s = Socket()
                protect(s)
                s.connect(InetSocketAddress(InetAddress.getByAddress(destIp), destPort), 8000)
                socket = s
                // SYN-ACK ans Gerät
                writeToTun(PacketUtil.buildTcpPacket(
                    destIp, destPort, clientIp, clientPort,
                    seq = ourSeq.get(), ack = clientSeq,
                    flags = PacketUtil.TCP_SYN or PacketUtil.TCP_ACK
                ))
                ourSeq.incrementAndGet()
                state = State.ESTABLISHED
                startReader()
            } catch (_: Exception) {
                sendRst()
                close()
            }
        }.apply { isDaemon = true; start() }
    }

    /** Nutzdaten- oder Kontroll-Paket vom Gerät (bereits als ACK vom Client, ggf. mit Payload). */
    fun onClientSegment(seq: Long, flags: Int, payload: ByteArray) {
        lastActive = System.currentTimeMillis()
        if (flags and PacketUtil.TCP_RST != 0) {
            close(); return
        }
        if (payload.isNotEmpty()) {
            runCatching {
                socket?.outputStream?.write(payload)
                socket?.outputStream?.flush()
            }
            clientSeq = (clientSeq + payload.size) and 0xFFFFFFFFL
            onData(payload.size, 0)
            // ACK für die empfangenen Daten
            writeToTun(PacketUtil.buildTcpPacket(
                destIp, destPort, clientIp, clientPort,
                seq = ourSeq.get(), ack = clientSeq, flags = PacketUtil.TCP_ACK
            ))
        }
        if (flags and PacketUtil.TCP_FIN != 0) {
            clientSeq = (clientSeq + 1) and 0xFFFFFFFFL
            state = State.CLOSING
            writeToTun(PacketUtil.buildTcpPacket(
                destIp, destPort, clientIp, clientPort,
                seq = ourSeq.get(), ack = clientSeq,
                flags = PacketUtil.TCP_FIN or PacketUtil.TCP_ACK
            ))
            ourSeq.incrementAndGet()
            close()
        }
    }

    private fun startReader() {
        readerThread = Thread {
            val buf = ByteArray(16384)
            try {
                val input = socket?.inputStream ?: return@Thread
                while (state == State.ESTABLISHED) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n == 0) continue
                    val chunk = buf.copyOf(n)
                    writeToTun(PacketUtil.buildTcpPacket(
                        destIp, destPort, clientIp, clientPort,
                        seq = ourSeq.get(), ack = clientSeq,
                        flags = PacketUtil.TCP_ACK or PacketUtil.TCP_PSH,
                        payload = chunk
                    ))
                    ourSeq.addAndGet(n.toLong())
                    onData(0, n)
                }
                // Remote hat geschlossen -> FIN ans Gerät
                writeToTun(PacketUtil.buildTcpPacket(
                    destIp, destPort, clientIp, clientPort,
                    seq = ourSeq.get(), ack = clientSeq,
                    flags = PacketUtil.TCP_FIN or PacketUtil.TCP_ACK
                ))
                ourSeq.incrementAndGet()
            } catch (_: Exception) {
                // Verbindung abgebrochen
            } finally {
                close()
            }
        }.apply { isDaemon = true; start() }
    }

    private fun sendRst() {
        runCatching {
            writeToTun(PacketUtil.buildTcpPacket(
                destIp, destPort, clientIp, clientPort,
                seq = ourSeq.get(), ack = clientSeq, flags = PacketUtil.TCP_RST
            ))
        }
    }

    private fun writeToTun(buf: java.nio.ByteBuffer) {
        synchronized(tunOut) {
            runCatching { tunOut.write(buf.array(), 0, buf.limit()) }
        }
    }

    fun close() {
        if (state == State.CLOSED) return
        state = State.CLOSED
        runCatching { socket?.close() }
        onClosed(key)
    }

    companion object {
        val sessions = ConcurrentHashMap<String, TcpSession>()
    }
}
