package io.homeassistant.companion.android.microwakeword

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MicroWakeWordInstrumentedTest {

    @Test
    fun createWithInvalidModelThrows() {
        val invalidModel = ByteBuffer.allocateDirect(64)
        assertThrows(IllegalStateException::class.java) {
            MicroWakeWord(
                modelBuffer = invalidModel,
                featureStepSizeMs = 10,
                probabilityCutoff = 0.5f,
                slidingWindowSize = 20,
            )
        }
    }

    @Test
    fun createWithEmptyModelThrows() {
        val emptyModel = ByteBuffer.allocateDirect(0)
        assertThrows(IllegalStateException::class.java) {
            MicroWakeWord(
                modelBuffer = emptyModel,
                featureStepSizeMs = 10,
                probabilityCutoff = 0.5f,
                slidingWindowSize = 20,
            )
        }
    }

    @Test
    fun createWithZeroSlidingWindowSizeThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            MicroWakeWord(
                modelBuffer = ByteBuffer.allocateDirect(64),
                featureStepSizeMs = 10,
                probabilityCutoff = 0.5f,
                slidingWindowSize = 0,
            )
        }
    }

    @Test
    fun createWithNegativeFeatureStepSizeThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            MicroWakeWord(
                modelBuffer = ByteBuffer.allocateDirect(64),
                featureStepSizeMs = -1,
                probabilityCutoff = 0.5f,
                slidingWindowSize = 20,
            )
        }
    }

    @Test
    fun createWithProbabilityCutoffOutOfRangeThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            MicroWakeWord(
                modelBuffer = ByteBuffer.allocateDirect(64),
                featureStepSizeMs = 10,
                probabilityCutoff = 1.5f,
                slidingWindowSize = 20,
            )
        }
    }

    @Test
    fun createWithNonDirectBufferThrows() {
        val heapBuffer = ByteBuffer.allocate(64)
        assertThrows(IllegalArgumentException::class.java) {
            MicroWakeWord(
                modelBuffer = heapBuffer,
                featureStepSizeMs = 10,
                probabilityCutoff = 0.5f,
                slidingWindowSize = 20,
            )
        }
    }
}
