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
        var requireUpdate = false

        for (beacon: Beacon in newBeacons) {
            val id = beacon.id1.toString()
            val distance = beacon.distance
            val previousDistance = beacons[id]

            tmp += Pair(id, beacon.distance)
            if (previousDistance == null || kotlin.math.abs(previousDistance - distance) >= 0.5) {
                requireUpdate = true
            }
        }

        val previousNearest = getNearestBeacon(beacons)
        val currentNearest = getNearestBeacon(beacons)
        if (currentNearest != null && previousNearest != null && currentNearest.first != previousNearest.first) {
            requireUpdate = true
        } else if (previousNearest == null) {
            requireUpdate = true
        }

        if (requireUpdate) {
            Log.e("Beacon Monitor", "update ${newBeacons.count()}")
            beacons = tmp
            sensorManager!!.updateBeaconMonitoringSensor(context)
        } else {
            Log.e("Beacon Monitor", "skip update ${newBeacons.count()}")
        }
    }
}


