package com.omniscan.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omniscan.app.scan.BluetoothScanner
import com.omniscan.app.scan.GattExplorer
import com.omniscan.app.scan.PermissionAuditor
import com.omniscan.app.scan.TlsInspector
import com.omniscan.app.scan.CellScanner
import com.omniscan.app.scan.LocationScanner
import com.omniscan.app.scan.PortScanner
import com.omniscan.app.scan.SensorScanner
import com.omniscan.app.scan.UsbScanner
import com.omniscan.app.scan.WifiScanner
import com.omniscan.app.model.AppPermissionInfo
import com.omniscan.app.model.TlsInspectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val portScanner = PortScanner()
    val gattExplorer = GattExplorer(app)

    private var portScanJob: Job? = null
    private var tlsJob: Job? = null
    private var auditJob: Job? = null

    private val _tlsResult = MutableStateFlow<TlsInspectionResult?>(null)
    val tlsResult: StateFlow<TlsInspectionResult?> = _tlsResult.asStateFlow()
    private val _tlsLoading = MutableStateFlow(false)
    val tlsLoading: StateFlow<Boolean> = _tlsLoading.asStateFlow()

    private val _appAudit = MutableStateFlow<List<AppPermissionInfo>>(emptyList())
    val appAudit: StateFlow<List<AppPermissionInfo>> = _appAudit.asStateFlow()
    private val _auditLoading = MutableStateFlow(false)
    val auditLoading: StateFlow<Boolean> = _auditLoading.asStateFlow()

    fun startWifi() = wifi.start()
    fun startBluetooth() = bluetooth.start()
    fun startLocation() = location.start()

    fun startPortScan(host: String, ports: List<Int>) {
        portScanJob?.cancel()
        portScanJob = viewModelScope.launch {
            portScanner.scan(host, ports)
        }
    }

    fun cancelPortScan() {
        portScanJob?.cancel()
    }

    fun inspectTls(host: String, port: Int) {
        tlsJob?.cancel()
        _tlsLoading.value = true
        tlsJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { TlsInspector.inspect(host, port) }
            _tlsResult.value = result
            _tlsLoading.value = false
        }
    }

    fun runAppAudit(includeSystemApps: Boolean = false) {
        auditJob?.cancel()
        _auditLoading.value = true
        auditJob = viewModelScope.launch {
            val list = withContext(Dispatchers.Default) {
                PermissionAuditor.auditInstalledApps(getApplication(), includeSystemApps)
            }
            _appAudit.value = list
            _auditLoading.value = false
        }
    }

    fun stopAll() {
        wifi.stop()
        bluetooth.stop()
        location.stop()
        portScanJob?.cancel()
        tlsJob?.cancel()
        auditJob?.cancel()
        gattExplorer.disconnect()
    }

    override fun onCleared() {
        stopAll()
        super.onCleared()
    }
}
