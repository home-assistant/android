package io.homeassistant.companion.android.common.bluetooth.ble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import io.homeassistant.companion.android.common.sensors.SensorUpdateReceiver
import io.homeassistant.companion.android.common.util.CHANNEL_BEACON_MONITOR
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
                    .setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"),
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
                            beacons,
                        )
                    }
                }
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_BEACON_MONITOR)
        builder.setSmallIcon(R.drawable.ic_stat_ic_notification)
        builder.setContentTitle(context.getString(R.string.beacon_scanning))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_BEACON_MONITOR,
                    context.getString(R.string.beacon_scanning),
                    NotificationManager.IMPORTANCE_LOW,
                )
            val notifManager = context.getSystemService<NotificationManager>()!!
            notifManager.createNotificationChannel(channel)
        }
        val stopScanningIntent = Intent(context, SensorUpdateReceiver::class.java)
        stopScanningIntent.action = SensorReceiverBase.ACTION_STOP_BEACON_SCANNING
        val stopScanningPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            stopScanningIntent,
            PendingIntent.FLAG_MUTABLE,
        )
        builder.addAction(0, context.getString(R.string.disable), stopScanningPendingIntent)
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
