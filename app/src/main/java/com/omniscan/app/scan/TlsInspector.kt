package com.omniscan.app.scan

import com.omniscan.app.model.TlsCertInfo
import com.omniscan.app.model.TlsInspectionResult
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Verbindet sich per TLS zu host:port und liest die vom Server präsentierte
 * Zertifikatskette aus — reines Client-Handshake, kein MITM, keine Root-Rechte
 * nötig. Nützlich um schnell zu prüfen, ob ein Zertifikat abgelaufen ist,
 * welche Signatur-Algorithmen verwendet werden, oder den Fingerprint gegen
 * einen bekannten Wert (Cert-Pinning-Check) zu vergleichen.
 */
object TlsInspector {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    fun inspect(host: String, port: Int, timeoutMs: Int = 8000): TlsInspectionResult {
        var socket: SSLSocket? = null
        return try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            socket = factory.createSocket() as SSLSocket
            socket.soTimeout = timeoutMs
            socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
            socket.startHandshake()

            val session = socket.session
            val chain = session.peerCertificates
                .filterIsInstance<X509Certificate>()
                .map { it.toInfo() }

            TlsInspectionResult(
                host = host,
                port = port,
                protocol = session.protocol,
                cipherSuite = session.cipherSuite,
                chain = chain
            )
        } catch (e: Exception) {
            TlsInspectionResult(
                host = host,
                port = port,
                protocol = null,
                cipherSuite = null,
                chain = emptyList(),
                error = e.message ?: e.javaClass.simpleName
            )
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun X509Certificate.toInfo(): TlsCertInfo {
        val fingerprint = runCatching {
            MessageDigest.getInstance("SHA-256").digest(encoded)
                .joinToString(":") { "%02X".format(it) }
        }.getOrDefault("?")
        val now = Date()
        return TlsCertInfo(
            subject = subjectX500Principal?.name ?: "?",
            issuer = issuerX500Principal?.name ?: "?",
            notBefore = dateFmt.format(notBefore),
            notAfter = dateFmt.format(notAfter),
            serial = serialNumber?.toString(16) ?: "?",
            sigAlgorithm = sigAlgName ?: "?",
            sha256Fingerprint = fingerprint,
            isExpired = now.after(notAfter) || now.before(notBefore)
        )
    }
}
