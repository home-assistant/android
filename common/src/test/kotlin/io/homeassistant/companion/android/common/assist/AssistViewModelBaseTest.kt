package io.homeassistant.companion.android.common.assist

import android.app.Application
import android.content.pm.PackageManager
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineError
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEventType
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineRunStart
import io.homeassistant.companion.android.common.util.AudioUrlPlayer
import io.homeassistant.companion.android.common.util.VoiceAudioRecorder
import io.homeassistant.companion.android.common.util.toAudioBytes
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class AssistViewModelBaseTest {

    private lateinit var serverManager: ServerManager
    private lateinit var voiceAudioRecorder: VoiceAudioRecorder
    private lateinit var audioUrlPlayer: AudioUrlPlayer
    private lateinit var application: Application
    private lateinit var webSocketRepository: WebSocketRepository
    private lateinit var audioDataFlow: MutableSharedFlow<ShortArray>
    private lateinit var pipelineEventsFlow: MutableSharedFlow<AssistPipelineEvent>

    @BeforeEach
    fun setUp() {
        serverManager = mockk(relaxed = true)
        voiceAudioRecorder = mockk(relaxed = true)
        audioUrlPlayer = mockk(relaxed = true)
        application = mockk(relaxed = true)
        webSocketRepository = mockk(relaxed = true)

        audioDataFlow = MutableSharedFlow(extraBufferCapacity = 10)
        pipelineEventsFlow = MutableSharedFlow(extraBufferCapacity = 10)

        val packageManager = mockk<PackageManager>()
        every { application.packageManager } returns packageManager
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) } returns true

        coEvery { voiceAudioRecorder.audioData() } returns audioDataFlow
        coEvery { serverManager.webSocketRepository(any()) } returns webSocketRepository
        coEvery { webSocketRepository.sendVoiceData(any(), any()) } returns true
        coEvery {
            webSocketRepository.runAssistPipelineForVoice(any(), any(), any(), any(), any())
        } returns pipelineEventsFlow
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given recorder setup When pipeline sends RUN_START and STT_START Then buffered audio is forwarded as one frame`() = runTest {
        val viewModel = TestAssistViewModel(audioDispatcher = StandardTestDispatcher(testScheduler))
        val samples1 = shortArrayOf(1, 2, 3)
        val samples2 = shortArrayOf(4, 5, 6)
        val handlerId = 42

        // Setup recorder and start pipeline
        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()

        // Emit audio data (should be buffered)
        audioDataFlow.emit(samples1)
        audioDataFlow.emit(samples2)
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

        // Everything buffered is forwarded in a single frame (converted to bytes via toAudioBytes)
        coVerify(exactly = 1) {
            webSocketRepository.sendVoiceData(handlerId, samples1.toAudioBytes() + samples2.toAudioBytes())
        }
    }

    @Test
    fun `Given pipeline started When new audio arrives after STT_START Then it is forwarded immediately`() = runTest {
        val viewModel = TestAssistViewModel(audioDispatcher = StandardTestDispatcher(testScheduler))
        val handlerId = 42
        val samples = shortArrayOf(1, 2, 3)

        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()

        // Start pipeline with RUN_START and STT_START
        pipelineEventsFlow.emit(createRunStartEvent(handlerId))
        pipelineEventsFlow.emit(createSttStartEvent())
        advanceUntilIdle()

        // Emit new audio
        audioDataFlow.emit(samples)
        advanceUntilIdle()

        // Should be forwarded immediately (converted to bytes)
        coVerify { webSocketRepository.sendVoiceData(handlerId, samples.toAudioBytes()) }
    }

    @Test
    fun `Given send in progress When more audio arrives Then queued chunks are sent as one frame`() = runTest {
        val viewModel = TestAssistViewModel(audioDispatcher = StandardTestDispatcher(testScheduler))
        val handlerId = 42
        val samples1 = shortArrayOf(1, 2, 3)
        val samples2 = shortArrayOf(4, 5, 6)
        val samples3 = shortArrayOf(7, 8, 9)
        val sendGate = CompletableDeferred<Boolean>()
        coEvery { webSocketRepository.sendVoiceData(any(), any()) } coAnswers { sendGate.await() }

        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()
        pipelineEventsFlow.emit(createRunStartEvent(handlerId))
        pipelineEventsFlow.emit(createSttStartEvent())
        advanceUntilIdle()

        // First chunk is picked up and its send blocks
        audioDataFlow.emit(samples1)
        advanceUntilIdle()
        coVerify(exactly = 1) { webSocketRepository.sendVoiceData(handlerId, samples1.toAudioBytes()) }

        // Chunks arriving meanwhile queue up
        audioDataFlow.emit(samples2)
        audioDataFlow.emit(samples3)
        advanceUntilIdle()

        sendGate.complete(true)
        advanceUntilIdle()

        coVerifyOrder {
            webSocketRepository.sendVoiceData(handlerId, samples1.toAudioBytes())
            webSocketRepository.sendVoiceData(handlerId, samples2.toAudioBytes() + samples3.toAudioBytes())
        }
    }

    @Test
    fun `Given queued audio larger than a frame When STT starts Then it is split into frames`() = runTest {
        val viewModel = TestAssistViewModel(audioDispatcher = StandardTestDispatcher(testScheduler))
        val handlerId = 42
        val halfFrameSamples = MAX_AUDIO_FRAME_BYTES / 2 / Short.SIZE_BYTES
        val samples1 = ShortArray(halfFrameSamples) { 1 }
        val samples2 = ShortArray(halfFrameSamples) { 2 }
        val samples3 = shortArrayOf(3)

        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()

        audioDataFlow.emit(samples1)
        audioDataFlow.emit(samples2)
        audioDataFlow.emit(samples3)
        advanceUntilIdle()

        pipelineEventsFlow.emit(createRunStartEvent(handlerId))
        pipelineEventsFlow.emit(createSttStartEvent())
        advanceUntilIdle()

        coVerifyOrder {
            webSocketRepository.sendVoiceData(handlerId, samples1.toAudioBytes() + samples2.toAudioBytes())
            webSocketRepository.sendVoiceData(handlerId, samples3.toAudioBytes())
        }
        coVerify(exactly = 2) { webSocketRepository.sendVoiceData(any(), any()) }
    }

    @Test
    fun `Given active recording When stopRecording with sendRecorded true Then buffer is drained and empty byte array is sent`() = runTest {
        val viewModel = TestAssistViewModel(audioDispatcher = StandardTestDispatcher(testScheduler))
        val handlerId = 42
        val samples = shortArrayOf(1, 2, 3)

        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()

        // Buffer some data
        audioDataFlow.emit(samples)
        advanceUntilIdle()

        // Start STT via pipeline events
        pipelineEventsFlow.emit(createRunStartEvent(handlerId))
        pipelineEventsFlow.emit(createSttStartEvent())
        advanceUntilIdle()

        // Stop recording
        viewModel.callStopRecording(sendRecorded = true)
        advanceUntilIdle()

        // Verify order: buffered data (as bytes), then empty byte array
        coVerifyOrder {
            webSocketRepository.sendVoiceData(handlerId, samples.toAudioBytes())
            webSocketRepository.sendVoiceData(handlerId, byteArrayOf())
        }
    }

    @Test
    fun `Given active recording When stopRecording with sendRecorded false Then no data is sent`() = runTest {
        val viewModel = TestAssistViewModel(audioDispatcher = StandardTestDispatcher(testScheduler))
        val handlerId = 42
        val samples = shortArrayOf(1, 2, 3)

        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()

        // Buffer some data before STT starts
        audioDataFlow.emit(samples)
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
    fun `Given audio still queued When STT_END received Then queued audio is dropped and no end marker is sent`() = runTest {
        val viewModel = TestAssistViewModel(audioDispatcher = StandardTestDispatcher(testScheduler))
        val handlerId = 42
        val samples1 = shortArrayOf(1, 2, 3)
        val sendGate = CompletableDeferred<Boolean>()
        coEvery { webSocketRepository.sendVoiceData(any(), any()) } coAnswers { sendGate.await() }

        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()
        pipelineEventsFlow.emit(createRunStartEvent(handlerId))
        pipelineEventsFlow.emit(createSttStartEvent())
        advanceUntilIdle()

        // First chunk is picked up and its send blocks while more audio queues up
        audioDataFlow.emit(samples1)
        advanceUntilIdle()
        audioDataFlow.emit(shortArrayOf(4, 5, 6))
        audioDataFlow.emit(shortArrayOf(7, 8, 9))
        advanceUntilIdle()

        // The server finished transcribing
        pipelineEventsFlow.emit(createSttEndEvent())
        advanceUntilIdle()

        sendGate.complete(true)
        advanceUntilIdle()

        // Only the chunk already in flight reached the server
        coVerify(exactly = 1) { webSocketRepository.sendVoiceData(any(), any()) }
        coVerify(exactly = 1) { webSocketRepository.sendVoiceData(handlerId, samples1.toAudioBytes()) }
    }

    @Test
    fun `Given no binaryHandlerId When stopRecording Then jobs are cancelled without sending`() = runTest {
        val viewModel = TestAssistViewModel(audioDispatcher = StandardTestDispatcher(testScheduler))
        viewModel.setupRecorder()
        advanceUntilIdle()

        audioDataFlow.emit(shortArrayOf(1, 2, 3))
        advanceUntilIdle()

        // Stop without ever receiving RUN_START (no handler ID)
        viewModel.callStopRecording(sendRecorded = true)
        advanceUntilIdle()

        // No voice data should be sent since there's no handler
        coVerify(exactly = 0) { webSocketRepository.sendVoiceData(any(), any()) }
    }

    @Test
    fun `Given multiple audio chunks When STT starts Then all chunks are forwarded in order`() = runTest {
        val viewModel = TestAssistViewModel(audioDispatcher = StandardTestDispatcher(testScheduler))
        val handlerId = 42
        val chunks = (1..10).map { shortArrayOf(it.toShort()) }

        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()

        // Buffer multiple chunks
        chunks.forEach { audioDataFlow.emit(it) }
        advanceUntilIdle()

        // Start STT via pipeline events
        pipelineEventsFlow.emit(createRunStartEvent(handlerId))
        pipelineEventsFlow.emit(createSttStartEvent())
        advanceUntilIdle()

        // All chunks are sent in order within a single frame (converted to bytes)
        val expectedFrame = chunks.fold(byteArrayOf()) { frame, chunk -> frame + chunk.toAudioBytes() }
        coVerify(exactly = 1) { webSocketRepository.sendVoiceData(handlerId, expectedFrame) }
    }

    @Test
    fun `Given voice pipeline When RUN_START received Then PipelineStarted event is emitted`() = runTest {
        val viewModel = TestAssistViewModel(audioDispatcher = StandardTestDispatcher(testScheduler))
        val handlerId = 42
        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()

        pipelineEventsFlow.emit(createRunStartEvent(handlerId))
        advanceUntilIdle()

        assertEquals(listOf(AssistEvent.PipelineStarted), viewModel.receivedEvents)
    }

    @Test
    fun `Given voice pipeline When duplicate wake-up error received Then Dismiss event is emitted`() = runTest {
        val viewModel = TestAssistViewModel(audioDispatcher = StandardTestDispatcher(testScheduler))
        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()

        pipelineEventsFlow.emit(createErrorEvent(code = "duplicate_wake_up_detected"))
        advanceUntilIdle()

        assertEquals(listOf(AssistEvent.Dismiss), viewModel.receivedEvents)
    }

    @Test
    fun `Given wake word phrase When voice pipeline started Then phrase is passed to WebSocket`() = runTest {
        val viewModel = TestAssistViewModel(audioDispatcher = StandardTestDispatcher(testScheduler))
        val wakePhrase = "Hey Jarvis"

        viewModel.setupRecorder()
        viewModel.runVoicePipeline(wakeWordPhrase = wakePhrase)
        advanceUntilIdle()

        coVerify {
            webSocketRepository.runAssistPipelineForVoice(
                sampleRate = any(),
                outputTts = any(),
                pipelineId = any(),
                conversationId = any(),
                wakeWordPhrase = wakePhrase,
            )
        }
    }

    @Test
    fun `Given no wake word phrase When voice pipeline started Then phrase is null in WebSocket call`() = runTest {
        val viewModel = TestAssistViewModel(audioDispatcher = StandardTestDispatcher(testScheduler))
        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()

        coVerify {
            webSocketRepository.runAssistPipelineForVoice(
                sampleRate = any(),
                outputTts = any(),
                pipelineId = any(),
                conversationId = any(),
                wakeWordPhrase = null,
            )
        }
    }

    @Test
    fun `Given external audio strategy When pipeline runs Then external audio is forwarded`() = runTest {
        val externalAudioFlow = MutableSharedFlow<ShortArray>(extraBufferCapacity = 10)
        val externalStrategy = object : AssistAudioStrategy {
            override suspend fun audioData() = externalAudioFlow
            override val wakeWordDetected: Flow<String> = emptyFlow()
            override fun requestFocus() {}
            override fun abandonFocus() {}
        }
        val externalVm = TestAssistViewModel(
            audioStrategy = externalStrategy,
            audioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val handlerId = 42
        val samples = shortArrayOf(10, 20, 30)

        externalVm.setupRecorder()
        externalVm.runVoicePipeline()
        advanceUntilIdle()

        // Emit via external audio source
        externalAudioFlow.emit(samples)
        advanceUntilIdle()

        // Start STT
        pipelineEventsFlow.emit(createRunStartEvent(handlerId))
        pipelineEventsFlow.emit(createSttStartEvent())
        advanceUntilIdle()

        // External audio should be forwarded to pipeline (converted to bytes)
        coVerify { webSocketRepository.sendVoiceData(handlerId, samples.toAudioBytes()) }
    }

    @Test
    fun `Given external audio strategy When stopRecording called Then strategy abandonFocus is called`() = runTest {
        var focusAbandoned = false
        val externalStrategy = object : AssistAudioStrategy {
            override suspend fun audioData() = MutableSharedFlow<ShortArray>()
            override val wakeWordDetected: Flow<String> = emptyFlow()
            override fun requestFocus() {}
            override fun abandonFocus() {
                focusAbandoned = true
            }
        }
        val externalVm = TestAssistViewModel(
            audioStrategy = externalStrategy,
            audioDispatcher = StandardTestDispatcher(testScheduler),
        )

        externalVm.setupRecorder()
        advanceUntilIdle()

        externalVm.callStopRecording(sendRecorded = false)
        advanceUntilIdle()

        // The external strategy's abandonFocus should be called
        assertTrue(focusAbandoned, "External strategy abandonFocus should be called")
    }

    @Test
    fun `Given audio collection fails When recorder is running Then onError is called`() = runTest {
        val audioError = RuntimeException("Microphone access lost")
        val errorStrategy = object : AssistAudioStrategy {
            override suspend fun audioData() = flow<ShortArray> { throw audioError }
            override val wakeWordDetected: Flow<String> = emptyFlow()
            override fun requestFocus() {}
            override fun abandonFocus() {}
        }
        val viewModel = TestAssistViewModel(
            audioStrategy = errorStrategy,
            audioDispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.setupRecorder()
        advanceUntilIdle()

        assertEquals(1, viewModel.receivedErrors.size)
        assertEquals("Microphone access lost", viewModel.receivedErrors.first().message)
    }

    @Test
    fun `Given sending voice data fails When consumer forwards audio Then onError is called`() = runTest {
        val viewModel = TestAssistViewModel(audioDispatcher = StandardTestDispatcher(testScheduler))
        val handlerId = 42
        val sendError = RuntimeException("WebSocket disconnected")
        coEvery { webSocketRepository.sendVoiceData(any(), any()) } throws sendError

        viewModel.setupRecorder()
        viewModel.runVoicePipeline()
        advanceUntilIdle()

        audioDataFlow.emit(shortArrayOf(1, 2, 3))
        advanceUntilIdle()

        pipelineEventsFlow.emit(createRunStartEvent(handlerId))
        pipelineEventsFlow.emit(createSttStartEvent())
        advanceUntilIdle()

        assertEquals(1, viewModel.receivedErrors.size)
        assertEquals("WebSocket disconnected", viewModel.receivedErrors.first().message)
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

    private fun createSttEndEvent(): AssistPipelineEvent {
        return AssistPipelineEvent(
            type = AssistPipelineEventType.STT_END,
            data = null,
        )
    }

    private fun createErrorEvent(code: String, message: String? = null): AssistPipelineEvent {
        return AssistPipelineEvent(
            type = AssistPipelineEventType.ERROR,
            data = AssistPipelineError(code = code, message = message),
        )
    }

    /**
     * Test implementation of AssistViewModelBase for testing purposes.
     */
    private inner class TestAssistViewModel(
        audioStrategy: AssistAudioStrategy = DefaultAssistAudioStrategy(voiceAudioRecorder),
        audioDispatcher: CoroutineDispatcher,
    ) : AssistViewModelBase(serverManager, audioStrategy, audioUrlPlayer, application, audioDispatcher) {

        private var inputMode: AssistInputMode? = null

        override fun getInput(): AssistInputMode? = inputMode

        override fun setInput(inputMode: AssistInputMode) {
            this.inputMode = inputMode
        }

        val receivedEvents = mutableListOf<AssistEvent>()
        val receivedErrors = mutableListOf<Throwable>()

        fun setupRecorder() {
            setupRecorder(onError = { receivedErrors += it })
        }

        fun runVoicePipeline(
            pipeline: AssistPipelineResponse? = null,
            wakeWordPhrase: String? = null,
        ) {
            runAssistPipelineInternal(
                text = null, // null means voice pipeline
                pipeline = pipeline,
                wakeWordPhrase = wakeWordPhrase,
                onEvent = { receivedEvents += it },
            )
        }

        fun callStopRecording(sendRecorded: Boolean = true) {
            stopRecording(sendRecorded)
        }
    }
}
