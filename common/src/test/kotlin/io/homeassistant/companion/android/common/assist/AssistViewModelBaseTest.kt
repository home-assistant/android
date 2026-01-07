package io.homeassistant.companion.android.common.assist

import android.app.Application
import android.content.pm.PackageManager
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEventType
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineRunStart
import io.homeassistant.companion.android.common.util.AudioRecorder
import io.homeassistant.companion.android.common.util.AudioUrlPlayer
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class, MainDispatcherJUnit5Extension::class)
class AssistViewModelBaseTest {

    private lateinit var serverManager: ServerManager
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var audioUrlPlayer: AudioUrlPlayer
    private lateinit var application: Application
    private lateinit var webSocketRepository: WebSocketRepository
    private lateinit var audioBytesFlow: MutableSharedFlow<ByteArray>
    private lateinit var pipelineEventsFlow: MutableSharedFlow<AssistPipelineEvent>

    private lateinit var viewModel: TestAssistViewModel

    @BeforeEach
    fun setUp() {
        serverManager = mockk(relaxed = true)
        audioRecorder = mockk(relaxed = true)
        audioUrlPlayer = mockk(relaxed = true)
        application = mockk(relaxed = true)
        webSocketRepository = mockk(relaxed = true)

        audioBytesFlow = MutableSharedFlow(extraBufferCapacity = 10)
        pipelineEventsFlow = MutableSharedFlow(extraBufferCapacity = 10)

        val packageManager = mockk<PackageManager>()
        every { application.packageManager } returns packageManager
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) } returns true

        every { audioRecorder.audioBytes } returns audioBytesFlow
        every { audioRecorder.stopRecording() } just Runs
        coEvery { serverManager.webSocketRepository(any()) } returns webSocketRepository
        coEvery { webSocketRepository.sendVoiceData(any(), any()) } returns true
        coEvery {
            webSocketRepository.runAssistPipelineForVoice(any(), any(), any(), any())
        } returns pipelineEventsFlow

        viewModel = TestAssistViewModel(serverManager, audioRecorder, audioUrlPlayer, application)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given recorder setup When pipeline sends RUN_START and STT_START Then buffered audio is forwarded`() = runTest {
        val audioData1 = byteArrayOf(1, 2, 3)
        val audioData2 = byteArrayOf(4, 5, 6)
        val handlerId = 42

        // Setup recorder and start pipeline
        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()

        // Emit audio data (should be buffered)
        audioBytesFlow.emit(audioData1)
        audioBytesFlow.emit(audioData2)
        advanceUntilIdle()

        // No data should be sent yet
        coVerify(exactly = 0) { webSocketRepository.sendVoiceData(any(), any()) }

        // Send RUN_START event with handler ID
        pipelineEventsFlow.emit(createRunStartEvent(handlerId))
        advanceUntilIdle()

        // Still no data - waiting for STT_START
        coVerify(exactly = 0) { webSocketRepository.sendVoiceData(any(), any()) }

        // Send STT_START event
        pipelineEventsFlow.emit(createSttStartEvent())
        advanceUntilIdle()

        // Now buffered data should be forwarded
        coVerifyOrder {
            webSocketRepository.sendVoiceData(handlerId, audioData1)
            webSocketRepository.sendVoiceData(handlerId, audioData2)
        }
    }

    @Test
    fun `Given pipeline started When new audio arrives after STT_START Then it is forwarded immediately`() = runTest {
        val handlerId = 42
        val audioData = byteArrayOf(1, 2, 3)

        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()

        // Start pipeline with RUN_START and STT_START
        pipelineEventsFlow.emit(createRunStartEvent(handlerId))
        pipelineEventsFlow.emit(createSttStartEvent())
        advanceUntilIdle()

        // Emit new audio
        audioBytesFlow.emit(audioData)
        advanceUntilIdle()

        // Should be forwarded immediately
        coVerify { webSocketRepository.sendVoiceData(handlerId, audioData) }
    }

    @Test
    fun `Given active recording When stopRecording with sendRecorded true Then buffer is drained and empty byte array is sent`() = runTest {
        val handlerId = 42
        val audioData = byteArrayOf(1, 2, 3)

        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()

        // Buffer some data
        audioBytesFlow.emit(audioData)
        advanceUntilIdle()

        // Start STT via pipeline events
        pipelineEventsFlow.emit(createRunStartEvent(handlerId))
        pipelineEventsFlow.emit(createSttStartEvent())
        advanceUntilIdle()

        // Stop recording
        viewModel.callStopRecording(sendRecorded = true)
        advanceUntilIdle()

        // Verify order: buffered data, then empty byte array
        coVerifyOrder {
            webSocketRepository.sendVoiceData(handlerId, audioData)
            webSocketRepository.sendVoiceData(handlerId, byteArrayOf())
        }
    }

    @Test
    fun `Given active recording When stopRecording with sendRecorded false Then no data is sent`() = runTest {
        val handlerId = 42
        val audioData = byteArrayOf(1, 2, 3)

        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()

        // Buffer some data before STT starts
        audioBytesFlow.emit(audioData)
        advanceUntilIdle()

        // Send RUN_START to set handler ID but not STT_START (simulating early cancellation)
        pipelineEventsFlow.emit(createRunStartEvent(handlerId))
        advanceUntilIdle()

        // Stop recording without sending
        viewModel.callStopRecording(sendRecorded = false)
        advanceUntilIdle()

        // No voice data should be sent
        coVerify(exactly = 0) { webSocketRepository.sendVoiceData(any(), any()) }
    }

    @Test
    fun `Given no binaryHandlerId When stopRecording Then jobs are cancelled without sending`() = runTest {
        viewModel.setupRecorder()
        advanceUntilIdle()

        audioBytesFlow.emit(byteArrayOf(1, 2, 3))
        advanceUntilIdle()

        // Stop without ever receiving RUN_START (no handler ID)
        viewModel.callStopRecording(sendRecorded = true)
        advanceUntilIdle()

        // No voice data should be sent since there's no handler
        coVerify(exactly = 0) { webSocketRepository.sendVoiceData(any(), any()) }
    }

    @Test
    fun `Given multiple audio chunks When STT starts Then all chunks are forwarded in order`() = runTest {
        val handlerId = 42
        val chunks = (1..10).map { byteArrayOf(it.toByte()) }

        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()

        // Buffer multiple chunks
        chunks.forEach { audioBytesFlow.emit(it) }
        advanceUntilIdle()

        // Start STT via pipeline events
        pipelineEventsFlow.emit(createRunStartEvent(handlerId))
        pipelineEventsFlow.emit(createSttStartEvent())
        advanceUntilIdle()

        // Verify all chunks were sent in order
        coVerifyOrder {
            chunks.forEach { chunk ->
                webSocketRepository.sendVoiceData(handlerId, chunk)
            }
        }
    }

    private fun createRunStartEvent(handlerId: Int): AssistPipelineEvent {
        return AssistPipelineEvent(
            type = AssistPipelineEventType.RUN_START,
            data = AssistPipelineRunStart(
                pipeline = "test-pipeline",
                language = "en",
                runnerData = mapOf("stt_binary_handler_id" to handlerId),
            ),
        )
    }

    private fun createSttStartEvent(): AssistPipelineEvent {
        return AssistPipelineEvent(
            type = AssistPipelineEventType.STT_START,
            data = null,
        )
    }

    /**
     * Test implementation of AssistViewModelBase for testing purposes.
     */
    private class TestAssistViewModel(
        serverManager: ServerManager,
        audioRecorder: AudioRecorder,
        audioUrlPlayer: AudioUrlPlayer,
        application: Application,
    ) : AssistViewModelBase(serverManager, audioRecorder, audioUrlPlayer, application) {

        private var inputMode: AssistInputMode? = null

        override fun getInput(): AssistInputMode? = inputMode

        override fun setInput(inputMode: AssistInputMode) {
            this.inputMode = inputMode
        }

        fun runVoicePipeline(pipeline: AssistPipelineResponse? = null) {
            runAssistPipelineInternal(
                text = null, // null means voice pipeline
                pipeline = pipeline,
                onEvent = { /* ignore events in tests */ },
            )
        }

        fun callStopRecording(sendRecorded: Boolean = true) {
            stopRecording(sendRecorded)
        }
    }
}
