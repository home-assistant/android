package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig

val microWakeWordModelConfigs = listOf(
    MicroWakeWordModelConfig(
        wakeWord = "Okay Nabu",
        author = "test",
        website = "https://test.com",
        model = "okay_nabu.tflite",
        trainedLanguages = listOf("en"),
        version = 1,
        micro = MicroWakeWordModelConfig.MicroFrontendConfig(
            probabilityCutoff = 0.5f,
            featureStepSize = 10,
            slidingWindowSize = 20,
        ),
    ),
        MicroWakeWordModelConfig(
            wakeWord = "Hey Jarvis",
            author = "test",
            website = "https://test.com",
            model = "hey_jarvis.tflite",
            trainedLanguages = listOf("en"),
            version = 1,
            micro = MicroWakeWordModelConfig.MicroFrontendConfig(
                probabilityCutoff = 0.5f,
                featureStepSize = 10,
                slidingWindowSize = 20,
            ),
        ),
    )
