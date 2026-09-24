package com.omniscan.app.vpn

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimaler IPv4/TCP/UDP-Header-Parser + -Builder für die userspace-NAT im VpnService. */
object PacketUtil {
    const val PROTO_TCP = 6
    const val PROTO_UDP = 17

    fun ipv4Version(buf: ByteBuffer): Int = (buf.get(0).toInt() and 0xF0) ushr 4
    fun ihl(buf: ByteBuffer): Int = (buf.get(0).toInt() and 0x0F) * 4
    fun protocol(buf: ByteBuffer): Int = buf.get(9).toInt() and 0xFF
    fun totalLength(buf: ByteBuffer): Int = buf.getShort(2).toUShortInt()

    fun srcIp(buf: ByteBuffer): ByteArray = ByteArray(4) { buf.get(12 + it) }
    fun dstIp(buf: ByteBuffer): ByteArray = ByteArray(4) { buf.get(16 + it) }

    fun srcPort(buf: ByteBuffer, ipHeaderLen: Int): Int = buf.getShort(ipHeaderLen).toUShortInt()
    fun dstPort(buf: ByteBuffer, ipHeaderLen: Int): Int = buf.getShort(ipHeaderLen + 2).toUShortInt()

    fun tcpSeq(buf: ByteBuffer, ipHeaderLen: Int): Long = buf.getInt(ipHeaderLen + 4).toUIntLong()
    fun tcpAck(buf: ByteBuffer, ipHeaderLen: Int): Long = buf.getInt(ipHeaderLen + 8).toUIntLong()
    fun tcpDataOffset(buf: ByteBuffer, ipHeaderLen: Int): Int =
        ((buf.get(ipHeaderLen + 12).toInt() and 0xF0) ushr 4) * 4
    fun tcpFlags(buf: ByteBuffer, ipHeaderLen: Int): Int = buf.get(ipHeaderLen + 13).toInt() and 0xFF

    const val TCP_FIN = 0x01
    const val TCP_SYN = 0x02
    const val TCP_RST = 0x04
    const val TCP_PSH = 0x08
    const val TCP_ACK = 0x10

    fun ipToString(ip: ByteArray): String = ip.joinToString(".") { (it.toInt() and 0xFF).toString() }

    private fun Short.toUShortInt(): Int = toInt() and 0xFFFF
    private fun Int.toUIntLong(): Long = toLong() and 0xFFFFFFFFL

    /** Baut ein UDP-Antwortpaket (vom "Server" zurück zum Client-Gerät). */
    fun buildUdpPacket(
        srcIp: ByteArray, srcPort: Int, dstIp: ByteArray, dstPort: Int, payload: ByteArray
    ): ByteBuffer {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val total = ipHeaderLen + udpHeaderLen + payload.size
        val buf = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)

        // IPv4-Header
        buf.put(0, (0x45).toByte())               // version=4, IHL=5
        buf.put(1, 0)                              // ToS
        buf.putShort(2, total.toShort())
        buf.putShort(4, 0)                          // ID
        buf.putShort(6, 0)                          // flags/fragment
        buf.put(8, 64)                              // TTL
        buf.put(9, PROTO_UDP.toByte())
        buf.putShort(10, 0)                         // checksum (unten berechnet)
        for (i in 0..3) buf.put(12 + i, srcIp[i])
        for (i in 0..3) buf.put(16 + i, dstIp[i])
        val ipChecksum = checksum(buf, 0, ipHeaderLen)
        buf.putShort(10, ipChecksum.toShort())

        // UDP-Header
        buf.putShort(ipHeaderLen, srcPort.toShort())
        buf.putShort(ipHeaderLen + 2, dstPort.toShort())
        buf.putShort(ipHeaderLen + 4, (udpHeaderLen + payload.size).toShort())
        buf.putShort(ipHeaderLen + 6, 0) // Checksum optional bei IPv4, wir lassen 0
        for (i in payload.indices) buf.put(ipHeaderLen + udpHeaderLen + i, payload[i])

        buf.rewind()
        return buf
    }

    /** Baut ein TCP-Kontrollpaket (SYN-ACK, ACK, FIN-ACK, RST) ohne Payload oder mit Payload. */
    fun buildTcpPacket(
        srcIp: ByteArray, srcPort: Int, dstIp: ByteArray, dstPort: Int,
        seq: Long, ack: Long, flags: Int, payload: ByteArray = ByteArray(0), window: Int = 65535
    ): ByteBuffer {
        val ipHeaderLen = 20
        val tcpHeaderLen = 20
        val total = ipHeaderLen + tcpHeaderLen + payload.size
        val buf = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)

        buf.put(0, (0x45).toByte())
        buf.put(1, 0)
        buf.putShort(2, total.toShort())
        buf.putShort(4, 0)
        buf.putShort(6, 0x4000.toShort()) // DF-Flag
        buf.put(8, 64)
        buf.put(9, PROTO_TCP.toByte())
        buf.putShort(10, 0)
        for (i in 0..3) buf.put(12 + i, srcIp[i])
        for (i in 0..3) buf.put(16 + i, dstIp[i])
        val ipChecksum = checksum(buf, 0, ipHeaderLen)
        buf.putShort(10, ipChecksum.toShort())

        buf.putShort(ipHeaderLen, srcPort.toShort())
        buf.putShort(ipHeaderLen + 2, dstPort.toShort())
        buf.putInt(ipHeaderLen + 4, seq.toInt())
        buf.putInt(ipHeaderLen + 8, ack.toInt())
        buf.put(ipHeaderLen + 12, ((tcpHeaderLen / 4) shl 4).toByte())
        buf.put(ipHeaderLen + 13, flags.toByte())
        buf.putShort(ipHeaderLen + 14, window.toShort())
        buf.putShort(ipHeaderLen + 16, 0) // checksum
        buf.putShort(ipHeaderLen + 18, 0) // urgent ptr
        for (i in payload.indices) buf.put(ipHeaderLen + tcpHeaderLen + i, payload[i])

        val tcpChecksum = tcpUdpChecksum(
            srcIp, dstIp, PROTO_TCP, buf, ipHeaderLen, tcpHeaderLen + payload.size
        )
        buf.putShort(ipHeaderLen + 16, tcpChecksum.toShort())

        buf.rewind()
        return buf
    }

    private fun checksum(buf: ByteBuffer, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            sum += ((buf.get(i).toInt() and 0xFF) shl 8) or (buf.get(i + 1).toInt() and 0xFF)
            i += 2
        }
        if (length % 2 == 1) {
            sum += (buf.get(offset + length - 1).toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun tcpUdpChecksum(
        srcIp: ByteArray, dstIp: ByteArray, protocol: Int, buf: ByteBuffer, offset: Int, length: Int
    ): Int {
        var sum = 0L
        for (i in 0..2 step 2) sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
        for (i in 0..2 step 2) sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
        sum += protocol
        sum += length
        var i = offset
        while (i < offset + length - 1) {
            sum += ((buf.get(i).toInt() and 0xFF) shl 8) or (buf.get(i + 1).toInt() and 0xFF)
            i += 2
        }
        if (length % 2 == 1) {
            sum += (buf.get(offset + length - 1).toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }
}
