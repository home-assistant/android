package io.homeassistant.companion.android.assist.wakeword

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        val models = MicroWakeWordModel.loadAvailableModels(appContext)

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
        TfLiteInitializerImpl().initialize(appContext)
        val models = MicroWakeWordModel.loadAvailableModels(appContext)

        for (model in models) {
            val detector = MicroWakeWord.create(appContext, model)

            try {
                // Process silent audio (160 samples = 10ms at 16kHz)
                val silentAudio = ShortArray(160)
                val detected = detector.processAudio(silentAudio)

                // Silent audio should not trigger detection
                assertFalse(
                    "Silent audio should not trigger '${model.wakeWord}' detection",
                    detected,
                )
            } finally {
                detector.close()
            }
        }
    }

    @Test
    fun microWakeWord_canResetState_withoutCrashing() = runTest {
        TfLiteInitializerImpl().initialize(appContext)
        val models = MicroWakeWordModel.loadAvailableModels(appContext)
        val model = models.first()

        val detector = MicroWakeWord.create(appContext, model)
        try {
            // Process some audio
            detector.processAudio(ShortArray(160))
            detector.processAudio(ShortArray(160))

            // Reset should not crash
            detector.reset()

            // Should be able to process more audio after reset
            val detected = detector.processAudio(ShortArray(160))
            assertFalse(detected)
        } finally {
            detector.close()
        }
    }
}
