package io.homeassistant.companion.android.assist.service

import android.Manifest
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import io.homeassistant.companion.android.assist.wakeword.WakeWordListener
import io.homeassistant.companion.android.assist.wakeword.WakeWordListenerFactory
import io.homeassistant.companion.android.settings.assist.AssistRepository
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.FakeClock
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowVoiceInteractionService

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AssistVoiceInteractionServiceTest {

    @get:Rule(order = 0)
    val consoleLogRule = ConsoleLogRule()

    @get:Rule(order = 1)
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    private val assistRepository: AssistRepository = mockk(relaxed = true)
    private val wakeWordListener: WakeWordListener = mockk(relaxed = true)
    private val onWakeWordDetectedSlot = slot<(MicroWakeWordModelConfig) -> Unit>()
    private val wakeWordListenerFactory: WakeWordListenerFactory = mockk {
        every { create(capture(onWakeWordDetectedSlot), any(), any()) } returns wakeWordListener
    }
    private val clock = FakeClock()

    private lateinit var serviceController: ServiceController<AssistVoiceInteractionService>
    private lateinit var service: AssistVoiceInteractionService

    private val testModels = listOf(
        MicroWakeWordModelConfig(
            wakeWord = "Okay Nabu",
            author = "test",
            website = "https://test.com",
            model = "okay_nabu.tflite",
            trainedLanguages = listOf("en"),
            version = 1,
            micro = MicroWakeWordModelConfig.MicroFrontendConfig(
                probabilityCutoff = 0.5f,
                featureStepSize = 10,
                slidingWindowSize = 20,
            ),
        ),
        MicroWakeWordModelConfig(
            wakeWord = "Hey Jarvis",
            author = "test",
            website = "https://test.com",
            model = "hey_jarvis.tflite",
            trainedLanguages = listOf("en"),
            version = 1,
            micro = MicroWakeWordModelConfig.MicroFrontendConfig(
                probabilityCutoff = 0.5f,
                featureStepSize = 10,
                slidingWindowSize = 20,
            ),
        ),
    )

    @Before
    fun setUp() {
        coEvery { assistRepository.getAvailableModels() } returns testModels

        serviceController = Robolectric.buildService(AssistVoiceInteractionService::class.java)
        service = serviceController.get()

        // Inject mocks
        service.assistRepository = assistRepository
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
        coEvery { assistRepository.isWakeWordEnabled() } returns true
        coEvery { assistRepository.getSelectedWakeWord() } returns "Okay Nabu"

        service.onReady()
        advanceUntilIdle()

        coVerify { wakeWordListener.start(any(), testModels[0]) }
    }

    @Test
    fun `Given wake word disabled when onReady then do not start listening`() = runTest {
        coEvery { assistRepository.isWakeWordEnabled() } returns false

        service.onReady()
        advanceUntilIdle()

        coVerify(exactly = 0) { wakeWordListener.start(any(), any()) }
    }

    @Test
    fun `Given START_LISTENING action when onStartCommand then start listening`() = runTest {
        coEvery { assistRepository.getSelectedWakeWord() } returns "Okay Nabu"
        val intent = Intent().apply {
            action = "io.homeassistant.companion.android.START_LISTENING"
        }

        service.onStartCommand(intent, 0, 1)
        advanceUntilIdle()

        coVerify { wakeWordListener.start(any(), testModels[0]) }
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
        coEvery { assistRepository.getSelectedWakeWord() } returns testModels[1].wakeWord
        val intent = Intent().apply {
            action = "io.homeassistant.companion.android.START_LISTENING"
        }

        service.onStartCommand(intent, 0, 1)
        advanceUntilIdle()

        coVerify { wakeWordListener.start(any(), testModels[1]) }
    }

    @Test
    fun `Given no wake word selected when starting then use first model`() = runTest {
        coEvery { assistRepository.getSelectedWakeWord() } returns null
        val intent = Intent().apply {
            action = "io.homeassistant.companion.android.START_LISTENING"
        }

        service.onStartCommand(intent, 0, 1)
        advanceUntilIdle()

        coVerify { wakeWordListener.start(any(), testModels[0]) }
    }

    @Test
    fun `Given unknown wake word selected when starting then use first model`() = runTest {
        coEvery { assistRepository.getSelectedWakeWord() } returns "Unknown"
        val intent = Intent().apply {
            action = "io.homeassistant.companion.android.START_LISTENING"
        }

        service.onStartCommand(intent, 0, 1)
        advanceUntilIdle()

        coVerify { wakeWordListener.start(any(), testModels[0]) }
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
        coEvery { assistRepository.getSelectedWakeWord() } returns testModels[0].wakeWord

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
        onWakeWordDetectedSlot.captured.invoke(testModels[0])
        val firstBundle = shadow.lastSessionBundle
        assertNotNull(firstBundle)
    }

    @Test
    fun `Given wake word detected twice quickly when callback invoked then debounce`() = runTest {
        val shadow = Shadows.shadowOf(service) as ShadowVoiceInteractionService
        clock.currentInstant = Instant.fromEpochMilliseconds(0)
        coEvery { assistRepository.getSelectedWakeWord() } returns testModels[0].wakeWord

        // Call onReady() to make showSession() available (isWakeWordEnabled defaults to false)
        service.onReady()
        advanceUntilIdle()

        val intent = Intent().apply {
            action = "io.homeassistant.companion.android.START_LISTENING"
        }
        service.onStartCommand(intent, 0, 1)
        advanceUntilIdle()

        // First detection - should trigger showSession
        onWakeWordDetectedSlot.captured.invoke(testModels[0])
        val firstBundle = shadow.lastSessionBundle
        assertNotNull(firstBundle)

        // Second detection 2 seconds later (within 3 second debounce) - should be ignored
        clock.currentInstant = Instant.fromEpochMilliseconds(2000)
        onWakeWordDetectedSlot.captured.invoke(testModels[0])
        assertSame(firstBundle, shadow.lastSessionBundle)

        // Third detection 4 seconds after first (after debounce) - should trigger showSession
        clock.currentInstant = Instant.fromEpochMilliseconds(4000)
        onWakeWordDetectedSlot.captured.invoke(testModels[0])
        assertNotSame(firstBundle, shadow.lastSessionBundle)
    }
}
