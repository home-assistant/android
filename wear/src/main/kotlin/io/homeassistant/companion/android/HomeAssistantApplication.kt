package io.homeassistant.companion.android

import android.app.Application
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Composer
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.core.content.ContextCompat
import dagger.hilt.android.HiltAndroidApp
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.KeyStoreRepositoryImpl
import io.homeassistant.companion.android.common.data.keychain.NamedKeyStore
import io.homeassistant.companion.android.common.sensors.AudioSensorManager
import io.homeassistant.companion.android.common.util.HAStrictMode
import io.homeassistant.companion.android.complications.ComplicationReceiver
import io.homeassistant.companion.android.sensors.SensorReceiver
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
open class HomeAssistantApplication : Application() {
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    @Inject
    @NamedKeyStore
    lateinit var keyStore: KeyChainRepository

    @OptIn(ExperimentalComposeRuntimeApi::class)
    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            BuildConfig.DEBUG &&
            !BuildConfig.NO_STRICT_MODE
        ) {
            HAStrictMode.enable()
        }

        // We should initialize the logger as early as possible in the lifecycle of the application
        Timber.plant(Timber.DebugTree())
        Timber.i("Running ${BuildConfig.VERSION_NAME} on SDK ${Build.VERSION.SDK_INT}")

        // Enable only for debug flavor to avoid perf regressions in release
        Composer.setDiagnosticStackTraceEnabled(BuildConfig.DEBUG)

        ioScope.launch {
            keyStore.load(applicationContext, KeyStoreRepositoryImpl.ALIAS)
        }

        val sensorReceiver = SensorReceiver()
        // This will cause the sensor to be updated every time the OS broadcasts that a cable was plugged/unplugged.
        // This should be nearly instantaneous allowing automations to fire immediately when a phone is plugged
        // in or unplugged. Updates will also be triggered when the system reports low battery and when it recovers.
        ContextCompat.registerReceiver(
            this,
            sensorReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )

        // This will trigger an update any time the wifi state has changed
        ContextCompat.registerReceiver(
            this,
            sensorReceiver,
            IntentFilter().apply {
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )

        // This will trigger for DND changes, including bedtime and theater mode
        ContextCompat.registerReceiver(
            this,
            sensorReceiver,
            IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )

        // Listen to changes to the audio input/output on the device
        ContextCompat.registerReceiver(
            this,
            sensorReceiver,
            IntentFilter().apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(AudioManager.ACTION_HEADSET_PLUG)
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                addAction(AudioSensorManager.VOLUME_CHANGED_ACTION)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )

        // Listen for microphone mute changes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ContextCompat.registerReceiver(
                this,
                sensorReceiver,
                IntentFilter(AudioManager.ACTION_MICROPHONE_MUTE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }

        // Listen for speakerphone state changes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.registerReceiver(
                this,
                sensorReceiver,
                IntentFilter(AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }

        // This will cause interactive and power save to update upon a state change
        ContextCompat.registerReceiver(
            this,
            sensorReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )

        ContextCompat.registerReceiver(
            this,
            sensorReceiver,
            IntentFilter().apply {
                addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )

        // Listen to changes to Wet Mode State
        ContextCompat.registerReceiver(
            this,
            sensorReceiver,
            IntentFilter().apply {
                addAction("com.google.android.clockwork.actions.WET_MODE_STARTED")
                addAction("com.google.android.clockwork.actions.WET_MODE_ENDED")
            },
            ContextCompat.RECEIVER_EXPORTED,
        )

        // Listen for bluetooth state changes
        ContextCompat.registerReceiver(
            this,
            sensorReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )

        // Listen for NFC state changes
        ContextCompat.registerReceiver(
            this,
            sensorReceiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )

        // Update complications when the screen is on
        val complicationReceiver = ComplicationReceiver()

        val screenIntentFilter = IntentFilter()
        screenIntentFilter.addAction(Intent.ACTION_SCREEN_ON)

        ContextCompat.registerReceiver(
            this,
            complicationReceiver,
            screenIntentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
