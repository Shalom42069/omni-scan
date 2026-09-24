package com.omniscan.app.scan

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import com.omniscan.app.model.LocationInfo
import com.omniscan.app.model.Satellite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Liefert Position + Live-Liste der GNSS-Satelliten (GPS, GLONASS, Galileo, BeiDou …).
 * Benötigt ACCESS_FINE_LOCATION.
 */
@SuppressLint("MissingPermission")
class LocationScanner(context: Context) {

    private val lm = context.applicationContext
        .getSystemService(Context.LOCATION_SERVICE) as LocationManager?

    private val _location = MutableStateFlow<LocationInfo?>(null)
    val location: StateFlow<LocationInfo?> = _location.asStateFlow()

    private val _satellites = MutableStateFlow<List<Satellite>>(emptyList())
    val satellites: StateFlow<List<Satellite>> = _satellites.asStateFlow()

    val gpsEnabled: Boolean
        get() = runCatching {
            lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        }.getOrDefault(false)

    private val locListener = LocationListener { loc -> publish(loc) }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val sats = ArrayList<Satellite>(status.satelliteCount)
            for (i in 0 until status.satelliteCount) {
                sats += Satellite(
                    svid = status.getSvid(i),
                    constellation = constellationName(status.getConstellationType(i)),
                    cn0 = status.getCn0DbHz(i),
                    usedInFix = status.usedInFix(i),
                    elevationDeg = status.getElevationDegrees(i),
                    azimuthDeg = status.getAzimuthDegrees(i)
                )
            }
            _satellites.value = sats.sortedByDescending { it.cn0 }
        }
    }

    fun start() {
        val m = lm ?: return
        runCatching {
            m.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 0f, locListener
            )
        }
        runCatching {
            m.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 1000L, 0f, locListener
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { m.registerGnssStatusCallback(gnssCallback) }
        }
        // Letzte bekannte Position sofort anzeigen
        runCatching {
            m.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: m.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()?.let { publish(it) }
    }

    fun stop() {
        val m = lm ?: return
        runCatching { m.removeUpdates(locListener) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { m.unregisterGnssStatusCallback(gnssCallback) }
        }
    }

    private fun publish(loc: Location) {
        _location.value = LocationInfo(
            lat = loc.latitude,
            lon = loc.longitude,
            accuracyM = if (loc.hasAccuracy()) loc.accuracy else -1f,
            altitudeM = if (loc.hasAltitude()) loc.altitude else 0.0,
            speedMs = if (loc.hasSpeed()) loc.speed else 0f,
            provider = loc.provider ?: "?",
            timestamp = loc.time
        )
    }

    private fun constellationName(type: Int): String = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        GnssStatus.CONSTELLATION_IRNSS -> "NavIC"
        else -> "Unbekannt"
    }
}
