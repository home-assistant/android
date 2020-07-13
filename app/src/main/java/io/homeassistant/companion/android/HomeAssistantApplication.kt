package io.homeassistant.companion.android

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import com.jakewharton.threetenabp.AndroidThreeTen
import com.lokalise.sdk.Lokalise
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.Graph
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.sensors.ChargingBroadcastReceiver

class HomeAssistantApplication : Application(), GraphComponentAccessor {

    lateinit var graph: Graph

    override fun onCreate() {
        super.onCreate()

        Lokalise.init(this, "16ff9dee3da7a3cba0d998a4e58fa99e92ba089d", "145814835dd655bc5ab0d0.36753359")
        Lokalise.updateTranslations()

        AndroidThreeTen.init(this)
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
    }

    override val appComponent: AppComponent
        get() = graph.appComponent
}
