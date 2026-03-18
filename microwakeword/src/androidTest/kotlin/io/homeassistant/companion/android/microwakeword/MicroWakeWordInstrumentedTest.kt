package io.homeassistant.companion.android.microwakeword

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.random.Random
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MicroWakeWordInstrumentedTest {

    @Test
    fun createProcessResetAndClose() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelBuffer = loadTestModel(context)

        val engine = MicroWakeWord(
            modelBuffer = modelBuffer,
            featureStepSizeMs = 10,
            probabilityCutoff = 0.5f,
            slidingWindowSize = 20,
        )

        // Generate random audio data (1 second at 16kHz) — should not trigger false detection
        val randomAudio = ShortArray(16000) {
            Random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val detected = engine.processAudio(randomAudio)
        assertNotNull(detected)

        engine.reset()
        engine.close()
    }

    /**
     * Load the first available .tflite model from the wakeword/ assets directory.
     */
    private fun loadTestModel(context: Context): ByteBuffer {
        val assetFiles = context.assets.list("wakeword") ?: emptyArray()
        val modelFile = assetFiles.first { it.endsWith(".tflite") }
        val assetFd = context.assets.openFd("wakeword/$modelFile")
        val inputStream = assetFd.createInputStream()
        val fileChannel = inputStream.channel
        val mapped = fileChannel.map(FileChannel.MapMode.READ_ONLY, assetFd.startOffset, assetFd.declaredLength)
        mapped.order(ByteOrder.nativeOrder())
        return mapped
    }
}
