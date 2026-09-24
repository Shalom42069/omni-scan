package com.omniscan.app.scan

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.omniscan.app.model.SensorInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Listet alle im Gerät verbauten Sensoren. Keine Permission nötig. */
class SensorScanner(context: Context) {

    private val sm = context.applicationContext
        .getSystemService(Context.SENSOR_SERVICE) as SensorManager?

    private val _results = MutableStateFlow<List<SensorInfo>>(emptyList())
    val results: StateFlow<List<SensorInfo>> = _results.asStateFlow()

    fun refresh() {
        val list = sm?.getSensorList(Sensor.TYPE_ALL) ?: emptyList()
        _results.value = list.map {
            SensorInfo(
                name = it.name,
                vendor = it.vendor,
                type = it.stringType ?: "Typ ${it.type}",
                maxRange = it.maximumRange,
                resolution = it.resolution,
                powerMa = it.power,
                wakeUp = it.isWakeUpSensor
            )
        }.sortedBy { it.name }
    }
}
