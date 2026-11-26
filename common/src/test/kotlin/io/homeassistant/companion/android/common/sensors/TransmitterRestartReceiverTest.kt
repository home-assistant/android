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
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Covers [TransmitterRestartReceiver]: only [Intent.ACTION_BOOT_COMPLETED] and
 * [Intent.ACTION_MY_PACKAGE_REPLACED] restart the BLE transmitter, and the restart is a local
 * operation delegated to [BluetoothSensorManager.restartTransmitterIfNeeded].
 *
 * The receiver is exercised through a real Robolectric broadcast dispatch so a genuine
 * [android.content.BroadcastReceiver.PendingResult] is created for it, exactly as the system does.
 * The receiver runs on a [StandardTestDispatcher] so the test controls when its coroutine runs.
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
    fun `Given a boot completed broadcast when received then the transmitter is restarted`() = runTest {
        val restarted = CompletableDeferred<Unit>()
        coEvery { bluetoothSensorManager.restartTransmitterIfNeeded() } coAnswers {
            restarted.complete(Unit)
        }

        val receiver = TransmitterRestartReceiver(StandardTestDispatcher(testScheduler))
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BOOT_COMPLETED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            context.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED))
            shadowOf(getMainLooper()).idle()
            advanceUntilIdle()
            withTimeout(10.seconds) { restarted.await() }

            coVerify(exactly = 1) { bluetoothSensorManager.restartTransmitterIfNeeded() }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    @Test
    fun `Given a package replaced broadcast when received then the transmitter is restarted`() = runTest {
        val restarted = CompletableDeferred<Unit>()
        coEvery { bluetoothSensorManager.restartTransmitterIfNeeded() } coAnswers {
            restarted.complete(Unit)
        }

        val receiver = TransmitterRestartReceiver(StandardTestDispatcher(testScheduler))
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_MY_PACKAGE_REPLACED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            context.sendBroadcast(Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
            shadowOf(getMainLooper()).idle()
            advanceUntilIdle()
            withTimeout(10.seconds) { restarted.await() }

            coVerify(exactly = 1) { bluetoothSensorManager.restartTransmitterIfNeeded() }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    @Test
    fun `Given a restart that suspends when the broadcast is received then the restart is awaited before finishing`() = runTest {
        val restartStarted = CompletableDeferred<Unit>()
        val restartGate = CompletableDeferred<Unit>()
        coEvery { bluetoothSensorManager.restartTransmitterIfNeeded() } coAnswers {
            restartStarted.complete(Unit)
            restartGate.await()
        }

        val receiver = TransmitterRestartReceiver(StandardTestDispatcher(testScheduler))
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BOOT_COMPLETED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            context.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED))
            shadowOf(getMainLooper()).idle()
            // Run the receiver's coroutine up to its suspension point without advancing
            // virtual time past the broadcast timeout (advanceUntilIdle would fire it).
            runCurrent()

            // The restart was invoked and is still suspended: the receiver's coroutine
            // (and therefore the broadcast pending result) must not have finished yet.
            withTimeout(10.seconds) { restartStarted.await() }

            // Now let the restart complete and confirm it finishes.
            restartGate.complete(Unit)
            advanceUntilIdle()

            coVerify(exactly = 1) { bluetoothSensorManager.restartTransmitterIfNeeded() }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    @Test
    fun `Given an unrelated broadcast when received then the transmitter is not restarted`() {
        val receiver = TransmitterRestartReceiver(StandardTestDispatcher())
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_TIME_TICK),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            context.sendBroadcast(Intent(Intent.ACTION_TIME_TICK))
            shadowOf(getMainLooper()).idle()

            coVerify(exactly = 0) { bluetoothSensorManager.restartTransmitterIfNeeded() }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
}
