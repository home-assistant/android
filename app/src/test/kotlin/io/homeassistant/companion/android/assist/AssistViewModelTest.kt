package io.homeassistant.companion.android.assist

import android.app.Application
import android.content.pm.PackageManager
import io.homeassistant.companion.android.common.assist.AssistAudioStrategy
import io.homeassistant.companion.android.common.assist.AssistChimeManager
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEventType
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineIntentEnd
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineListResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineTtsEnd
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ConversationResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ConversationSpeechPlainResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ConversationSpeechResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TtsOutputResponse
import io.homeassistant.companion.android.common.util.AudioUrlPlayer
import io.homeassistant.companion.android.common.util.PlaybackState
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import java.net.URL
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private const val CHIME = "chime"
private const val CAPTURE = "capture"

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class AssistViewModelTest {

    private val serverManager: ServerManager = mockk(relaxed = true)
    private val audioUrlPlayer: AudioUrlPlayer = mockk(relaxed = true)
    private val application: Application = mockk(relaxed = true)
    private val webSocketRepository: WebSocketRepository = mockk(relaxed = true)
    private val integrationRepository: IntegrationRepository = mockk(relaxed = true)
    private val chimeManager: AssistChimeManager = mockk(relaxed = true)

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

    private fun createViewModel(
        audioStrategy: AssistAudioStrategy = object : AssistAudioStrategy {
            override suspend fun audioData(): Flow<ShortArray> = emptyFlow()

            override val wakeWordDetected: Flow<String> = emptyFlow()

            override fun requestFocus() {
                // No-op for testing
            }

            override fun abandonFocus() {
                // No-op for testing
            }
        },
    ): AssistViewModel {
        return AssistViewModel(
            serverManager = serverManager,
            audioUrlPlayer = audioUrlPlayer,
            chimeManager = chimeManager,
            application = application,
            initialAudioStrategy = audioStrategy,
        )
    }

    private fun createAndInitialize(
        hasPermission: Boolean = false,
        startedWithWakeWord: Boolean = false,
        fromSystemAssistant: Boolean = false,
        audioStrategy: AssistAudioStrategy? = null,
    ): AssistViewModel {
        val vm = audioStrategy?.let { createViewModel(it) } ?: createViewModel()
        vm.onCreate(
            hasPermission = hasPermission,
            serverId = null,
            pipelineId = null,
            startListening = null,
            launchTrigger = when {
                startedWithWakeWord -> AssistTrigger.WakeWord("Okay Nabu")
                fromSystemAssistant -> AssistTrigger.SystemAssistant
                else -> AssistTrigger.ScreenUi
            },
        )
        return vm
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Nested
    inner class InactivityTimerTest {

        private val pipelineEvents = MutableSharedFlow<AssistPipelineEvent>()

        /**
         * Sets up mocks for a voice pipeline backed by [pipelineEvents].
         *
         * Configures the audio recorder to start successfully and wires
         * [pipelineEvents] as the pipeline event source for voice input.
         */
        private fun setupVoicePipeline() {
            coEvery {
                webSocketRepository.runAssistPipelineForVoice(any(), any(), anyNullable(), anyNullable(), anyNullable())
            } returns pipelineEvents
        }

        /**
         * Sets up mocks for a voice pipeline that supports TTS playback.
         *
         * Extends [setupVoicePipeline] with connection state and [AudioUrlPlayer]
         * mocks so that emitting [AssistPipelineEventType.TTS_END] triggers audio
         * playback through [playbackStates].
         */
        private fun setupVoicePipelineWithTts(playbackStates: MutableSharedFlow<PlaybackState>) {
            setupVoicePipeline()

            val connectionStateProvider = mockk<ServerConnectionStateProvider>()
            coEvery { serverManager.connectionStateProvider(any()) } returns connectionStateProvider
            every { connectionStateProvider.urlFlow(anyNullable()) } returns flowOf(
                UrlState.HasUrl(URL("http://test-ha.local")),
            )
            every { audioUrlPlayer.playAudio(any(), any()) } returns playbackStates
        }

        private suspend fun emitIntentEnd() {
            pipelineEvents.emit(
                AssistPipelineEvent(
                    type = AssistPipelineEventType.INTENT_END,
                    data = AssistPipelineIntentEnd(
                        intentOutput = ConversationResponse(
                            response = ConversationSpeechResponse(
                                speech = ConversationSpeechPlainResponse(
                                    plain = mapOf("speech" to "Hello there"),
                                ),
                            ),
                            conversationId = "test-conv",
                        ),
                    ),
                ),
            )
        }

        private suspend fun emitTtsEnd() {
            pipelineEvents.emit(
                AssistPipelineEvent(
                    type = AssistPipelineEventType.TTS_END,
                    data = AssistPipelineTtsEnd(
                        ttsOutput = TtsOutputResponse(
                            mimeType = "audio/mpeg",
                            url = "/api/tts_proxy/test.mp3",
                        ),
                    ),
                ),
            )
        }

        @Test
        fun `Given started with wake word and voice inactive mode and non-placeholder message when CLOSE_INACTIVE elapses then shouldFinish is true`() = runTest {
            viewModel = createAndInitialize(startedWithWakeWord = true)
            runCurrent()

            advanceTimeBy(CLOSE_INACTIVE)
            runCurrent()

            assertTrue(viewModel.shouldFinish)
        }

        @Test
        fun `Given voice inactive mode and non-placeholder message when less than CLOSE_INACTIVE elapses then shouldFinish is false`() = runTest {
            viewModel = createAndInitialize()
            runCurrent()

            advanceTimeBy(CLOSE_INACTIVE - 1.seconds)
            runCurrent()

            assertFalse(viewModel.shouldFinish)
        }

        @Test
        fun `Given text input mode when CLOSE_INACTIVE elapses then shouldFinish is false`() = runTest {
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

            advanceTimeBy(CLOSE_INACTIVE + 1.seconds)
            runCurrent()

            assertFalse(viewModel.shouldFinish)
        }

        @Test
        fun `Given blocked input mode when CLOSE_INACTIVE elapses then shouldFinish is false`() = runTest {
            coEvery { serverManager.isRegistered() } returns false
            viewModel = createAndInitialize()
            runCurrent()

            advanceTimeBy(CLOSE_INACTIVE + 1.seconds)
            runCurrent()

            assertFalse(viewModel.shouldFinish)
        }

        @Test
        fun `Given voice inactive mode when onPause is called then timer is cancelled`() = runTest {
            viewModel = createAndInitialize()
            runCurrent()

            advanceTimeBy(1.seconds)
            runCurrent()

            viewModel.onPause()
            runCurrent()

            advanceTimeBy(CLOSE_INACTIVE - 1.seconds)
            runCurrent()

            assertFalse(viewModel.shouldFinish)
        }

        @Test
        fun `Given voice inactive mode when onDestroy is called then timer is cancelled`() = runTest {
            viewModel = createAndInitialize()
            runCurrent()

            advanceTimeBy(1.seconds)
            runCurrent()

            viewModel.onDestroy()
            runCurrent()

            advanceTimeBy(CLOSE_INACTIVE - 1.seconds)
            runCurrent()

            assertFalse(viewModel.shouldFinish)
        }

        @Test
        fun `Given voice inactive mode when input mode changes to TEXT then timer is cancelled`() = runTest {
            viewModel = createAndInitialize()
            runCurrent()

            advanceTimeBy(1.seconds)
            runCurrent()

            viewModel.onChangeInput() // VOICE_INACTIVE -> TEXT
            runCurrent()

            advanceTimeBy(CLOSE_INACTIVE - 1.seconds)
            runCurrent()

            assertFalse(viewModel.shouldFinish)
        }

        @Test
        fun `Given started with wake word and TEXT mode when switching back to VOICE_INACTIVE then timer restarts`() = runTest {
            viewModel = createAndInitialize(startedWithWakeWord = true)
            runCurrent()

            viewModel.onChangeInput() // VOICE_INACTIVE -> TEXT
            runCurrent()

            advanceTimeBy(CLOSE_INACTIVE)
            runCurrent()
            assertFalse(viewModel.shouldFinish)

            viewModel.onChangeInput() // TEXT -> VOICE_INACTIVE
            runCurrent()

            advanceTimeBy(CLOSE_INACTIVE - 1.seconds)
            runCurrent()
            assertFalse(viewModel.shouldFinish)

            advanceTimeBy(1.seconds)
            runCurrent()
            assertTrue(viewModel.shouldFinish)
        }

        @Test
        fun `Given voice active mode when CLOSE_INACTIVE elapses then shouldFinish is false`() = runTest {
            setupVoicePipeline()
            coEvery {
                webSocketRepository.runAssistPipelineForVoice(any(), any(), anyNullable(), anyNullable(), anyNullable())
            } returns flow { awaitCancellation() }

            viewModel = createAndInitialize(hasPermission = true)
            runCurrent()

            advanceTimeBy(CLOSE_INACTIVE + 1.seconds)
            runCurrent()

            assertFalse(viewModel.shouldFinish)
        }

        @Test
        fun `Given started with wake word and voice active mode when recording stops and response arrives then timer fires and shouldFinish is true`() = runTest {
            setupVoicePipeline()

            viewModel = createAndInitialize(hasPermission = true, startedWithWakeWord = true)
            runCurrent()

            // VM is now VOICE_ACTIVE. Stop recording to transition to VOICE_INACTIVE.
            viewModel.onMicrophoneInput(AssistTrigger.ScreenUi)
            runCurrent()

            // Last message is still a placeholder, so the timer should not start yet.
            advanceTimeBy(CLOSE_INACTIVE + 1.seconds)
            runCurrent()
            assertFalse(viewModel.shouldFinish)

            // Pipeline emits a response, replacing the placeholder with a real message.
            emitIntentEnd()
            runCurrent()

            // Timer is now running. Verify it hasn't fired yet just before expiry.
            advanceTimeBy(CLOSE_INACTIVE - 1.seconds)
            runCurrent()
            assertFalse(viewModel.shouldFinish)

            // Timer fires.
            advanceTimeBy(1.seconds)
            runCurrent()
            assertTrue(viewModel.shouldFinish)
        }

        @Test
        fun `Given screen UI session reused with a wake word intent when recording stops and response arrives then timer fires`() = runTest {
            setupVoicePipeline()

            // The session starts from the screen, so the inactivity timer is disabled.
            viewModel = createAndInitialize(hasPermission = true)
            runCurrent()

            // A new wake-word intent reaches the running (singleTask) activity, reclassifying the session.
            viewModel.onNewIntent(
                intent = mockk(relaxed = true),
                lockedMatches = true,
                launchTrigger = AssistTrigger.WakeWord("Okay Nabu"),
            )
            runCurrent()

            // Reusing the active session stops recording and moves to VOICE_INACTIVE, but the last
            // message is still a placeholder so the timer does not start yet.
            advanceTimeBy(CLOSE_INACTIVE + 1.seconds)
            runCurrent()
            assertFalse(viewModel.shouldFinish)

            // Pipeline emits a response, replacing the placeholder with a real message.
            emitIntentEnd()
            runCurrent()

            // The wake-word classification survived the reuse, so the timer now closes the dialog.
            advanceTimeBy(CLOSE_INACTIVE)
            runCurrent()
            assertTrue(viewModel.shouldFinish)
        }

        @Test
        fun `Given voice inactive mode with placeholder message when CLOSE_INACTIVE elapses then shouldFinish is false`() = runTest {
            setupVoicePipeline()
            coEvery {
                webSocketRepository.runAssistPipelineForVoice(any(), any(), anyNullable(), anyNullable(), anyNullable())
            } returns flow { awaitCancellation() }

            viewModel = createAndInitialize(hasPermission = true)
            runCurrent()

            // Stop recording: transitions to VOICE_INACTIVE while last message is still a placeholder
            viewModel.onMicrophoneInput(AssistTrigger.ScreenUi)
            runCurrent()

            advanceTimeBy(CLOSE_INACTIVE + 1.seconds)
            runCurrent()

            assertFalse(viewModel.shouldFinish)
        }

        @Test
        fun `Given voice inactive mode and audio playing when CLOSE_INACTIVE elapses then shouldFinish is false`() = runTest {
            val playbackStates = MutableSharedFlow<PlaybackState>()
            setupVoicePipelineWithTts(playbackStates)

            viewModel = createAndInitialize(hasPermission = true)
            runCurrent()

            // Stop recording to transition to VOICE_INACTIVE
            viewModel.onMicrophoneInput(AssistTrigger.ScreenUi)
            runCurrent()

            // Emit INTENT_END to replace placeholder with a real message
            emitIntentEnd()
            runCurrent()

            // Emit TTS_END which triggers audio playback
            emitTtsEnd()
            runCurrent()

            // Audio starts playing
            playbackStates.emit(PlaybackState.PLAYING)
            runCurrent()

            advanceTimeBy(CLOSE_INACTIVE + 1.seconds)
            runCurrent()

            assertFalse(viewModel.shouldFinish)
        }

        @Test
        fun `Given started with wake word and voice inactive mode and audio playing when playback stops then timer starts`() = runTest {
            val playbackStates = MutableSharedFlow<PlaybackState>()
            setupVoicePipelineWithTts(playbackStates)

            viewModel = createAndInitialize(hasPermission = true, startedWithWakeWord = true)
            runCurrent()

            // Stop recording to transition to VOICE_INACTIVE
            viewModel.onMicrophoneInput(AssistTrigger.ScreenUi)
            runCurrent()

            // Emit INTENT_END to replace placeholder with a real message
            emitIntentEnd()
            runCurrent()

            // Emit TTS_END and start playback
            emitTtsEnd()
            runCurrent()
            playbackStates.emit(PlaybackState.PLAYING)
            runCurrent()

            // Timer should not fire while audio is playing
            advanceTimeBy(CLOSE_INACTIVE + 1.seconds)
            runCurrent()
            assertFalse(viewModel.shouldFinish)

            // Audio playback finishes → triggers PlaybackFinished → restartInactivityTimer()
            playbackStates.emit(PlaybackState.STOP_PLAYING)
            runCurrent()

            advanceTimeBy(CLOSE_INACTIVE - 1.seconds)
            runCurrent()
            assertFalse(viewModel.shouldFinish)

            advanceTimeBy(1.seconds)
            runCurrent()
            assertTrue(viewModel.shouldFinish)
        }

        @Test
        fun `Given started with wake word and voice inactive mode when text input triggers a response then timer restarts`() = runTest {
            val textPipelineEvents = MutableSharedFlow<AssistPipelineEvent>()
            coEvery {
                integrationRepository.getAssistResponse(any(), anyNullable(), anyNullable())
            } returns textPipelineEvents

            viewModel = createAndInitialize(startedWithWakeWord = true)
            runCurrent()

            // Timer is running. Advance partway through.
            advanceTimeBy(CLOSE_INACTIVE - 5.seconds)
            runCurrent()
            assertFalse(viewModel.shouldFinish)

            // Send text input which starts a pipeline run
            viewModel.onTextInput("hello")
            runCurrent()

            // Emit a response message from the pipeline, which calls restartInactivityTimer()
            textPipelineEvents.emit(
                AssistPipelineEvent(
                    type = AssistPipelineEventType.INTENT_END,
                    data = AssistPipelineIntentEnd(
                        intentOutput = ConversationResponse(
                            response = ConversationSpeechResponse(
                                speech = ConversationSpeechPlainResponse(
                                    plain = mapOf("speech" to "Hello there"),
                                ),
                            ),
                            conversationId = "test-conv",
                        ),
                    ),
                ),
            )
            runCurrent()

            // Original CLOSE_INACTIVE has now elapsed since initialization, but timer was restarted
            advanceTimeBy(5.seconds)
            runCurrent()
            assertFalse(viewModel.shouldFinish)

            // Wait for the restarted timer to almost expire
            advanceTimeBy(CLOSE_INACTIVE - 6.seconds)
            runCurrent()
            assertFalse(viewModel.shouldFinish)

            // Now the restarted timer expires
            advanceTimeBy(1.seconds)
            runCurrent()
            assertTrue(viewModel.shouldFinish)
        }

        @Test
        fun `Given not a wake word session when CLOSE_INACTIVE elapses then shouldFinish is false`() = runTest {
            viewModel = createViewModel()
            viewModel.onCreate(
                hasPermission = false,
                serverId = null,
                pipelineId = null,
                startListening = null,
                launchTrigger = AssistTrigger.ScreenUi,
            )
            runCurrent()

            advanceTimeBy(CLOSE_INACTIVE + 1.seconds)
            runCurrent()

            assertFalse(viewModel.shouldFinish)
        }
    }

    @Nested
    inner class ListeningChimeTest {

        /** Records the order of chimes and audio captures. */
        private val events = mutableListOf<String>()
        private val wakeWordDetected = MutableSharedFlow<String>()

        private val audioStrategy = object : AssistAudioStrategy {
            override suspend fun audioData(): Flow<ShortArray> = flow {
                events += CAPTURE
                awaitCancellation()
            }

            override val wakeWordDetected: Flow<String> = this@ListeningChimeTest.wakeWordDetected

            override fun requestFocus() {
                // No-op for testing
            }

            override fun abandonFocus() {
                // No-op for testing
            }
        }

        @BeforeEach
        fun setUpChime() {
            coEvery { chimeManager.playListeningChimeIfEnabled() } coAnswers { events += CHIME }
            coEvery {
                webSocketRepository.runAssistPipelineForVoice(any(), any(), anyNullable(), anyNullable(), anyNullable())
            } returns MutableSharedFlow()
        }

        @Test
        fun `Given system assistant launch when listening starts then play the chime before capturing audio`() = runTest {
            viewModel = createAndInitialize(hasPermission = true, fromSystemAssistant = true, audioStrategy = audioStrategy)
            runCurrent()

            assertEquals(listOf(CHIME, CAPTURE), events)
        }

        @Test
        fun `Given screen UI launch when listening starts then do not play the chime`() = runTest {
            viewModel = createAndInitialize(hasPermission = true, fromSystemAssistant = false, audioStrategy = audioStrategy)
            runCurrent()

            assertEquals(listOf(CAPTURE), events)
        }

        @Test
        fun `Given system assistant session when the user taps the microphone to listen again then do not play the chime`() = runTest {
            viewModel = createAndInitialize(hasPermission = true, fromSystemAssistant = true, audioStrategy = audioStrategy)
            runCurrent()
            viewModel.onMicrophoneInput(AssistTrigger.ScreenUi) // Stops listening
            runCurrent()
            events.clear()

            viewModel.onMicrophoneInput(AssistTrigger.ScreenUi) // Listens again
            runCurrent()

            assertEquals(listOf(CAPTURE), events)
        }

        @Test
        fun `Given screen UI session when the wake word is detected then play the chime before capturing audio`() = runTest {
            viewModel = createAndInitialize(hasPermission = true, fromSystemAssistant = false, audioStrategy = audioStrategy)
            runCurrent()
            viewModel.onMicrophoneInput(AssistTrigger.ScreenUi) // Stops listening
            runCurrent()
            events.clear()

            wakeWordDetected.emit("Okay Nabu")
            runCurrent()

            assertEquals(listOf(CHIME, CAPTURE), events)
        }

        @Test
        fun `Given chime playing when listening stops then do not capture audio`() = runTest {
            coEvery { chimeManager.playListeningChimeIfEnabled() } coAnswers {
                events += CHIME
                awaitCancellation()
            }
            viewModel = createAndInitialize(hasPermission = true, fromSystemAssistant = true, audioStrategy = audioStrategy)
            runCurrent()

            viewModel.onMicrophoneInput(AssistTrigger.ScreenUi) // Stops listening while the chime is playing
            runCurrent()

            assertEquals(listOf(CHIME), events)
        }

        @Test
        fun `Given hands-free launch without permission when permission is granted then play the chime before capturing audio`() = runTest {
            viewModel = createAndInitialize(hasPermission = false, fromSystemAssistant = true, audioStrategy = audioStrategy)
            runCurrent()
            // Listening cannot start until the microphone permission is granted.
            assertTrue(events.isEmpty())

            viewModel.onPermissionResult(granted = true)
            runCurrent()

            assertEquals(listOf(CHIME, CAPTURE), events)
        }

        @Test
        fun `Given hands-free launch without permission when the user taps the microphone and grants permission then do not play the chime`() = runTest {
            viewModel = createAndInitialize(hasPermission = false, fromSystemAssistant = true, audioStrategy = audioStrategy)
            runCurrent()
            // The hands-free request is still waiting on the permission prompt.
            assertTrue(events.isEmpty())

            viewModel.onMicrophoneInput(AssistTrigger.ScreenUi) // User taps the microphone, re-requesting permission.
            runCurrent()
            viewModel.onPermissionResult(granted = true)
            runCurrent()

            // The grant resumes the screen tap, not the hands-free launch, so no chime plays.
            assertEquals(listOf(CAPTURE), events)
        }

        @Test
        fun `Given screen UI session when the conversation continues then play the chime before capturing audio`() = runTest {
            val pipelineEvents = MutableSharedFlow<AssistPipelineEvent>()
            coEvery {
                webSocketRepository.runAssistPipelineForVoice(any(), any(), anyNullable(), anyNullable(), anyNullable())
            } returns pipelineEvents
            val connectionStateProvider = mockk<ServerConnectionStateProvider>()
            coEvery { serverManager.connectionStateProvider(any()) } returns connectionStateProvider
            every { connectionStateProvider.urlFlow(anyNullable()) } returns flowOf(
                UrlState.HasUrl(URL("http://test-ha.local")),
            )
            val playbackStates = MutableSharedFlow<PlaybackState>()
            every { audioUrlPlayer.playAudio(any(), any()) } returns playbackStates

            viewModel = createAndInitialize(hasPermission = true, fromSystemAssistant = false, audioStrategy = audioStrategy)
            runCurrent()
            // Screen UI launch captures audio without a chime.
            assertEquals(listOf(CAPTURE), events)
            events.clear()

            // The turn finishes asking to continue the conversation, which reopens the microphone hands-free.
            pipelineEvents.emit(AssistPipelineEvent(AssistPipelineEventType.STT_END, null))
            runCurrent()
            pipelineEvents.emit(
                AssistPipelineEvent(
                    type = AssistPipelineEventType.INTENT_END,
                    data = AssistPipelineIntentEnd(
                        intentOutput = ConversationResponse(
                            response = ConversationSpeechResponse(
                                speech = ConversationSpeechPlainResponse(plain = mapOf("speech" to "Anything else?")),
                            ),
                            conversationId = "test-conv",
                            continueConversation = true,
                        ),
                    ),
                ),
            )
            runCurrent()
            pipelineEvents.emit(
                AssistPipelineEvent(
                    type = AssistPipelineEventType.TTS_END,
                    data = AssistPipelineTtsEnd(
                        ttsOutput = TtsOutputResponse(mimeType = "audio/mpeg", url = "/api/tts_proxy/test.mp3"),
                    ),
                ),
            )
            runCurrent()
            playbackStates.emit(PlaybackState.PLAYING)
            playbackStates.emit(PlaybackState.STOP_PLAYING)
            runCurrent()

            // The continued turn is hands-free, so it chimes even though the session started from the screen.
            assertEquals(listOf(CHIME, CAPTURE), events)
        }
    }
}
