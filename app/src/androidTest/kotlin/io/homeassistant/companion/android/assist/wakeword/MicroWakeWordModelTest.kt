package io.homeassistant.companion.android.assist.wakeword

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.homeassistant.companion.android.microwakeword.MicroWakeWord
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MicroWakeWordModelTest {

    private val appContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun loadsModelsFromAppAssets_verify_models_config_files() = runTest {
        val models = MicroWakeWordModelConfig.loadAvailableModels(appContext)

        assertTrue("Expected at least one wake word model", models.isNotEmpty())

        // Verify each model has required fields populated
        for (model in models) {
            assertTrue("Wake word should not be blank", model.wakeWord.isNotBlank())
            assertTrue("Author should not be blank", model.author.isNotBlank())
            assertTrue("Website should not be blank", model.website.isNotBlank())
            assertTrue("Model file name should not be blank", model.model.isNotBlank())
            assertTrue("Trained languages should not be empty", model.trainedLanguages.isNotEmpty())
            assertTrue("Version should be positive", model.version > 0)
            assertTrue("Probability cutoff should be between 0 and 1", model.micro.probabilityCutoff in 0f..1f)
            assertTrue("Sliding window size should be positive", model.micro.slidingWindowSize > 0)
            assertTrue("Feature step size should be positive", model.micro.featureStepSize > 0)
        }
    }

    @Test
    fun microWakeWord_loadsAndProcessesAudio_withAllModels() = runTest {
        val models = MicroWakeWordModelConfig.loadAvailableModels(appContext)

        for (model in models) {
            val modelBuffer = loadModelFile(appContext, model.modelAssetPath)
            val detector = MicroWakeWord(
                modelBuffer = modelBuffer,
                featureStepSizeMs = model.micro.featureStepSize,
                probabilityCutoff = model.micro.probabilityCutoff,
                slidingWindowSize = model.micro.slidingWindowSize,
            )

            detector.use { detector ->
                // Process silent audio (160 samples = 10ms at 16kHz)
                val silentAudio = ShortArray(160)
                val detected = detector.processAudio(silentAudio)

                // Silent audio should not trigger detection
                assertFalse(
                    "Silent audio should not trigger '${model.wakeWord}' detection",
                    detected,
                )
            }
        }
    }

    @Test
    fun microWakeWord_canResetState_withoutCrashing() = runTest {
        val models = MicroWakeWordModelConfig.loadAvailableModels(appContext)
        val model = models.first()

        val modelBuffer = loadModelFile(appContext, model.modelAssetPath)
        val detector = MicroWakeWord(
            modelBuffer = modelBuffer,
            featureStepSizeMs = model.micro.featureStepSize,
            probabilityCutoff = model.micro.probabilityCutoff,
            slidingWindowSize = model.micro.slidingWindowSize,
        )
        detector.use { detector ->
            // Process some audio
            detector.processAudio(ShortArray(160))
            detector.processAudio(ShortArray(160))

            // Reset should not crash
            detector.reset()

            // Should be able to process more audio after reset
            val detected = detector.processAudio(ShortArray(160))
            assertFalse(detected)
        }
    }

    private fun loadModelFile(context: Context, assetPath: String): ByteBuffer {
        val assetFd = context.assets.openFd(assetPath)
        val inputStream = assetFd.createInputStream()
        val fileChannel = inputStream.channel
        val mapped = fileChannel.map(FileChannel.MapMode.READ_ONLY, assetFd.startOffset, assetFd.declaredLength)
        mapped.order(ByteOrder.nativeOrder())
        return mapped
    }
}
