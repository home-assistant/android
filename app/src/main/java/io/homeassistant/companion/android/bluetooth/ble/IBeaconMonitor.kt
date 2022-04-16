package io.homeassistant.companion.android.bluetooth.ble

import android.content.Context
import io.homeassistant.companion.android.sensors.BluetoothSensorManager
import org.altbeacon.beacon.Beacon

class IBeaconMonitor {
    var monitoring: Boolean = false
    lateinit var sensorManager: BluetoothSensorManager
    var beacons: Map<String, Double> = mapOf()
    private var skippedUpdates: Int = 0

    fun getNearestBeacon(tmp: Map<String, Double>): Pair<String, Double>? {
        var beaconID: String? = null
        var minDistance: Double = Double.MAX_VALUE

        for ((id, distance) in tmp) {
            if (distance <= minDistance) {
                minDistance = distance
                beaconID = id
            }
        }

        if (beaconID == null) {
            return null
        }
        return Pair(beaconID, minDistance)
    }

    fun setBeacons(context: Context, newBeacons: Collection<Beacon>) {
        var tmp: Map<String, Double> = mapOf()
        var requireUpdate: Boolean = false
        for (beacon: Beacon in newBeacons) {
            val id = beacon.id1.toString()
            val distance = beacon.distance
            val previousDistance = beacons[id]

            tmp += Pair(id, beacon.distance)
            if (previousDistance == null || kotlin.math.abs(previousDistance - distance) >= 0.5) {
                requireUpdate = true
            }
        }

        if (!requireUpdate) {
            return
        }

        if (tmp.count() >= beacons.count() || skippedUpdates >= 10) {
            beacons = tmp
            skippedUpdates = 0
            sensorManager!!.updateBeaconMonitoringSensor(context)
        } else {
            skippedUpdates++
        }
    }
}


