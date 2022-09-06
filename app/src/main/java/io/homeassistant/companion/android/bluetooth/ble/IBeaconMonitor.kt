package io.homeassistant.companion.android.bluetooth.ble

import android.content.Context
import io.homeassistant.companion.android.sensors.BluetoothSensorManager
import io.homeassistant.companion.android.sensors.SensorWorker
import org.altbeacon.beacon.Beacon
import kotlin.math.round

const val MAX_SKIPPED_UPDATED = 10

data class IBeacon(
    var uuid: String,
    var distance: Double,
    var rssi: Double,
    var skippedUpdated: Int,
)

class IBeaconMonitor {
    lateinit var sensorManager: BluetoothSensorManager
    var beacons: List<IBeacon> = listOf()

    fun sort(tmp: Collection<IBeacon>): Collection<IBeacon> {
        return tmp.sortedBy { it.distance }
    }

    fun setBeacons(context: Context, newBeacons: Collection<Beacon>) {
        var requireUpdate = false
        var tmp: Map<String, IBeacon> = linkedMapOf()
        for (existingBeacon in beacons) {
            existingBeacon.skippedUpdated++
            tmp += Pair(existingBeacon.uuid, existingBeacon)
        }
        for (newBeacon in newBeacons) {
            val uuid = newBeacon.id1.toString()
            val distance = round(newBeacon.distance * 100) / 100
            val rssi = newBeacon.runningAverageRssi
            if (!tmp.contains(uuid)) { // we found a new beacon
                requireUpdate = true
            }
            tmp += Pair(uuid, IBeacon(uuid, distance, rssi, 0))
        }
        val sorted = sort(tmp.values).toMutableList()
        if (requireUpdate) {
            sendUpdate(context, sorted)
            return
        }
        for (i in sorted.indices.reversed()) {
            if (sorted[i].skippedUpdated > MAX_SKIPPED_UPDATED) { // a old beacon expired
                sorted.removeAt(i)
                requireUpdate = true
            }
        }
        if (requireUpdate) {
            sendUpdate(context, sorted)
            return
        }
        assert(sorted.count() == beacons.count())
        beacons.forEachIndexed foreach@{ i, existingBeacon ->
            if (sorted[i].uuid != existingBeacon.uuid || // the distance order switched
                kotlin.math.abs(sorted[i].distance - existingBeacon.distance) > 0.5 // the distance difference is greater than 0.5m
            ) {
                requireUpdate = true
                return@foreach
            }
        }
        if (requireUpdate) {
            sendUpdate(context, sorted)
            return
        }
    }

    private fun sendUpdate(context: Context, tmp: List<IBeacon>) {
        beacons = tmp
        sensorManager!!.updateBeaconMonitoringSensor(context)
        SensorWorker.start(context)
    }
}
