package io.homeassistant.companion.android

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.Graph
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.sensors.ChargingBroadcastReceiver
import io.homeassistant.companion.android.sensors.WifiStateReceiver

open class HomeAssistantApplication : Application(), GraphComponentAccessor {

    lateinit var graph: Graph

    override fun onCreate() {
        super.onCreate()

        graph = Graph(this, 0)

        // This will cause the sensor to be updated every time the OS broadcasts that a cable was plugged/unplugged.
        // This should be nearly instantaneous allowing automations to fire immediately when a phone is plugged
        // in or unplugged.
        registerReceiver(
            ChargingBroadcastReceiver(appComponent.integrationUseCase()), IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
        )

        // This will trigger an update any time the wifi state has changed
        registerReceiver(WifiStateReceiver(), IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION))
    }

    override val appComponent: AppComponent
        get() = graph.appComponent
}
