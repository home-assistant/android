package io.homeassistant.companion.android.bluetooth.ble

import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.core.content.getSystemService
import org.altbeacon.beacon.*
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.altbeacon.beacon.service.RunningAverageRssiFilter
import java.util.UUID

const val TAG = "Beacon Manager"

object MonitoringManager {
    private lateinit var beaconManager: BeaconManager
    private lateinit var region: Region

    private fun buildRegion(): Region {
        return Region("all-beacons", null, null, null)
    }

    private fun shouldStartReceiving(): Boolean {
        return !this::beaconManager.isInitialized || !beaconManager.isAnyConsumerBound
    }

    @Synchronized
    fun startMonitoring(context: Context, haMonitor: IBeaconMonitor) {
        if (!shouldStartReceiving()) {
            return
        }
        if (!this::beaconManager.isInitialized) {
            beaconManager = BeaconManager.getInstanceForApplication(context)
            BeaconManager.setDebug(true)

            // find iBeacons
            beaconManager.getBeaconParsers().add(
                BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"))

            beaconManager.setIntentScanningStrategyEnabled(true)
            beaconManager.setBackgroundBetweenScanPeriod(500);
            beaconManager.setBackgroundScanPeriod(1100);
            BeaconManager.setRssiFilterImplClass(KalmanFilter::class.java)
        }
        if (!beaconManager.isAnyConsumerBound) {
            region = buildRegion()
            GlobalScope.launch(Dispatchers.Main) {
                beaconManager.getRegionViewModel(region).rangedBeacons.observeForever(
                    object : Observer<Collection<Beacon>> {
                        override fun onChanged(beacons: Collection<Beacon>) {
                            haMonitor.setBeacons(context, beacons)
                        }
                    }
                )
            }
        }
        val bluetoothAdapter = context.getSystemService<BluetoothManager>()?.adapter
        val bluetoothOn = bluetoothAdapter?.isEnabled == true
        if (bluetoothOn) {
            Log.e(TAG, "starting")
            beaconManager.startRangingBeacons(region)
            haMonitor.monitoring = true

        } else {
            stopMonitoring(haMonitor)
        }
    }

    fun stopMonitoring(haMonitor: IBeaconMonitor) {
        if (this::beaconManager.isInitialized && haMonitor.monitoring) {
            Log.e(TAG, "stopping")
            beaconManager.stopRangingBeacons(region)
        }
        haMonitor.monitoring = false
    }
}