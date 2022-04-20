package io.homeassistant.companion.android.bluetooth.ble

import android.content.Context
import io.homeassistant.companion.android.sensors.BluetoothSensorManager
import org.altbeacon.beacon.Beacon

const val MAX_SKIPPED_UPDATED = 10

class IBeaconMonitor {
    var monitoring: Boolean = false
    lateinit var sensorManager: BluetoothSensorManager
    var beacons: List<Pair<String, Double>> = listOf()
    private var skippedUpdates: Int = 0

    fun sort(tmp: List<Pair<String, Double>>): List<Pair<String, Double>> {
        return tmp.sortedBy { (_, value) -> value}
    }

    fun setBeacons(context: Context, newBeacons: Collection<Beacon>) {
        var tmp: List<Pair<String, Double>> = listOf()
        for (newBeacon: Beacon in newBeacons) {
            val id = newBeacon.id1.toString()
            val distance = kotlin.math.round(newBeacon.distance * 100) / 100
            tmp += Pair(id, distance)
        }

        tmp = sort(tmp)
        if (beacons.count() < tmp.count() || skippedUpdates > MAX_SKIPPED_UPDATED) {
            sendUpdate(context, tmp)
        } else if (beacons.count() == tmp.count()) {
            tmp.forEachIndexed {i, newBeacon ->
                if (beacons[i].first != newBeacon.first || kotlin.math.abs(beacons[i].second - newBeacon.second) > 0.5){
                    sendUpdate(context, tmp)
                    return@forEachIndexed
                }
            }
        } else {
            skippedUpdates++
        }
    }

    private fun sendUpdate(context: Context, tmp: List<Pair<String, Double>>) {
        beacons = tmp
        skippedUpdates = 0
        sensorManager!!.updateBeaconMonitoringSensor(context)
    }
}


