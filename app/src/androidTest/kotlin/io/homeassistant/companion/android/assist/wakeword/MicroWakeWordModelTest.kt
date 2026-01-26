package io.homeassistant.companion.android.assist.wakeword

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.DataType
import org.tensorflow.lite.InterpreterApi

@RunWith(AndroidJUnit4::class)
class MicroWakeWordModelTest {

    @Test
    fun loadsModelsFromAppAssets_verify_models_config_files() = runTest {
        val models = MicroWakeWordModel.loadAvailableModels(InstrumentationRegistry.getInstrumentation().targetContext)

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
    fun loadsModelsFromAppAssets_verify_valid_tf_files() = runTest {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        TfLiteInitializerImpl().initialize(appContext)
        val models = MicroWakeWordModel.loadAvailableModels(appContext)

        for (model in models) {
            val modelBuffer = appContext.loadModelFile(model.modelAssetPath)
            val interpreter = InterpreterApi.create(
                modelBuffer,
                InterpreterApi.Options()
                    .setRuntime(InterpreterApi.Options.TfLiteRuntime.PREFER_SYSTEM_OVER_APPLICATION),
            )

            try {
                val inputTensor = interpreter.getInputTensor(0)
                val outputTensor = interpreter.getOutputTensor(0)

                // Verify input tensor: [1, 3, 40] - batch, stride, features (INT8)
                assertArrayEquals(
                    "Input shape for ${model.wakeWord} should be [1, 3, 40]",
                    intArrayOf(1, 3, 40),
                    inputTensor.shape(),
                )
                assertTrue(
                    "Input type for ${model.wakeWord} should be INT8",
                    inputTensor.dataType() == DataType.INT8,
                )

                // Verify output tensor: [1, 1] - single probability (UINT8)
                assertArrayEquals(
                    "Output shape for ${model.wakeWord} should be [1, 1]",
                    intArrayOf(1, 1),
                    outputTensor.shape(),
                )
                assertTrue(
                    "Output type for ${model.wakeWord} should be UINT8",
                    outputTensor.dataType() == DataType.UINT8,
                )
            } finally {
                interpreter.close()
            }
        }
    }

    private fun Context.loadModelFile(assetPath: String): MappedByteBuffer {
        val assetFileDescriptor = assets.openFd(assetPath)
        val inputStream = assetFileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}
