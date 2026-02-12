package io.homeassistant.companion.android.assist.service

import android.Manifest
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import io.homeassistant.companion.android.assist.wakeword.WakeWordListener
import io.homeassistant.companion.android.assist.wakeword.WakeWordListenerFactory
import io.homeassistant.companion.android.settings.assist.AssistConfigManager
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.FakeClock
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.homeassistant.companion.android.util.microWakeWordModelConfigs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertNull
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ServiceController
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
    private val wakeWordListenerFactory: WakeWordListenerFactory = mockk {
        every { create(capture(onWakeWordDetectedSlot), any(), any()) } returns wakeWordListener
    }
    private val clock = FakeClock()

    private lateinit var serviceController: ServiceController<AssistVoiceInteractionService>
    private lateinit var service: AssistVoiceInteractionService

    @Before
    fun setUp() {
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
    fun `Given START_LISTENING action when onStartCommand then start listening`() = runTest {
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns microWakeWordModelConfigs[0]
        val intent = Intent().apply {
            action = "io.homeassistant.companion.android.START_LISTENING"
        }

        service.onStartCommand(intent, 0, 1)
        advanceUntilIdle()

        coVerify { wakeWordListener.start(any(), microWakeWordModelConfigs[0]) }
    }

    @Test
    fun `Given STOP_LISTENING action when onStartCommand then stop listening`() = runTest {
        val intent = Intent().apply {
            action = "io.homeassistant.companion.android.STOP_LISTENING"
        }

        service.onStartCommand(intent, 0, 1)
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
        val intent = Intent().apply {
            action = "io.homeassistant.companion.android.START_LISTENING"
        }

        service.onStartCommand(intent, 0, 1)
        advanceUntilIdle()

        coVerify { wakeWordListener.start(any(), microWakeWordModelConfigs[1]) }
    }

    @Test
    fun `Given no wake word selected when starting then use first model`() = runTest {
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns null
        val intent = Intent().apply {
            action = "io.homeassistant.companion.android.START_LISTENING"
        }

        service.onStartCommand(intent, 0, 1)
        advanceUntilIdle()

        coVerify { wakeWordListener.start(any(), microWakeWordModelConfigs[0]) }
    }

    @Test
    fun `Given unknown wake word selected when starting then use first model`() = runTest {
        // When an unknown wake word is stored, getSelectedWakeWordModel returns null
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns null
        val intent = Intent().apply {
            action = "io.homeassistant.companion.android.START_LISTENING"
        }

        service.onStartCommand(intent, 0, 1)
        advanceUntilIdle()

        coVerify { wakeWordListener.start(any(), microWakeWordModelConfigs[0]) }
    }

    @Test
    fun `Given no RECORD_AUDIO permission when start listening then do not start`() = runTest {
        // Revoke permission
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .denyPermissions(Manifest.permission.RECORD_AUDIO)

        val intent = Intent().apply {
            action = "io.homeassistant.companion.android.START_LISTENING"
        }

        service.onStartCommand(intent, 0, 1)
        advanceUntilIdle()

        coVerify(exactly = 0) { wakeWordListener.start(any(), any()) }
    }

    @Test
    fun `Given wake word detected when callback invoked then callback executes`() = runTest {
        val shadow = Shadows.shadowOf(service) as ShadowVoiceInteractionService
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns microWakeWordModelConfigs[0]

        // Call onReady() to make showSession() available (isWakeWordEnabled defaults to false)
        service.onReady()
        advanceUntilIdle()

        val intent = Intent().apply {
            action = "io.homeassistant.companion.android.START_LISTENING"
        }
        service.onStartCommand(intent, 0, 1)
        advanceUntilIdle()

        assertNull(shadow.lastSessionBundle)

        // Simulate wake word detection - callback should execute without exception
        onWakeWordDetectedSlot.captured.invoke(microWakeWordModelConfigs[0])
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

        val intent = Intent().apply {
            action = "io.homeassistant.companion.android.START_LISTENING"
        }
        service.onStartCommand(intent, 0, 1)
        advanceUntilIdle()

        // First detection - should trigger showSession
        onWakeWordDetectedSlot.captured.invoke(microWakeWordModelConfigs[0])
        val firstBundle = shadow.lastSessionBundle
        assertNotNull(firstBundle)

        // Second detection 2 seconds later (within 3 second debounce) - should be ignored
        clock.currentInstant = Instant.fromEpochMilliseconds(2000)
        onWakeWordDetectedSlot.captured.invoke(microWakeWordModelConfigs[0])
        assertSame(firstBundle, shadow.lastSessionBundle)

        // Third detection 4 seconds after first (after debounce) - should trigger showSession
        clock.currentInstant = Instant.fromEpochMilliseconds(4000)
        onWakeWordDetectedSlot.captured.invoke(microWakeWordModelConfigs[0])
        assertNotSame(firstBundle, shadow.lastSessionBundle)
    }
}
