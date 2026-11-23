package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper.getMainLooper
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/**
 * Covers [TransmitterRestartReceiver]: only [Intent.ACTION_BOOT_COMPLETED] and
 * [Intent.ACTION_MY_PACKAGE_REPLACED] restart the BLE transmitter, and the restart is a local
 * operation (no server sync) delegated to [BluetoothSensorManager.restartTransmitter].
 *
 * The receiver is exercised through a real Robolectric broadcast dispatch so a genuine
 * [android.content.BroadcastReceiver.PendingResult] is created for it, exactly as the system does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class TransmitterRestartReceiverTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val bluetoothSensorManager: BluetoothSensorManager = mockk(relaxed = true)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        hilt.inject()
    }

    @Test
    fun `Given a boot completed broadcast when received then the transmitter is restarted`() {
        val restarted = CompletableDeferred<Unit>()
        coEvery { bluetoothSensorManager.restartTransmitter() } coAnswers {
            restarted.complete(Unit)
            true
        }

        val receiver = TransmitterRestartReceiver()
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BOOT_COMPLETED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            context.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED))
            shadowOf(getMainLooper()).idle()
            runBlocking { withTimeout(10.seconds) { restarted.await() } }

            coVerify(exactly = 1) { bluetoothSensorManager.restartTransmitter() }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    @Test
    fun `Given a package replaced broadcast when received then the transmitter is restarted`() {
        val restarted = CompletableDeferred<Unit>()
        coEvery { bluetoothSensorManager.restartTransmitter() } coAnswers {
            restarted.complete(Unit)
            true
        }

        val receiver = TransmitterRestartReceiver()
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_MY_PACKAGE_REPLACED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            context.sendBroadcast(Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
            shadowOf(getMainLooper()).idle()
            runBlocking { withTimeout(10.seconds) { restarted.await() } }

            coVerify(exactly = 1) { bluetoothSensorManager.restartTransmitter() }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    @Test
    fun `Given an unrelated broadcast when received then the transmitter is not restarted`() {
        val receiver = TransmitterRestartReceiver()
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_TIME_TICK),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            context.sendBroadcast(Intent(Intent.ACTION_TIME_TICK))
            shadowOf(getMainLooper()).idle()

            coVerify(exactly = 0) { bluetoothSensorManager.restartTransmitter() }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
}
