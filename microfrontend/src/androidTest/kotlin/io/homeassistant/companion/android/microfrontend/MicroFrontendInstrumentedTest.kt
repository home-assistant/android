package io.homeassistant.companion.android.microfrontend

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.random.Random
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MicroFrontendInstrumentedTest {

    @Test
    fun createProcessAndClose() {
        val microFrontend = MicroFrontend(stepSizeMs = 10)

        // Generate garbage audio data (1 second of random samples at 16kHz)
        val garbageData =
            ShortArray(16000) { Random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }

        val features = microFrontend.processSamples(garbageData)

        // Should produce some feature frames (16000 samples / 160 samples per 10ms step ≈ 100 frames)
        assertNotNull(features)
        assertTrue("Expected feature frames from 1 second of audio", features.isNotEmpty())

        // Each frame should have 40 mel bins
        features.forEach { frame ->
            assertTrue("Expected 40 mel bins per frame", frame.size == 40)
        }

        microFrontend.close()
    }
}
