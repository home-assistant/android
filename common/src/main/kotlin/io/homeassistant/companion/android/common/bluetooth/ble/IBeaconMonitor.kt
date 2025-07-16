package io.homeassistant.companion.android.common.bluetooth.ble

import android.content.Context
import io.homeassistant.companion.android.common.sensors.BluetoothSensorManager
import io.homeassistant.companion.android.common.sensors.SensorUpdateReceiver
import kotlin.math.abs
import kotlin.math.round
import org.altbeacon.beacon.Beacon

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
    var lastSeenBeacons: Collection<Beacon> = listOf()
    private var uuidFilter = listOf<String>()
    private var uuidFilterExclude = false

    private fun sort(tmp: Collection<IBeacon>): Collection<IBeacon> {
        return tmp.sortedBy { it.distance }
    }

    private fun ignoreBeacon(uuid: String): Boolean {
        val inList = uuidFilter.contains(uuid)
        return if (uuidFilterExclude) {
            inList // exclude filter, keep those not in list
        } else {
            !(inList || uuidFilter.isEmpty()) // include filter, keep those in list (or all if the list is empty)
        }
    }

    fun clearBeacons() {
        beacons = listOf()
    }

    fun setBeacons(context: Context, newBeacons: Collection<Beacon>) {
        lastSeenBeacons = newBeacons // unfiltered list, for the settings UI
        var requireUpdate = false
        val tmp = mutableMapOf<String, IBeacon>()
        for (existingBeacon in beacons) {
            if (++existingBeacon.skippedUpdated > MAX_SKIPPED_UPDATED) { // an old beacon expired
                requireUpdate = true
            } else {
                tmp += existingBeacon.name to existingBeacon
            }
        }
        for (newBeacon in newBeacons) {
            val uuid = newBeacon.id1.toString()
            val major = newBeacon.id2.toString()
            val minor = newBeacon.id3.toString()
            val distance = round(newBeacon.distance * 100) / 100
            val rssi = newBeacon.runningAverageRssi

            val beacon = IBeacon(uuid, major, minor, distance, rssi, 0)
            val existing = tmp[beacon.name]
            if (existing == null) { // we found a new beacon
                if (ignoreBeacon(uuid)) continue // UUID filter (note: no need to check old beacons)
                requireUpdate = true
            } else {
                // beacon seen, make sure skippedUpdated=0, even if requireUpdate stays false (and beacons list is not replaced)
                existing.skippedUpdated = 0
            }
            tmp += beacon.name to beacon
        }
        val sorted = sort(tmp.values).toMutableList()
        if (requireUpdate) {
            sendUpdate(context, sorted)
            return
        }
        for ((i, existingBeacon) in beacons.withIndex()) {
            if (i < sorted.size) {
                if (sorted[i].name != existingBeacon.name ||
                    abs(sorted[i].distance - existingBeacon.distance) > 0.5
                ) {
                    // the distance order switched and the distance difference is greater than 0.5m
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
        SensorUpdateReceiver.updateSensors(context)
    }

    fun setUUIDFilter(uuidFilter: List<String>, uuidFilterExclude: Boolean) {
        this.uuidFilter = uuidFilter
        this.uuidFilterExclude = uuidFilterExclude

        // existing beacons are only filtered when the filter changes
        beacons
            .filter { ignoreBeacon(it.uuid) }
            .forEach { it.skippedUpdated = MAX_SKIPPED_UPDATED } // delete in the next update
    }
}
