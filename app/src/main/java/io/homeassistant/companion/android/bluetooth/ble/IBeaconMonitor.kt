package io.homeassistant.companion.android.bluetooth.ble

import android.content.Context
import io.homeassistant.companion.android.sensors.BluetoothSensorManager
import io.homeassistant.companion.android.sensors.SensorWorker
import org.altbeacon.beacon.Beacon
import kotlin.math.round

const val MAX_SKIPPED_UPDATED = 10

data class IBeacon(
    var uuid: String,
    var major: String,
    var minor: String,
    var distance: Double,
    var rssi: Double,
    var skippedUpdated: Int,
    var name: String?,
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
            tmp += Pair(name(existingBeacon.uuid, existingBeacon.major, existingBeacon.minor), existingBeacon)
        }
        for (newBeacon in newBeacons) {
            val uuid = newBeacon.id1.toString()
            val major = newBeacon.id2.toString()
            val minor = newBeacon.id3.toString()
            val distance = round(newBeacon.distance * 100) / 100
            val rssi = newBeacon.runningAverageRssi
            val name = newBeacon.bluetoothName
            if (!tmp.contains(name(uuid, major, minor))) { // we found a new beacon
                requireUpdate = true
            }
            tmp += Pair(name(uuid, major, minor), IBeacon(uuid, major, minor, distance, rssi, 0, name))
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
        beacons.forEachIndexed foreach@{ i, existingBeacon ->
            if (i < sorted.size) {
                if (name(sorted[i].uuid, sorted[i].major, sorted[i].minor) != name(existingBeacon.uuid, existingBeacon.major, existingBeacon.minor) || // the distance order switched
                    kotlin.math.abs(sorted[i].distance - existingBeacon.distance) > 0.5 // the distance difference is greater than 0.5m
                ) {
                    requireUpdate = true
                    return@foreach
                }
            }
        }
        if (requireUpdate) {
            sendUpdate(context, sorted)
            return
        }
    }

    private fun sendUpdate(context: Context, tmp: List<IBeacon>) {
        beacons = tmp
        sensorManager.updateBeaconMonitoringSensor(context)
        SensorWorker.start(context)
    }
}
