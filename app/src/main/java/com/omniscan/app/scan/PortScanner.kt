package com.omniscan.app.scan

import com.omniscan.app.model.PortResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * TCP-Connect-Port-Scanner (kein SYN-Scan — der braucht Raw-Sockets/root).
 * Läuft mit vielen parallelen Verbindungsversuchen und macht bei erfolgreicher
 * Verbindung einen kurzen Banner-Grab-Versuch.
 */
class PortScanner {

    companion object {
        /** Häufig interessante Ports für einen schnellen Scan. */
        val COMMON_PORTS = listOf(
            21, 22, 23, 25, 53, 80, 110, 111, 135, 139, 143, 443, 445,
            465, 587, 631, 993, 995, 1723, 3306, 3389, 5000, 5432, 5900,
            5985, 6379, 7000, 7001, 8000, 8008, 8080, 8081, 8443, 8888,
            9000, 9090, 9200, 27017
        )

        private val SERVICE_NAMES = mapOf(
            21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP",
            53 to "DNS", 80 to "HTTP", 110 to "POP3", 111 to "RPCbind",
            135 to "MSRPC", 139 to "NetBIOS", 143 to "IMAP", 443 to "HTTPS",
            445 to "SMB", 465 to "SMTPS", 587 to "SMTP-Submission",
            631 to "IPP/CUPS", 993 to "IMAPS", 995 to "POP3S",
            1723 to "PPTP", 3306 to "MySQL", 3389 to "RDP", 5432 to "PostgreSQL",
            5900 to "VNC", 5985 to "WinRM", 6379 to "Redis", 8080 to "HTTP-Alt",
            8443 to "HTTPS-Alt", 9200 to "Elasticsearch", 27017 to "MongoDB"
        )

        fun serviceNameFor(port: Int): String = SERVICE_NAMES[port] ?: "unbekannt"
    }

    private val _results = MutableStateFlow<List<PortResult>>(emptyList())
    val results: StateFlow<List<PortResult>> = _results.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _progress = MutableStateFlow(0f) // 0..1
    val progress: StateFlow<Float> = _progress.asStateFlow()

    /**
     * Scannt [host] auf [ports]. [concurrency] parallele Verbindungsversuche,
     * [timeoutMs] pro Verbindungsversuch.
     */
    suspend fun scan(
        host: String,
        ports: List<Int>,
        timeoutMs: Int = 500,
        concurrency: Int = 48
    ) {
        _scanning.value = true
        _progress.value = 0f
        _results.value = emptyList()
        val found = java.util.Collections.synchronizedList(mutableListOf<PortResult>())
        val done = AtomicInteger(0)
        val total = ports.size

        withContext(Dispatchers.IO) {
            ports.chunked(maxOf(1, ports.size / concurrency + 1)).map { chunk ->
                async {
                    for (port in chunk) {
                        val start = System.currentTimeMillis()
                        val open = runCatching {
                            Socket().use { s ->
                                s.connect(InetSocketAddress(host, port), timeoutMs)
                                true
                            }
                        }.getOrDefault(false)
                        val latency = System.currentTimeMillis() - start

                        if (open) {
                            val banner = grabBanner(host, port, timeoutMs)
                            found.add(
                                PortResult(
                                    port = port,
                                    service = serviceNameFor(port),
                                    banner = banner,
                                    latencyMs = latency
                                )
                            )
                            _results.value = found.sortedBy { it.port }.toList()
                        }
                        _progress.value = done.incrementAndGet().toFloat() / total
                    }
                }
            }.awaitAll()
        }
        _scanning.value = false
        _progress.value = 1f
    }

    /** Kurzer Versuch, eine Banner-Zeile vom offenen Port zu lesen. */
    private fun grabBanner(host: String, port: Int, timeoutMs: Int): String? = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), timeoutMs)
            s.soTimeout = timeoutMs

            // Bei HTTP(S)-artigen Ports erst eine simple Anfrage senden,
            // sonst passiv auf eine Server-Banner warten (SSH/FTP/SMTP).
            if (port == 80 || port == 8080 || port == 8000 || port == 8888) {
                s.getOutputStream().write("HEAD / HTTP/1.0\r\nHost: $host\r\n\r\n".toByteArray())
                s.getOutputStream().flush()
            }
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val line = reader.readLine()
            line?.take(200)
        }
    }.getOrNull()
}
