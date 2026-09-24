package com.omniscan.app.scan

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.omniscan.app.model.UsbDeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listet per USB-OTG angeschlossene Geräte auf. Zum bloßen Auflisten
 * (VID/PID/Klasse) ist keine Laufzeit-Permission nötig.
 */
class UsbScanner(context: Context) {

    private val usb = context.applicationContext
        .getSystemService(Context.USB_SERVICE) as UsbManager?

    private val _results = MutableStateFlow<List<UsbDeviceInfo>>(emptyList())
    val results: StateFlow<List<UsbDeviceInfo>> = _results.asStateFlow()

    val available: Boolean get() = usb != null

    fun refresh() {
        val devs = usb?.deviceList?.values ?: emptyList()
        _results.value = devs.map { it.toInfo() }
    }

    private fun UsbDevice.toInfo() = UsbDeviceInfo(
        deviceName = deviceName,
        vendorId = vendorId,
        productId = productId,
        manufacturer = runCatching { manufacturerName }.getOrNull(),
        product = runCatching { productName }.getOrNull(),
        deviceClass = usbClassName(deviceClass),
        interfaceCount = interfaceCount
    )

    private fun usbClassName(c: Int): String = when (c) {
        UsbConstants.USB_CLASS_APP_SPEC -> "App-spezifisch"
        UsbConstants.USB_CLASS_AUDIO -> "Audio"
        UsbConstants.USB_CLASS_CDC_DATA -> "CDC-Daten"
        UsbConstants.USB_CLASS_COMM -> "Kommunikation"
        UsbConstants.USB_CLASS_CONTENT_SEC -> "Content Security"
        UsbConstants.USB_CLASS_CSCID -> "Smartcard"
        UsbConstants.USB_CLASS_HID -> "HID (Tastatur/Maus)"
        UsbConstants.USB_CLASS_HUB -> "Hub"
        UsbConstants.USB_CLASS_MASS_STORAGE -> "Massenspeicher"
        UsbConstants.USB_CLASS_MISC -> "Misc"
        UsbConstants.USB_CLASS_PHYSICA -> "Physical"
        UsbConstants.USB_CLASS_PRINTER -> "Drucker"
        UsbConstants.USB_CLASS_STILL_IMAGE -> "Bild/Kamera"
        UsbConstants.USB_CLASS_VENDOR_SPEC -> "Hersteller-spezifisch"
        UsbConstants.USB_CLASS_VIDEO -> "Video"
        UsbConstants.USB_CLASS_WIRELESS_CONTROLLER -> "Wireless-Controller"
        UsbConstants.USB_CLASS_PER_INTERFACE -> "pro Interface definiert"
        else -> "Klasse 0x%02X".format(c)
    }
}
