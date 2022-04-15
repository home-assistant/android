package io.homeassistant.companion.android.bluetooth.ble

import android.content.Context
import android.util.Log
import io.homeassistant.companion.android.sensors.BluetoothSensorManager
import io.homeassistant.companion.android.sensors.SensorWorker
import org.altbeacon.beacon.Beacon

class IBeaconMonitor {
    var monitoring: Boolean = false
    lateinit var sensorManager: BluetoothSensorManager
    var beacons: Map<String, Double> = mapOf()
    var requireSensorUpdate: Boolean = false
    var noBeaconIterations: Int = 0

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
        for (beacon: Beacon in newBeacons) {
            tmp += Pair(beacon.id1.toString(), beacon.distance)
        }

        val previousNearest = getNearestBeacon(beacons)
        val currentNearest = getNearestBeacon(tmp)

        if (currentNearest == null) {
            noBeaconIterations++
        }

        // due to the variation of beacon distances, we only update the sensor if one of the following condition is met
        // - the nearest beacon did change
        // - there is a major change in distance (>= 0.5m)
        // - we didn't detect a beacon for 10 iterations
        if (previousNearest == null ||
            (currentNearest == null && noBeaconIterations >= 10) ||
            (
                currentNearest != null && previousNearest != null &&
                (
                    currentNearest.first != previousNearest.first ||
                    kotlin.math.abs(currentNearest.second - previousNearest.second) >= 0.5
                )
            )
        ) {
            beacons = tmp
            noBeaconIterations = 0
            sensorManager!!.updateBeaconMonitoringSensor(context)
        }
    }
}


