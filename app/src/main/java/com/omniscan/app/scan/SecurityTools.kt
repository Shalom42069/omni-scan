package com.omniscan.app.scan

import android.util.Base64
import com.omniscan.app.model.HashResult
import java.security.MessageDigest
import java.util.Locale

/**
 * Sammlung kleiner, rein lokaler Security-Werkzeuge:
 * Hashing, Base64/Hex/URL-Encoding, JWT-Decoding.
 * Alles ohne Netzwerk, ohne root.
 */
object SecurityTools {

    private val HASH_ALGOS = listOf("MD5", "SHA-1", "SHA-256", "SHA-512")

    fun hashAll(input: String): List<HashResult> {
        val bytes = input.toByteArray(Charsets.UTF_8)
        return HASH_ALGOS.map { algo ->
            val digest = MessageDigest.getInstance(algo).digest(bytes)
            HashResult(algorithm = algo, hex = digest.toHex())
        }
    }

    fun base64Encode(input: String): String =
        Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    fun base64Decode(input: String): String = runCatching {
        String(Base64.decode(input, Base64.DEFAULT), Charsets.UTF_8)
    }.getOrElse { "Ungültiges Base64: ${it.message}" }

    fun hexEncode(input: String): String =
        input.toByteArray(Charsets.UTF_8).toHex()

    fun hexDecode(input: String): String = runCatching {
        val clean = input.replace(" ", "").replace("0x", "", ignoreCase = true)
        val bytes = ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        String(bytes, Charsets.UTF_8)
    }.getOrElse { "Ungültiger Hex-String: ${it.message}" }

    fun urlEncode(input: String): String =
        java.net.URLEncoder.encode(input, "UTF-8")

    fun urlDecode(input: String): String = runCatching {
        java.net.URLDecoder.decode(input, "UTF-8")
    }.getOrElse { "Ungültige URL-Kodierung: ${it.message}" }

    /**
     * Dekodiert ein JWT (Header + Payload, ohne Signaturprüfung — dafür wäre
     * der Secret/Public Key nötig). Gibt (header, payload, alg, rawSignatureB64) zurück,
     * oder eine Fehlermeldung.
     */
    data class JwtParts(
        val headerJson: String,
        val payloadJson: String,
        val algorithm: String?,
        val signatureHex: String?
    )

    fun decodeJwt(token: String): JwtParts? {
        val parts = token.trim().split(".")
        if (parts.size < 2) return null
        val header = runCatching {
            String(Base64.decode(parts[0], Base64.URL_SAFE or Base64.NO_PADDING), Charsets.UTF_8)
        }.getOrNull() ?: return null
        val payload = runCatching {
            String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING), Charsets.UTF_8)
        }.getOrNull() ?: return null
        val alg = Regex("\"alg\"\\s*:\\s*\"([^\"]+)\"").find(header)?.groupValues?.get(1)
        val sigHex = if (parts.size >= 3) {
            runCatching {
                Base64.decode(parts[2], Base64.URL_SAFE or Base64.NO_PADDING).toHex()
            }.getOrNull()
        } else null
        return JwtParts(header, payload, alg, sigHex)
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(Locale.ROOT, it) }
}
