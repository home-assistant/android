package io.homeassistant.companion.android.sensors

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.connection.CarConnection
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarAppNotificationBroadcastReceiver
import androidx.car.app.notification.CarNotificationManager
import androidx.car.app.notification.CarPendingIntent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.Observer
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.vehicle.HaCarAppService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import io.homeassistant.companion.android.common.R as commonR


class AndroidAutoSensorManager : SensorManager, Observer<Int> {

    companion object {

        internal const val TAG = "AndroidAutoSM"

        private val androidAutoConnected = SensorManager.BasicSensor(
            "android_auto",
            "binary_sensor",
            commonR.string.basic_sensor_name_android_auto,
            commonR.string.sensor_description_android_auto,
            "mdi:car",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
    }

    override val name: Int
        get() = commonR.string.sensor_name_android_auto

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            listOf(androidAutoConnected)
        } else {
            emptyList()
        }
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    private lateinit var context: Context
    private var carConnection: CarConnection? = null

    override fun requestSensorUpdate(context: Context) {
        this.context = context
        if (!isEnabled(context, androidAutoConnected)) {
            return
        }
        CoroutineScope(Dispatchers.Main + Job()).launch {
            if (carConnection == null) {
                carConnection = CarConnection(context)
            }
            carConnection?.type?.observeForever(this@AndroidAutoSensorManager)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onChanged(type: Int?) {
        if (!isEnabled(context, androidAutoConnected)) {
            CoroutineScope(Dispatchers.Main + Job()).launch {
                carConnection?.type?.removeObserver(this@AndroidAutoSensorManager)
            }
            return
        }
        val (connected, typeString) = when (type) {
            CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> {
                false to "Disconnected"
            }
            CarConnection.CONNECTION_TYPE_PROJECTION -> {
                true to "Projection"
            }
            CarConnection.CONNECTION_TYPE_NATIVE -> {
                true to "Native"
            }
            else -> {
                false to "Unknown($type)"
            }
        }

        if(connected) {
            startNotif()
        }

        onSensorUpdated(
            context,
            androidAutoConnected,
            connected,
            androidAutoConnected.statelessIcon,
            mapOf(
                "connection_type" to typeString
            )
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startNotif() {
        val manager = CarNotificationManager.from(context)

        val channelID = "HA_AA_OPEN"
        val chan = NotificationChannelCompat.Builder(channelID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Open Android Auto App")
            .build()
        manager.createNotificationChannel(chan)

        val intent = Intent(Intent.ACTION_VIEW)
            .setComponent(ComponentName(context, HaCarAppService::class.java))

        val notification = NotificationCompat.Builder(context, channelID)
            .setContentTitle("Open Home Assistant app to enable CarInfo sensors")
            .setSmallIcon(io.homeassistant.companion.android.common.R.drawable.ic_stat_ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .extend(
                CarAppExtender.Builder()
                    .setContentIntent(CarPendingIntent.getCarApp(context, intent.hashCode(), intent, 0))
                    .build()
            )
        manager.notify(-1, notification)
    }
}
