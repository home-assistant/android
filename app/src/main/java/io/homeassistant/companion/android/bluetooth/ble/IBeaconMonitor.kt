package io.homeassistant.companion.android.bluetooth.ble

import android.content.Context
import io.homeassistant.companion.android.sensors.BluetoothSensorManager
import io.homeassistant.companion.android.sensors.SensorWorker
import org.altbeacon.beacon.Beacon
import kotlin.math.abs
import kotlin.math.round

const val MAX_SKIPPED_UPDATED = 10

data class IBeacon(
    override val uuid: String,
    override val major: String,
    override val minor: String,
    val distance: Double,
    val rssi: Double,
    var skippedUpdated: Int,
) : IBeaconNameFormat

class IBeaconMonitor {
    lateinit var sensorManager: BluetoothSensorManager
    var beacons: List<IBeacon> = listOf()

    private fun sort(tmp: Collection<IBeacon>): Collection<IBeacon> {
        return tmp.sortedBy { it.distance }
    }

    fun setBeacons(context: Context, newBeacons: Collection<Beacon>) {
        var requireUpdate = false
        val tmp = mutableMapOf<String, IBeacon>()
        for (existingBeacon in beacons) {
            existingBeacon.skippedUpdated++
            tmp += existingBeacon.name to existingBeacon
        }
        for (newBeacon in newBeacons) {
            val uuid = newBeacon.id1.toString()
            val major = newBeacon.id2.toString()
            val minor = newBeacon.id3.toString()
            val distance = round(newBeacon.distance * 100) / 100
            val rssi = newBeacon.runningAverageRssi

            val beacon = IBeacon(uuid, major, minor, distance, rssi, 0)
            if (beacon.name !in tmp) { // we found a new beacon
                requireUpdate = true
            }
            tmp += beacon.name to beacon
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
        for ((i, existingBeacon) in beacons.withIndex()) {
            if (i < sorted.size) {
                if (sorted[i].name != existingBeacon.name || // the distance order switched
                    abs(sorted[i].distance - existingBeacon.distance) > 0.5 // the distance difference is greater than 0.5m
                ) {
                    requireUpdate = true
                    break
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
