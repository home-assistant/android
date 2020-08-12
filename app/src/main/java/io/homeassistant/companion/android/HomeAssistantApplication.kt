package io.homeassistant.companion.android

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.Graph
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.sensors.ChargingBroadcastReceiver
import io.homeassistant.companion.android.sensors.PhoneStateReceiver
import io.homeassistant.companion.android.sensors.WifiStateReceiver

open class HomeAssistantApplication : Application(), GraphComponentAccessor {

    lateinit var graph: Graph

    override fun onCreate() {
        super.onCreate()

        graph = Graph(this, 0)

        // This will cause the sensor to be updated every time the OS broadcasts that a cable was plugged/unplugged.
        // This should be nearly instantaneous allowing automations to fire immediately when a phone is plugged
        // in or unplugged. Updates will also be triggered when the system reports low battery and when it recovers.
        registerReceiver(
            ChargingBroadcastReceiver(appComponent.integrationUseCase()), IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
        )

        // This will trigger an update any time the wifi state has changed
        registerReceiver(WifiStateReceiver(), IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION))

        // This will cause the phone state sensor to be updated every time the OS broadcasts that a call triggered.
        registerReceiver(
            PhoneStateReceiver(appComponent.integrationUseCase()), IntentFilter().apply {
                addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            }
        )
    }

    override val appComponent: AppComponent
        get() = graph.appComponent
}
