package io.homeassistant.companion.android.common.bluetooth.ble

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Region

class MonitoringManager {
    private lateinit var beaconManager: BeaconManager
    private lateinit var region: Region
    var scanPeriod: Long = 1100
    var scanInterval: Long = 500

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private fun buildRegion(): Region {
        return Region("all-beacons", null, null, null)
    }

    fun isMonitoring(): Boolean {
        return this::beaconManager.isInitialized && beaconManager.isAnyConsumerBound
    }

    @Synchronized
    fun startMonitoring(context: Context, haMonitor: IBeaconMonitor) {
        if (isMonitoring()) {
            return
        }
        if (!this::beaconManager.isInitialized) {
            beaconManager = BeaconManager.getInstanceForApplication(context)

            // find iBeacons
            beaconManager.beaconParsers.add(
                BeaconParser()
                    .setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")
            )

            BeaconManager.setRssiFilterImplClass(KalmanFilter::class.java)
        }
        if (!beaconManager.isAnyConsumerBound) {
            beaconManager.foregroundScanPeriod = scanPeriod
            beaconManager.foregroundBetweenScanPeriod = scanInterval
            beaconManager.backgroundScanPeriod = scanPeriod
            beaconManager.backgroundBetweenScanPeriod = scanInterval

            region = buildRegion()
            var observerCount = 0
            scope.launch(Dispatchers.Main) {
                beaconManager.getRegionViewModel(region).rangedBeacons.observeForever { beacons ->
                    if (observerCount > 0)
                        haMonitor.setBeacons(
                            context,
                            beacons
                        )
                    observerCount++
                }
            }
        }

        beaconManager.startRangingBeacons(region)
        haMonitor.sensorManager.updateBeaconMonitoringSensor(context)
    }

    fun stopMonitoring(context: Context, haMonitor: IBeaconMonitor) {
        haMonitor.beacons = emptyList()
        haMonitor.lastSeenBeacons = emptyList()
        if (isMonitoring()) {
            beaconManager.stopRangingBeacons(region)
            haMonitor.sensorManager.updateBeaconMonitoringSensor(context)
        }
    }
}
