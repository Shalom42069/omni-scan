package com.omniscan.app.scan

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.omniscan.app.model.AppPermissionInfo

/**
 * Listet installierte Apps und zeigt, welche "gefährlichen" (dangerous-level)
 * Permissions sie angefordert haben und welche davon tatsächlich gewährt
 * wurden. Rein PackageManager-basiert — keine Root-Rechte nötig, funktioniert
 * für alle Apps (nicht nur die eigene) dank QUERY_ALL_PACKAGES-freiem
 * Standardverhalten auf den meisten OEM-ROMs bzw. der öffentlichen
 * getInstalledPackages-API.
 */
object PermissionAuditor {

    // Kuratierte Liste der bekanntesten "dangerous" Permissions —
    // die vollständige Liste ändert sich je Android-Version.
    private val DANGEROUS_PREFIXES = setOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_PHONE_STATE",
        "android.permission.CALL_PHONE",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.BODY_SENSORS",
        "android.permission.ACTIVITY_RECOGNITION",
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.NEARBY_WIFI_DEVICES",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CALENDAR"
    )

    fun auditInstalledApps(context: Context, includeSystemApps: Boolean = false): List<AppPermissionInfo> {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS
        val packages = runCatching {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(flags)
        }.getOrDefault(emptyList())

        return packages.mapNotNull { pkg ->
            val appInfo = pkg.applicationInfo ?: return@mapNotNull null
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem && !includeSystemApps) return@mapNotNull null

            val requested = pkg.requestedPermissions ?: emptyArray()
            val grantedFlags = pkg.requestedPermissionsFlags
            val granted = mutableListOf<String>()
            val denied = mutableListOf<String>()

            requested.forEachIndexed { i, perm ->
                if (perm !in DANGEROUS_PREFIXES) return@forEachIndexed
                val isGranted = grantedFlags != null &&
                    i < grantedFlags.size &&
                    (grantedFlags[i] and PackageManager.PERMISSION_GRANTED) != 0
                if (isGranted) granted += perm.removePrefix("android.permission.")
                else denied += perm.removePrefix("android.permission.")
            }

            if (granted.isEmpty() && denied.isEmpty()) return@mapNotNull null

            AppPermissionInfo(
                packageName = pkg.packageName,
                appName = runCatching {
                    pm.getApplicationLabel(appInfo).toString()
                }.getOrDefault(pkg.packageName),
                isSystemApp = isSystem,
                grantedDangerous = granted,
                deniedDangerous = denied
            )
        }.sortedByDescending { it.grantedDangerous.size }
    }
}
