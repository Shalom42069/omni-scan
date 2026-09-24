package com.omniscan.app.scan

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellIdentityNr
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import com.omniscan.app.model.CellRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Liest sichtbare Mobilfunkzellen (dienende + Nachbarzellen).
 * Benötigt READ_PHONE_STATE + ACCESS_FINE_LOCATION.
 */
@SuppressLint("MissingPermission")
class CellScanner(context: Context) {

    private val tm = context.applicationContext
        .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?

    private val _results = MutableStateFlow<List<CellRecord>>(emptyList())
    val results: StateFlow<List<CellRecord>> = _results.asStateFlow()

    val available: Boolean get() = tm != null

    val networkOperator: String
        get() = runCatching { tm?.networkOperatorName ?: "" }.getOrDefault("")

    @Suppress("DEPRECATION")
    fun refresh() {
        val list = runCatching { tm?.allCellInfo }.getOrNull() ?: emptyList()
        _results.value = list.mapNotNull { it.toRecord() }
    }

    private fun CellInfo.toRecord(): CellRecord? = when (this) {
        is CellInfoLte -> {
            val id = cellIdentity
            val s = cellSignalStrength
            CellRecord(
                type = "LTE",
                registered = isRegistered,
                identity = "MCC=${id.mccString} MNC=${id.mncString} " +
                    "TAC=${id.tac} CI=${id.ci} PCI=${id.pci} EARFCN=${id.earfcn}",
                signalDbm = s.dbm,
                extra = "RSRP=${s.rsrp} RSRQ=${s.rsrq} SNR=${s.rssnr} TA=${s.timingAdvance}"
            )
        }
        is CellInfoGsm -> {
            val id = cellIdentity
            val s = cellSignalStrength
            CellRecord(
                type = "GSM",
                registered = isRegistered,
                identity = "MCC=${id.mccString} MNC=${id.mncString} " +
                    "LAC=${id.lac} CID=${id.cid} ARFCN=${id.arfcn}",
                signalDbm = s.dbm,
                extra = "ASU=${s.asuLevel}"
            )
        }
        is CellInfoWcdma -> {
            val id = cellIdentity
            val s = cellSignalStrength
            CellRecord(
                type = "WCDMA",
                registered = isRegistered,
                identity = "MCC=${id.mccString} MNC=${id.mncString} " +
                    "LAC=${id.lac} CID=${id.cid} PSC=${id.psc} UARFCN=${id.uarfcn}",
                signalDbm = s.dbm,
                extra = "ASU=${s.asuLevel}"
            )
        }
        is CellInfoCdma -> {
            val id = cellIdentity
            val s = cellSignalStrength
            CellRecord(
                type = "CDMA",
                registered = isRegistered,
                identity = "SID=${id.systemId} NID=${id.networkId} BID=${id.basestationId}",
                signalDbm = s.dbm,
                extra = "dBm=${s.cdmaDbm} EcIo=${s.cdmaEcio}"
            )
        }
        else -> nrRecord()
    }

    /** NR (5G) erst ab Android 10 (API 29). */
    private fun CellInfo.nrRecord(): CellRecord? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (this !is CellInfoNr) return null
        val id = cellIdentity as? CellIdentityNr ?: return null
        val s = cellSignalStrength as? CellSignalStrengthNr
        return CellRecord(
            type = "NR (5G)",
            registered = isRegistered,
            identity = "MCC=${id.mccString} MNC=${id.mncString} " +
                "TAC=${id.tac} NCI=${id.nci} PCI=${id.pci} ARFCN=${id.nrarfcn}",
            signalDbm = s?.dbm,
            extra = s?.let { "SS-RSRP=${it.ssRsrp} SS-RSRQ=${it.ssRsrq} SS-SINR=${it.ssSinr}" } ?: ""
        )
    }
}
