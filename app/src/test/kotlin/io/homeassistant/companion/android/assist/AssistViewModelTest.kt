package io.homeassistant.companion.android.assist

import android.app.Application
import android.content.pm.PackageManager
import io.homeassistant.companion.android.common.assist.AssistViewModelBase.AssistInputMode
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineListResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.common.util.AudioRecorder
import io.homeassistant.companion.android.common.util.AudioUrlPlayer
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class AssistViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherJUnit5Extension()

    private val serverManager: ServerManager = mockk(relaxed = true)
    private val audioRecorder: AudioRecorder = mockk(relaxed = true)
    private val audioUrlPlayer: AudioUrlPlayer = mockk(relaxed = true)
    private val application: Application = mockk(relaxed = true)
    private val webSocketRepository: WebSocketRepository = mockk(relaxed = true)
    private val integrationRepository: IntegrationRepository = mockk(relaxed = true)

    private lateinit var viewModel: AssistViewModel

    @BeforeEach
    fun setUp() {
        val packageManager = mockk<PackageManager>()
        every { application.packageManager } returns packageManager
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) } returns true

        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.webSocketRepository(any()) } returns webSocketRepository
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        coEvery { integrationRepository.isHomeAssistantVersionAtLeast(any(), any(), any()) } returns true
        coEvery { webSocketRepository.getConfig() } returns GetConfigResponse(
            latitude = 0.0,
            longitude = 0.0,
            elevation = 0.0,
            unitSystem = emptyMap(),
            locationName = "Test",
            timeZone = "UTC",
            components = listOf("assist_pipeline"),
            version = "2025.1.0",
        )
        coEvery { webSocketRepository.getAssistPipeline(anyNullable()) } returns AssistPipelineResponse(
            id = "test-pipeline",
            name = "Test Pipeline",
            language = "en",
            conversationEngine = "conversation",
            conversationLanguage = "en",
            sttEngine = "stt",
            sttLanguage = "en",
            ttsEngine = "tts",
            ttsLanguage = "en",
            ttsVoice = null,
        )
        coEvery { webSocketRepository.getAssistPipelines() } returns AssistPipelineListResponse(
            pipelines = listOf(),
            preferredPipeline = "test-pipeline",
        )
        coEvery { integrationRepository.getLastUsedPipelineId() } returns null
        coEvery { integrationRepository.getLastUsedPipelineSttSupport() } returns false
        coEvery { integrationRepository.setLastUsedPipeline(any(), any()) } returns Unit
        coEvery { serverManager.getServer(any<Int>()) } returns mockk(relaxed = true)
        coEvery { serverManager.servers() } returns listOf()
    }

    private fun createViewModel(): AssistViewModel {
        return AssistViewModel(
            serverManager = serverManager,
            audioRecorder = audioRecorder,
            audioUrlPlayer = audioUrlPlayer,
            application = application,
        )
    }

    /**
     * Initializes the ViewModel through [AssistViewModel.onCreate] and advances the coroutines
     * so the conversation contains the start message (non-placeholder). Sets input mode to
     * [AssistInputMode.VOICE_INACTIVE] via pipeline setup with STT support.
     *
     * Uses `startListening = null` to avoid overwriting the default `recorderAutoStart = true`,
     * which is required for the voice-capable pipeline to set [AssistInputMode.VOICE_INACTIVE].
     */
    private fun createAndInitialize(hasPermission: Boolean = false): AssistViewModel {
        val vm = createViewModel()
        vm.onCreate(
            hasPermission = hasPermission,
            serverId = null,
            pipelineId = null,
            startListening = null,
            wakeWordPhrase = null,
        )
        return vm
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Nested
    inner class InactivityTimerTest {

        @Test
        fun `Given voice inactive mode and non-placeholder message when 30s elapses then shouldFinish is true`() = runTest {
            viewModel = createAndInitialize()
            runCurrent()

            advanceTimeBy(30.seconds)
            runCurrent()

            assertTrue(viewModel.shouldFinish)
        }

        @Test
        fun `Given voice inactive mode and non-placeholder message when less than 30s elapses then shouldFinish is false`() = runTest {
            viewModel = createAndInitialize()
            runCurrent()

            advanceTimeBy(29.seconds)
            runCurrent()

            assertFalse(viewModel.shouldFinish)
        }

        @Test
        fun `Given text input mode when 30s elapses then shouldFinish is false`() = runTest {
            // Pipeline with no STT engine -> TEXT_ONLY mode
            coEvery { webSocketRepository.getAssistPipeline(anyNullable()) } returns AssistPipelineResponse(
                id = "test-pipeline",
                name = "Test Pipeline",
                language = "en",
                conversationEngine = "conversation",
                conversationLanguage = "en",
                sttEngine = null,
                sttLanguage = null,
                ttsEngine = null,
                ttsLanguage = null,
                ttsVoice = null,
            )
            viewModel = createAndInitialize()
            runCurrent()

            advanceTimeBy(60.seconds)
            runCurrent()

            assertFalse(viewModel.shouldFinish)
        }

        @Test
        fun `Given blocked input mode when 30s elapses then shouldFinish is false`() = runTest {
            coEvery { serverManager.isRegistered() } returns false
            viewModel = createAndInitialize()
            runCurrent()

            advanceTimeBy(60.seconds)
            runCurrent()

            assertFalse(viewModel.shouldFinish)
        }

        @Test
        fun `Given voice inactive mode when onPause is called then timer is cancelled`() = runTest {
            viewModel = createAndInitialize()
            runCurrent()

            advanceTimeBy(15.seconds)
            runCurrent()

            viewModel.onPause()
            runCurrent()

            advanceTimeBy(30.seconds)
            runCurrent()

            assertFalse(viewModel.shouldFinish)
        }

        @Test
        fun `Given voice inactive mode when onDestroy is called then timer is cancelled`() = runTest {
            viewModel = createAndInitialize()
            runCurrent()

            advanceTimeBy(15.seconds)
            runCurrent()

            viewModel.onDestroy()
            runCurrent()

            advanceTimeBy(30.seconds)
            runCurrent()

            assertFalse(viewModel.shouldFinish)
        }

        @Test
        fun `Given voice inactive mode when input mode changes to TEXT then timer is cancelled`() = runTest {
            viewModel = createAndInitialize()
            runCurrent()

            advanceTimeBy(15.seconds)
            runCurrent()

            viewModel.onChangeInput() // VOICE_INACTIVE -> TEXT
            runCurrent()

            advanceTimeBy(30.seconds)
            runCurrent()

            assertFalse(viewModel.shouldFinish)
        }

        @Test
        fun `Given TEXT mode when switching back to VOICE_INACTIVE then timer restarts`() = runTest {
            viewModel = createAndInitialize(hasPermission = true)
            runCurrent()

            viewModel.onChangeInput() // VOICE_INACTIVE -> TEXT
            runCurrent()

            advanceTimeBy(30.seconds)
            runCurrent()
            assertFalse(viewModel.shouldFinish)

            viewModel.onChangeInput() // TEXT -> VOICE_INACTIVE
            runCurrent()

            advanceTimeBy(30.seconds)
            runCurrent()
            assertTrue(viewModel.shouldFinish)
        }
    }
}
