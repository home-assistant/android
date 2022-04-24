package io.homeassistant.companion.android.bluetooth.ble

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Region

object MonitoringManager {
    private lateinit var beaconManager: BeaconManager
    private lateinit var region: Region
    var scanPeriod: Long = 1100
    var scanInterval: Long = 500

    private fun buildRegion(): Region {
        return Region("all-beacons", null, null, null)
    }

    private fun shouldStartMonitoring(): Boolean {
        return !this::beaconManager.isInitialized || !beaconManager.isAnyConsumerBound
    }

    @Synchronized
    fun startMonitoring(context: Context, haMonitor: IBeaconMonitor) {
        if (!shouldStartMonitoring()) {
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
            GlobalScope.launch(Dispatchers.Main) {
                beaconManager.getRegionViewModel(region).rangedBeacons.observeForever { beacons ->
                    haMonitor.setBeacons(
                        context,
                        beacons
                    )
                }
            }
        }
        val bluetoothAdapter = context.getSystemService<BluetoothManager>()?.adapter
        val bluetoothOn = bluetoothAdapter?.isEnabled == true
        if (bluetoothOn) {
            beaconManager.startRangingBeacons(region)
            haMonitor.monitoring = true
        } else {
            stopMonitoring(haMonitor)
        }
    }

    fun stopMonitoring(haMonitor: IBeaconMonitor) {
        if (this::beaconManager.isInitialized && haMonitor.monitoring) {
            beaconManager.stopRangingBeacons(region)
        }
        haMonitor.monitoring = false
    }
}
