package io.homeassistant.companion.android.common.util

import android.media.AudioRecord
import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class VoiceAudioRecorderTest {

    @Nested
    inner class `Audio recording lifecycle` {

        @Test
        fun `Given all collectors cancelled When WhileSubscribed stops Then recorder is stopped and released`() = runTest {
            val recorder = mockk<AudioRecord>(relaxed = true) {
                every { state } returns AudioRecord.STATE_INITIALIZED
                every { read(any<ShortArray>(), eq(0), any()) } answers {
                    // Simulate the blocking nature of a real AudioRecord.read() call.
                    // Without this, the mock returns instantly creating a tight
                    // non-suspending loop that prevents cancellation from propagating.
                    Thread.sleep(1)
                    val buffer = firstArg<ShortArray>()
                    shortArrayOf(42).copyInto(buffer)
                    1
                }
            }
            val voiceAudioRecorder = VoiceAudioRecorder(
                audioRecordFactory = { recorder },
                sharingScope = backgroundScope,
            )

            val flow = voiceAudioRecorder.audioData()

            turbineScope {
                val turbine1 = flow.testIn(this)
                val turbine2 = flow.testIn(this)

                // Both receive data — upstream is active
                turbine1.awaitItem()
                turbine2.awaitItem()

                // Cancel first collector — recorder should still be active
                turbine1.cancelAndConsumeRemainingEvents()
                verify(exactly = 0) { recorder.stop() }

                // Cancel second (last) collector — triggers WhileSubscribed cleanup
                turbine2.cancelAndConsumeRemainingEvents()
            }
            // Process WhileSubscribed cancellation in the sharing scope
            advanceUntilIdle()
            runCurrent()

            verify { recorder.stop() }
            verify { recorder.release() }
        }

        @Test
        fun `Given uninitialized recorder When audioData collected Then does not emit`() = runTest {
            val recorder = mockk<AudioRecord>(relaxed = true) {
                every { state } returns AudioRecord.STATE_UNINITIALIZED
            }

            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val voiceAudioRecorder = VoiceAudioRecorder(
                audioRecordFactory = { recorder },
                recorderDispatcher = testDispatcher,
                sharingScope = backgroundScope,
            )

            val flow = voiceAudioRecorder.audioData()

            turbineScope {
                val turbine = flow.testIn(this)

                advanceUntilIdle()
                runCurrent()

                turbine.expectNoEvents()
                turbine.cancelAndConsumeRemainingEvents()
            }

            verify { recorder.release() }
            verify(exactly = 0) { recorder.startRecording() }
        }

        @Test
        fun `Given recorder with read error When audioData collected Then stops emitting`() = runTest {
            val recorder = mockk<AudioRecord>(relaxed = true) {
                every { state } returns AudioRecord.STATE_INITIALIZED
                every { read(any<ShortArray>(), any(), any()) } returns AudioRecord.ERROR
            }

            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val voiceAudioRecorder = VoiceAudioRecorder(
                audioRecordFactory = { recorder },
                recorderDispatcher = testDispatcher,
                sharingScope = backgroundScope,
            )

            val flow = voiceAudioRecorder.audioData()

            turbineScope {
                val turbine = flow.testIn(this)

                advanceUntilIdle()
                runCurrent()

                turbine.expectNoEvents()
                turbine.cancelAndConsumeRemainingEvents()
            }

            advanceUntilIdle()
            runCurrent()

            verify { recorder.stop() }
            verify { recorder.release() }
        }

        @Test
        fun `Given recorder When multiple chunks read Then emits each chunk`() = runTest {
            var callCount = 0
            val chunk1 = shortArrayOf(10, 20)
            val chunk2 = shortArrayOf(30, 40)

            val recorder = mockk<AudioRecord>(relaxed = true) {
                every { state } returns AudioRecord.STATE_INITIALIZED
                every { read(any<ShortArray>(), eq(0), any()) } answers {
                    val buffer = firstArg<ShortArray>()
                    when (callCount++) {
                        0 -> {
                            chunk1.copyInto(buffer)
                            chunk1.size
                        }

                        1 -> {
                            chunk2.copyInto(buffer)
                            chunk2.size
                        }

                        else -> {
                            chunk2.copyInto(buffer)
                            chunk2.size
                        }
                    }
                }
            }
            val voiceAudioRecorder = VoiceAudioRecorder(
                audioRecordFactory = { recorder },
            )

            val flow = voiceAudioRecorder.audioData()

            turbineScope {
                val turbine = flow.testIn(this)

                assertArrayEquals(chunk1, turbine.awaitItem())
                assertArrayEquals(chunk2, turbine.awaitItem())

                turbine.cancelAndConsumeRemainingEvents()
            }
        }
    }

    @Nested
    inner class `Shared flow behavior` {

        @Test
        fun `Given recorder When audioData called multiple times Then returns same flow instance`() = runTest {
            val recorder = createMockAudioRecordContinuous(shortArrayOf(1))
            val voiceAudioRecorder = VoiceAudioRecorder(
                audioRecordFactory = { recorder },
            )

            val flow1 = voiceAudioRecorder.audioData()
            val flow2 = voiceAudioRecorder.audioData()

            assertSame(flow1, flow2, "Multiple audioData() calls should return the same shared flow")
        }

        @Test
        fun `Given two collectors When both collect Then both receive same audio data`() = runTest {
            val data = shortArrayOf(42, 84)
            val recorder = mockk<AudioRecord>(relaxed = true) {
                every { state } returns AudioRecord.STATE_INITIALIZED
                every { read(any<ShortArray>(), eq(0), any()) } answers {
                    // Simulate blocking read so both turbine subscribers have time
                    // to register with shareIn before the first emission
                    Thread.sleep(1)
                    val buffer = firstArg<ShortArray>()
                    data.copyInto(buffer)
                    data.size
                }
            }
            val voiceAudioRecorder = VoiceAudioRecorder(
                audioRecordFactory = { recorder },
            )

            val flow = voiceAudioRecorder.audioData()

            turbineScope {
                val turbine1 = flow.testIn(this)
                val turbine2 = flow.testIn(this)

                assertArrayEquals(data, turbine1.awaitItem())
                assertArrayEquals(data, turbine2.awaitItem())

                turbine1.cancelAndConsumeRemainingEvents()
                turbine2.cancelAndConsumeRemainingEvents()
            }

            // Only one AudioRecord should have been created
            verify(exactly = 1) { recorder.startRecording() }
        }
    }

    @Nested
    inner class `toAudioBytes conversion` {

        @Test
        fun `Given ShortArray When toAudioBytes called Then converts to little-endian ByteArray`() {
            // 0x0100 = 256 -> low byte = 0x00, high byte = 0x01
            val input = shortArrayOf(256)
            val result = input.toAudioBytes()

            assertEquals(2, result.size)
            assertEquals(0x00.toByte(), result[0]) // Low byte
            assertEquals(0x01.toByte(), result[1]) // High byte
        }

        @Test
        fun `Given ShortArray with negative values When toAudioBytes called Then converts correctly`() {
            // -1 as short = 0xFFFF -> low byte = 0xFF, high byte = 0xFF
            val input = shortArrayOf(-1)
            val result = input.toAudioBytes()

            assertEquals(2, result.size)
            assertEquals(0xFF.toByte(), result[0])
            assertEquals(0xFF.toByte(), result[1])
        }

        @Test
        fun `Given empty ShortArray When toAudioBytes called Then returns empty ByteArray`() {
            val result = shortArrayOf().toAudioBytes()
            assertEquals(0, result.size)
        }

        @Test
        fun `Given multiple samples When toAudioBytes called Then converts all correctly`() {
            // 0x0102 = 258 -> low = 0x02, high = 0x01
            // 0x0304 = 772 -> low = 0x04, high = 0x03
            val input = shortArrayOf(0x0102, 0x0304)
            val result = input.toAudioBytes()

            assertEquals(4, result.size)
            assertEquals(0x02.toByte(), result[0])
            assertEquals(0x01.toByte(), result[1])
            assertEquals(0x04.toByte(), result[2])
            assertEquals(0x03.toByte(), result[3])
        }
    }

    /**
     * Creates a mock [AudioRecord] that continuously returns [data] on every read.
     * Suitable for shared flow tests where the upstream must keep emitting.
     */
    private fun createMockAudioRecordContinuous(data: ShortArray): AudioRecord =
        mockk(relaxed = true) {
            every { state } returns AudioRecord.STATE_INITIALIZED
            every { read(any<ShortArray>(), eq(0), any()) } answers {
                val buffer = firstArg<ShortArray>()
                data.copyInto(buffer)
                data.size
            }
        }
}
