package io.homeassistant.companion.android.assist.service

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.assist.service.AssistVoiceInteractionService.Companion.ACTION_RESUME_LISTENING
import io.homeassistant.companion.android.assist.service.AssistVoiceInteractionService.Companion.ACTION_START_LISTENING
import io.homeassistant.companion.android.assist.service.AssistVoiceInteractionService.Companion.ACTION_STOP_LISTENING
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import io.homeassistant.companion.android.assist.wakeword.WakeWordListener
import io.homeassistant.companion.android.assist.wakeword.WakeWordListenerFactory
import io.homeassistant.companion.android.settings.assist.AssistConfigManager
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.FakeClock
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.homeassistant.companion.android.util.microWakeWordModelConfigs
import io.mockk.Ordering
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertNull
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVoiceInteractionService

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AssistVoiceInteractionServiceTest {

    @get:Rule(order = 0)
    val consoleLogRule = ConsoleLogRule()

    @get:Rule(order = 1)
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    private val assistConfigManager: AssistConfigManager = mockk(relaxed = true)
    private val wakeWordListener: WakeWordListener = mockk(relaxed = true)
    private val onWakeWordDetectedSlot = slot<(MicroWakeWordModelConfig) -> Unit>()
    private val onListenerFailureSlot = slot<() -> Unit>()
    private val wakeWordListenerFactory: WakeWordListenerFactory = mockk {
        every { create(capture(onWakeWordDetectedSlot), any(), any(), capture(onListenerFailureSlot)) } returns wakeWordListener
    }
    private val clock = FakeClock()

    private lateinit var serviceController: ServiceController<AssistVoiceInteractionService>
    private lateinit var service: AssistVoiceInteractionService

    @Before
    fun setUp() {
        every { assistConfigManager.isWakeWordSupported() } returns true
        coEvery { assistConfigManager.getAvailableModels() } returns microWakeWordModelConfigs

        serviceController = Robolectric.buildService(AssistVoiceInteractionService::class.java)
        service = serviceController.get()

        // Inject mocks
        service.assistConfigManager = assistConfigManager
        service.wakeWordListenerFactory = wakeWordListenerFactory
        service.clock = clock

        // Grant audio permission
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.RECORD_AUDIO)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Sends a command intent via [onStartCommand], exercising the same internal
     * methods that the broadcast-based companion methods trigger in production.
     */
    private fun sendCommand(action: String) {
        service.onStartCommand(
            Intent().apply { this.action = action },
            0,
            1,
        )
    }

    @Test
    fun `Given service when onReady then register command receiver for all actions`() = runTest {
        service.onReady()
        advanceUntilIdle()

        val registeredActions = getRegisteredReceiverActions()
        assertTrue(ACTION_START_LISTENING in registeredActions)
        assertTrue(ACTION_STOP_LISTENING in registeredActions)
        assertTrue(ACTION_RESUME_LISTENING in registeredActions)
    }

    @Test
    fun `Given service ready when onShutdown then unregister command receiver`() = runTest {
        service.onReady()
        advanceUntilIdle()

        service.onShutdown()

        val registeredActions = getRegisteredReceiverActions()
        assertTrue(ACTION_START_LISTENING !in registeredActions)
        assertTrue(ACTION_STOP_LISTENING !in registeredActions)
        assertTrue(ACTION_RESUME_LISTENING !in registeredActions)
    }

    @Test
    fun `Given wake word enabled when onReady then start listening`() = runTest {
        coEvery { assistConfigManager.isWakeWordEnabled() } returns true
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns microWakeWordModelConfigs[0]

        service.onReady()
        advanceUntilIdle()

        coVerify { wakeWordListener.start(any(), microWakeWordModelConfigs[0]) }
    }

    @Test
    fun `Given wake word disabled when onReady then do not start listening`() = runTest {
        coEvery { assistConfigManager.isWakeWordEnabled() } returns false

        service.onReady()
        advanceUntilIdle()

        coVerify(exactly = 0) { wakeWordListener.start(any(), any()) }
    }

    @Test
    fun `Given unsupported device when onReady then do not start listening`() = runTest {
        every { assistConfigManager.isWakeWordSupported() } returns false
        coEvery { assistConfigManager.isWakeWordEnabled() } returns true

        service.onReady()
        advanceUntilIdle()

        coVerify(exactly = 0) { wakeWordListener.start(any(), any()) }
        coVerify(ordering = Ordering.ORDERED) {
            assistConfigManager.isWakeWordEnabled()
            assistConfigManager.isWakeWordSupported()
        }
    }

    @Test
    fun `Given START_LISTENING command then start listening`() = runTest {
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns microWakeWordModelConfigs[0]

        sendCommand(ACTION_START_LISTENING)
        advanceUntilIdle()

        coVerify { wakeWordListener.start(any(), microWakeWordModelConfigs[0]) }
    }

    @Test
    fun `Given STOP_LISTENING command then stop listening`() = runTest {
        sendCommand(ACTION_STOP_LISTENING)
        advanceUntilIdle()

        coVerify { wakeWordListener.stop() }
    }

    @Test
    fun `Given null intent when onStartCommand then do nothing`() = runTest {
        service.onStartCommand(null, 0, 1)
        advanceUntilIdle()

        coVerify(exactly = 0) { wakeWordListener.start(any(), any()) }
        coVerify(exactly = 0) { wakeWordListener.stop() }
    }

    @Test
    fun `Given selected wake word exists when starting then use selected model`() = runTest {
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns microWakeWordModelConfigs[1]

        sendCommand(ACTION_START_LISTENING)
        advanceUntilIdle()

        coVerify { wakeWordListener.start(any(), microWakeWordModelConfigs[1]) }
    }

    @Test
    fun `Given no wake word selected when starting then use first model`() = runTest {
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns null

        sendCommand(ACTION_START_LISTENING)
        advanceUntilIdle()

        coVerify { wakeWordListener.start(any(), microWakeWordModelConfigs[0]) }
    }

    @Test
    fun `Given unknown wake word selected when starting then use first model`() = runTest {
        // When an unknown wake word is stored, getSelectedWakeWordModel returns null
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns null

        sendCommand(ACTION_START_LISTENING)
        advanceUntilIdle()

        coVerify { wakeWordListener.start(any(), microWakeWordModelConfigs[0]) }
    }

    @Test
    fun `Given no RECORD_AUDIO permission when start listening then do not start`() = runTest {
        // Revoke permission
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .denyPermissions(Manifest.permission.RECORD_AUDIO)

        sendCommand(ACTION_START_LISTENING)
        advanceUntilIdle()

        coVerify(exactly = 0) { wakeWordListener.start(any(), any()) }
    }

    @Test
    fun `Given wake word listening initialization failure when start listening then failure callback disables wake word`() = runTest {
        coEvery { wakeWordListener.start(any(), any()) } coAnswers {
            // Simulate a failure during initialization calling the failure callback
            onListenerFailureSlot.captured.invoke()
        }

        sendCommand(ACTION_START_LISTENING)
        advanceUntilIdle()

        coVerify(exactly = 1) { assistConfigManager.setWakeWordEnabled(false) }
    }

    @Test
    fun `Given RESUME_LISTENING command and wake word enabled then start listening`() = runTest {
        coEvery { assistConfigManager.isWakeWordEnabled() } returns true
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns microWakeWordModelConfigs[0]

        sendCommand(ACTION_RESUME_LISTENING)
        advanceUntilIdle()

        coVerify { wakeWordListener.start(any(), microWakeWordModelConfigs[0]) }
    }

    @Test
    fun `Given RESUME_LISTENING command and wake word disabled then do not start listening`() = runTest {
        coEvery { assistConfigManager.isWakeWordEnabled() } returns false

        sendCommand(ACTION_RESUME_LISTENING)
        advanceUntilIdle()

        coVerify(exactly = 0) { wakeWordListener.start(any(), any()) }
    }

    @Test
    fun `Given wake word detected when callback invoked then stop listener and show session`() = runTest {
        val shadow = Shadows.shadowOf(service) as ShadowVoiceInteractionService
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns microWakeWordModelConfigs[0]

        // Call onReady() to make showSession() available (isWakeWordEnabled defaults to false)
        service.onReady()
        advanceUntilIdle()

        sendCommand(ACTION_START_LISTENING)
        advanceUntilIdle()

        assertNull(shadow.lastSessionBundle)

        // Simulate wake word detection - callback should execute without exception
        onWakeWordDetectedSlot.captured.invoke(microWakeWordModelConfigs[0])
        advanceUntilIdle()

        coVerify { wakeWordListener.stop() }
        val firstBundle = shadow.lastSessionBundle
        assertNotNull(firstBundle)
    }

    @Test
    fun `Given wake word detected twice quickly when callback invoked then debounce`() = runTest {
        val shadow = Shadows.shadowOf(service) as ShadowVoiceInteractionService
        clock.currentInstant = Instant.fromEpochMilliseconds(0)
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns microWakeWordModelConfigs[0]

        // Call onReady() to make showSession() available (isWakeWordEnabled defaults to false)
        service.onReady()
        advanceUntilIdle()

        sendCommand(ACTION_START_LISTENING)
        advanceUntilIdle()

        // First detection - should trigger showSession
        onWakeWordDetectedSlot.captured.invoke(microWakeWordModelConfigs[0])
        advanceUntilIdle()
        val firstBundle = shadow.lastSessionBundle
        assertNotNull(firstBundle)

        // Second detection 2 seconds later (within 3 second debounce) - should be ignored
        clock.currentInstant = Instant.fromEpochMilliseconds(2000)
        onWakeWordDetectedSlot.captured.invoke(microWakeWordModelConfigs[0])
        advanceUntilIdle()
        assertSame(firstBundle, shadow.lastSessionBundle)

        // Third detection 4 seconds after first (after debounce) - should trigger showSession
        clock.currentInstant = Instant.fromEpochMilliseconds(4000)
        onWakeWordDetectedSlot.captured.invoke(microWakeWordModelConfigs[0])
        advanceUntilIdle()
        assertNotSame(firstBundle, shadow.lastSessionBundle)
    }

    @Test
    fun `Given wake word detected when callback invoked then send broadcast`() = runTest {
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns microWakeWordModelConfigs[0]

        service.onReady()
        advanceUntilIdle()

        sendCommand(ACTION_START_LISTENING)
        advanceUntilIdle()

        onWakeWordDetectedSlot.captured.invoke(microWakeWordModelConfigs[0])
        advanceUntilIdle()

        val broadcastIntents = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .broadcastIntents
        val wakeWordBroadcast = broadcastIntents.find {
            it.action == "io.homeassistant.companion.android.WAKE_WORD_DETECTED"
        }
        assertNotNull(wakeWordBroadcast)
        assertEquals(service.packageName, wakeWordBroadcast!!.`package`)
    }

    @Test
    fun `Given wake word detected within debounce when callback invoked then still send broadcast`() = runTest {
        clock.currentInstant = Instant.fromEpochMilliseconds(0)
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns microWakeWordModelConfigs[0]

        service.onReady()
        advanceUntilIdle()

        sendCommand(ACTION_START_LISTENING)
        advanceUntilIdle()

        // First detection
        onWakeWordDetectedSlot.captured.invoke(microWakeWordModelConfigs[0])
        advanceUntilIdle()

        // Second detection within debounce (showSession suppressed, but broadcast should still fire)
        clock.currentInstant = Instant.fromEpochMilliseconds(2000)
        onWakeWordDetectedSlot.captured.invoke(microWakeWordModelConfigs[0])
        advanceUntilIdle()

        val broadcastIntents = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .broadcastIntents
        val wakeWordBroadcasts = broadcastIntents.filter {
            it.action == "io.homeassistant.companion.android.WAKE_WORD_DETECTED"
        }
        // Both detections should have sent a broadcast, even though the second was debounced
        assertEquals(2, wakeWordBroadcasts.size)
    }

    @Test
    fun `Given service not ready when wake word detected then do not show session`() = runTest {
        val shadow = Shadows.shadowOf(service) as ShadowVoiceInteractionService
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns microWakeWordModelConfigs[0]

        // Do NOT call onReady() - service is not ready
        sendCommand(ACTION_START_LISTENING)
        advanceUntilIdle()

        // Simulate wake word detection while service is not ready
        onWakeWordDetectedSlot.captured.invoke(microWakeWordModelConfigs[0])
        advanceUntilIdle()

        // showSession should NOT have been called
        assertNull(shadow.lastSessionBundle)
        // But the listener should still have been stopped to release the microphone
        coVerify { wakeWordListener.stop() }
    }

    @Test
    fun `Given service shut down after ready when wake word detected then do not show session`() = runTest {
        val shadow = Shadows.shadowOf(service) as ShadowVoiceInteractionService
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns microWakeWordModelConfigs[0]

        // Make service ready and start listening so the wake word callback is captured
        service.onReady()
        advanceUntilIdle()

        sendCommand(ACTION_START_LISTENING)
        advanceUntilIdle()

        // Simulate a shutdown that races with the wake-word coroutine: when
        // stop() is called inside the launched coroutine, the service shuts down
        // concurrently (setting isServiceReady = false). Because launchAssist()
        // runs synchronously after stop() returns, only the isServiceReady guard
        // prevents showSession() from being called.
        coEvery { wakeWordListener.stop() } coAnswers {
            service.onShutdown()
        }

        // Invoke the wake word callback to queue the coroutine, then advance
        onWakeWordDetectedSlot.captured.invoke(microWakeWordModelConfigs[0])
        advanceUntilIdle()

        // showSession should NOT have been called because the service is no longer ready
        assertNull(shadow.lastSessionBundle)
        coVerify { wakeWordListener.stop() }
    }

    @Test
    fun `Given context when startListening then send START_LISTENING broadcast with package`() {
        assertCommand(ACTION_START_LISTENING, AssistVoiceInteractionService::startListening)
    }

    @Test
    fun `Given context when stopListening then send STOP_LISTENING broadcast with package`() {
        assertCommand(ACTION_STOP_LISTENING, AssistVoiceInteractionService::stopListening)
    }

    @Test
    fun `Given context when resumeListening then send RESUME_LISTENING broadcast with package`() {
        assertCommand(ACTION_RESUME_LISTENING, AssistVoiceInteractionService::resumeListening)
    }

    private fun getRegisteredReceiverActions(): Set<String> =
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .registeredReceivers
            .flatMap { wrapper ->
                (0 until wrapper.intentFilter.countActions()).map { wrapper.intentFilter.getAction(it) }
            }
            .toSet()

    private fun assertCommand(action: String, command: (Context) -> Unit) {
        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "io.homeassistant.companion.android"
        val intentSlot = slot<Intent>()

        command(context)

        verify { context.sendBroadcast(capture(intentSlot)) }
        assertEquals(action, intentSlot.captured.action)
        assertEquals("io.homeassistant.companion.android", intentSlot.captured.`package`)
    }
}
