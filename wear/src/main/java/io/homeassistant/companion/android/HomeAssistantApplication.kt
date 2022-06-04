package io.homeassistant.companion.android

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import dagger.hilt.android.HiltAndroidApp
import io.homeassistant.companion.android.complications.ComplicationReceiver
import io.homeassistant.companion.android.sensors.SensorReceiver

@HiltAndroidApp
open class HomeAssistantApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val sensorReceiver = SensorReceiver()
        // This will cause the sensor to be updated every time the OS broadcasts that a cable was plugged/unplugged.
        // This should be nearly instantaneous allowing automations to fire immediately when a phone is plugged
        // in or unplugged. Updates will also be triggered when the system reports low battery and when it recovers.
        registerReceiver(
            sensorReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
        )

        // Update complications when the screen is on
        val complicationReceiver = ComplicationReceiver()

        val screenIntentFilter = IntentFilter()
        screenIntentFilter.addAction(Intent.ACTION_SCREEN_ON)

        registerReceiver(complicationReceiver, screenIntentFilter)
    }
}
