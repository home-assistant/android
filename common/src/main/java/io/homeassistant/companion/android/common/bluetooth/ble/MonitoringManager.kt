package io.homeassistant.companion.android.common.bluetooth.ble

import android.content.Context
import io.homeassistant.companion.android.common.BuildConfig
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

            if (BuildConfig.DEBUG) {
                BeaconManager.setDebug(true)
            }

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
            scope.launch(Dispatchers.Main) {
                beaconManager.getRegionViewModel(region).rangedBeacons.observeForever { beacons ->
                    if (beaconManager.isAnyConsumerBound) {
                        haMonitor.setBeacons(
                            context,
                            beacons
                        )
                    }
                }
            }
        }

        val builder = beaconNotification(false, context)
        beaconManager.enableForegroundServiceScanning(builder.build(), 444)
        beaconManager.setEnableScheduledScanJobs(false)
        beaconManager.startRangingBeacons(region)
        haMonitor.sensorManager.updateBeaconMonitoringSensor(context)
    }

    fun stopMonitoring(context: Context, haMonitor: IBeaconMonitor) {
        if (isMonitoring()) {
            beaconManager.stopRangingBeacons(region)
            haMonitor.clearBeacons()
            beaconManager.disableForegroundServiceScanning()
            haMonitor.sensorManager.updateBeaconMonitoringSensor(context)
        }
    }
}
