package com.omniscan.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.omniscan.app.scan.BluetoothScanner
import com.omniscan.app.scan.CellScanner
import com.omniscan.app.scan.LocationScanner
import com.omniscan.app.scan.SensorScanner
import com.omniscan.app.scan.UsbScanner
import com.omniscan.app.scan.WifiScanner

/**
 * Hält alle Scanner-Instanzen für die Lebensdauer des Screens.
 * (NFC wird separat in der Activity verwaltet, da es die Activity braucht.)
 */
class ScanViewModel(app: Application) : AndroidViewModel(app) {

    val wifi = WifiScanner(app)
    val bluetooth = BluetoothScanner(app)
    val cell = CellScanner(app)
    val location = LocationScanner(app)
    val usb = UsbScanner(app)
    val sensors = SensorScanner(app)

    fun startWifi() = wifi.start()
    fun startBluetooth() = bluetooth.start()
    fun startLocation() = location.start()

    fun stopAll() {
        wifi.stop()
        bluetooth.stop()
        location.stop()
    }

    override fun onCleared() {
        stopAll()
        super.onCleared()
    }
}
