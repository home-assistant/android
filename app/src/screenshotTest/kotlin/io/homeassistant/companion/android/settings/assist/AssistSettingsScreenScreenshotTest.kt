package io.homeassistant.companion.android.settings.assist

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews

class AssistSettingsScreenScreenshotTest {

    private val testModels = listOf(
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

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Assist settings as default assistant with wake word enabled`() {
        HAThemeForPreview {
            AssistSettingsScreen(
                uiState = AssistSettingsUiState(
                    isLoading = false,
                    isDefaultAssistant = true,
                    isWakeWordEnabled = true,
                    selectedWakeWordModel = testModels[0],
                    availableModels = testModels,
                    isTestingWakeWord = false,
                    wakeWordDetected = false,
                ),
                hasAudioPermission = true,
                onSetDefaultAssistant = {},
                onToggleWakeWord = {},
                onSelectWakeWord = {},
                onStartTestWakeWord = {},
                onStopTestWakeWord = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Assist settings not default assistant`() {
        HAThemeForPreview {
            AssistSettingsScreen(
                uiState = AssistSettingsUiState(
                    isLoading = false,
                    isDefaultAssistant = false,
                    isWakeWordEnabled = false,
                    selectedWakeWordModel = testModels[0],
                    availableModels = testModels,
                ),
                hasAudioPermission = false,
                onSetDefaultAssistant = {},
                onToggleWakeWord = {},
                onSelectWakeWord = {},
                onStartTestWakeWord = {},
                onStopTestWakeWord = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Assist settings testing wake word with detection`() {
        HAThemeForPreview {
            AssistSettingsScreen(
                uiState = AssistSettingsUiState(
                    isLoading = false,
                    isDefaultAssistant = true,
                    isWakeWordEnabled = true,
                    selectedWakeWordModel = testModels[0],
                    availableModels = testModels,
                    isTestingWakeWord = true,
                    wakeWordDetected = true,
                ),
                hasAudioPermission = true,
                onSetDefaultAssistant = {},
                onToggleWakeWord = {},
                onSelectWakeWord = {},
                onStartTestWakeWord = {},
                onStopTestWakeWord = {},
            )
        }
    }
}
