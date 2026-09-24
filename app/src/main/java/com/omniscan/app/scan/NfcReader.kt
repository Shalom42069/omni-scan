package com.omniscan.app.scan

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import com.omniscan.app.model.NfcTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.Charset

/**
 * Liest NFC-Tags (13,56 MHz) im Reader-Mode.
 *
 * WICHTIG: 125-kHz-RFID (EM4100, HID Prox etc.) kann die Handy-Hardware
 * NICHT lesen — das NFC-Frontend arbeitet ausschließlich auf 13,56 MHz.
 */
class NfcReader(private val activity: Activity) {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    val available: Boolean get() = adapter != null
    val enabled: Boolean get() = adapter?.isEnabled == true

    private val _last = MutableStateFlow<NfcTag?>(null)
    val last: StateFlow<NfcTag?> = _last.asStateFlow()

    private val _history = MutableStateFlow<List<NfcTag>>(emptyList())
    val history: StateFlow<List<NfcTag>> = _history.asStateFlow()

    private val callback = NfcAdapter.ReaderCallback { tag -> onTag(tag) }

    /** Im onResume der Activity aufrufen. */
    fun enableReader() {
        val a = adapter ?: return
        // Alle Frequenz-/Modulationsarten aktivieren; NDEF-Check bleibt an
        // (FLAG_READER_SKIP_NDEF_CHECK wird bewusst NICHT gesetzt).
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE
        runCatching { a.enableReaderMode(activity, callback, flags, null) }
    }

    /** Im onPause der Activity aufrufen. */
    fun disableReader() {
        runCatching { adapter?.disableReaderMode(activity) }
    }

    private fun onTag(tag: Tag) {
        val uid = tag.id?.toHex() ?: ""
        val techs = tag.techList?.map { it.substringAfterLast('.') } ?: emptyList()

        var records: List<String> = emptyList()
        var maxSize: Int? = null
        var writable: Boolean? = null

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            maxSize = ndef.maxSize
            writable = ndef.isWritable
            val msg: NdefMessage? = ndef.cachedNdefMessage
                ?: runCatching {
                    ndef.connect(); ndef.ndefMessage
                }.getOrNull().also { runCatching { ndef.close() } }
            records = msg?.records?.map { it.describe() } ?: emptyList()
        }

        val result = NfcTag(
            uid = uid,
            techList = techs,
            ndefRecords = records,
            maxSizeBytes = maxSize,
            writable = writable,
            timestamp = System.currentTimeMillis()
        )
        _last.value = result
        _history.value = (listOf(result) + _history.value).take(50)
    }

    private fun NdefRecord.describe(): String {
        return when {
            tnf == NdefRecord.TNF_WELL_KNOWN &&
                type.contentEquals(NdefRecord.RTD_TEXT) -> "Text: ${decodeText()}"
            tnf == NdefRecord.TNF_WELL_KNOWN &&
                type.contentEquals(NdefRecord.RTD_URI) -> "URI: ${decodeUri()}"
            tnf == NdefRecord.TNF_MIME_MEDIA ->
                "MIME(${String(type)}): ${payload.toHex()}"
            else -> "TNF=$tnf ${payload.toHex()}"
        }
    }

    private fun NdefRecord.decodeText(): String {
        if (payload.isEmpty()) return ""
        val status = payload[0].toInt()
        val enc = if (status and 0x80 == 0) "UTF-8" else "UTF-16"
        val langLen = status and 0x3F
        return runCatching {
            String(
                payload, langLen + 1,
                payload.size - langLen - 1,
                Charset.forName(enc)
            )
        }.getOrDefault(payload.toHex())
    }

    private fun NdefRecord.decodeUri(): String {
        if (payload.isEmpty()) return ""
        val prefixes = arrayOf(
            "", "http://www.", "https://www.", "http://", "https://",
            "tel:", "mailto:", "ftp://anonymous:anonymous@", "ftp://ftp.",
            "ftps://", "sftp://", "smb://", "nfs://", "ftp://", "dav://",
            "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:",
            "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://",
            "tcpobex://", "irdaobex://", "file://", "urn:epc:id:",
            "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:",
            "urn:nfc:"
        )
        val idx = payload[0].toInt() and 0xFF
        val prefix = prefixes.getOrElse(idx) { "" }
        return prefix + String(payload, 1, payload.size - 1, Charsets.UTF_8)
    }

    private fun ByteArray.toHex(): String =
        joinToString(":") { "%02X".format(it) }
}
