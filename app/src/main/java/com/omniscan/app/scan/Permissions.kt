package com.omniscan.app.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Zentrale Verwaltung der Laufzeit-Permissions je nach Android-Version. */
object Permissions {

    /** Alle Permissions, die die App zur Laufzeit anfragen sollte. */
    fun required(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        return list.toTypedArray()
    }

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    fun allGranted(context: Context): Boolean =
        required().all { granted(context, it) }

    fun hasLocation(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBluetooth(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            granted(context, Manifest.permission.BLUETOOTH_SCAN)
        else granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasWifi(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            granted(context, Manifest.permission.NEARBY_WIFI_DEVICES) ||
                granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        else granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPhone(context: Context): Boolean =
        granted(context, Manifest.permission.READ_PHONE_STATE)
}
