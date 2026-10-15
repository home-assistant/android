package io.homeassistant.companion.android.settings.assist

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.testing.unit.stringResource
import io.homeassistant.companion.android.util.microWakeWordModelConfigs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class AssistSettingsScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given loading state then the settings sections are not displayed`() {
        setContent(AssistSettingsUiState(isLoading = true))

        composeTestRule.onNodeWithText(string(commonR.string.assist_default_assistant_title)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(commonR.string.assist_listening_chime_enable)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(commonR.string.assist_wake_word_title)).assertDoesNotExist()
    }

    @Test
    fun `Given loaded state then the main sections are displayed`() {
        setContent(AssistSettingsUiState(isLoading = false))

        composeTestRule.onNodeWithText(string(commonR.string.assist_default_assistant_title)).assertExists()
        composeTestRule.onNodeWithText(string(commonR.string.assist_listening_chime_enable)).assertExists()
        composeTestRule.onNodeWithText(string(commonR.string.assist_wake_word_title)).assertExists()
    }

    @Test
    fun `Given listening chime disabled when the chime row is clicked then onToggleListeningChime is invoked with true`() {
        var toggledTo: Boolean? = null
        setContent(
            AssistSettingsUiState(isLoading = false, isListeningChimeEnabled = false),
            onToggleListeningChime = { toggledTo = it },
        )

        composeTestRule.onNodeWithText(string(commonR.string.assist_listening_chime_enable)).performClick()

        assertEquals(true, toggledTo)
    }

    @Test
    fun `Given listening chime enabled when the chime row is clicked then onToggleListeningChime is invoked with false`() {
        var toggledTo: Boolean? = null
        setContent(
            AssistSettingsUiState(isLoading = false, isListeningChimeEnabled = true),
            onToggleListeningChime = { toggledTo = it },
        )

        composeTestRule.onNodeWithText(string(commonR.string.assist_listening_chime_enable)).performClick()

        assertEquals(false, toggledTo)
    }

    @Test
    fun `Given not the default assistant then the set default button is shown and invokes onSetDefaultAssistant`() {
        var setDefaultCalled = false
        setContent(
            AssistSettingsUiState(isLoading = false, isDefaultAssistant = false),
            onSetDefaultAssistant = { setDefaultCalled = true },
        )

        composeTestRule.onNodeWithText(string(commonR.string.assist_default_assistant_disabled)).assertExists()
        composeTestRule.onNodeWithText(string(commonR.string.assist_set_default)).performScrollTo().performClick()

        assertEquals(true, setDefaultCalled)
    }

    @Test
    fun `Given the default assistant then the enabled text is shown and the set default button is hidden`() {
        setContent(AssistSettingsUiState(isLoading = false, isDefaultAssistant = true))

        composeTestRule.onNodeWithText(string(commonR.string.assist_default_assistant_enabled)).assertExists()
        composeTestRule.onNodeWithText(string(commonR.string.assist_set_default)).assertDoesNotExist()
    }

    @Test
    fun `Given the default assistant when the wake word row is clicked then onToggleWakeWord is invoked`() {
        var toggledTo: Boolean? = null
        setContent(
            AssistSettingsUiState(isLoading = false, isDefaultAssistant = true, isWakeWordEnabled = false),
            onToggleWakeWord = { toggledTo = it },
        )

        composeTestRule.onNodeWithText(string(commonR.string.assist_wake_word_enable)).performScrollTo().performClick()

        assertEquals(true, toggledTo)
    }

    @Test
    fun `Given not the default assistant when the wake word row is clicked then onToggleWakeWord is not invoked`() {
        var toggledTo: Boolean? = null
        setContent(
            AssistSettingsUiState(isLoading = false, isDefaultAssistant = false),
            onToggleWakeWord = { toggledTo = it },
        )

        composeTestRule.onNodeWithText(string(commonR.string.assist_wake_word_enable)).performScrollTo().performClick()

        assertNull("onToggleWakeWord must not fire while the row is disabled", toggledTo)
    }

    @Test
    fun `Given wake word enabled then the model selector battery warning and test button are displayed`() {
        setContent(
            AssistSettingsUiState(
                isLoading = false,
                isDefaultAssistant = true,
                isWakeWordEnabled = true,
                selectedWakeWordModel = microWakeWordModelConfigs[0],
                availableModels = microWakeWordModelConfigs,
            ),
            hasAudioPermission = true,
        )

        // The selected model name only appears in the selector, unlike the "Wake word" label which
        // is also the section title.
        composeTestRule.onNodeWithText(microWakeWordModelConfigs[0].wakeWord).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(commonR.string.assist_wake_word_battery_warning)).assertExists()
        composeTestRule.onNodeWithText(string(commonR.string.assist_wake_word_test)).assertExists()
    }

    @Test
    fun `Given wake word disabled then the model selector and test button are hidden`() {
        setContent(
            AssistSettingsUiState(
                isLoading = false,
                isDefaultAssistant = true,
                isWakeWordEnabled = false,
                selectedWakeWordModel = microWakeWordModelConfigs[0],
                availableModels = microWakeWordModelConfigs,
            ),
        )

        composeTestRule.onNodeWithText(microWakeWordModelConfigs[0].wakeWord).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(commonR.string.assist_wake_word_test)).assertDoesNotExist()
    }

    @Test
    fun `Given not testing when the test button is clicked then onStartTestWakeWord is invoked`() {
        var startCalled = false
        setContent(
            AssistSettingsUiState(
                isLoading = false,
                isDefaultAssistant = true,
                isWakeWordEnabled = true,
                selectedWakeWordModel = microWakeWordModelConfigs[0],
                availableModels = microWakeWordModelConfigs,
                isTestingWakeWord = false,
            ),
            onStartTestWakeWord = { startCalled = true },
        )

        composeTestRule.onNodeWithText(string(commonR.string.assist_wake_word_test)).performScrollTo().performClick()

        assertEquals(true, startCalled)
    }

    @Test
    fun `Given testing when the stop test button is clicked then onStopTestWakeWord is invoked`() {
        var stopCalled = false
        setContent(
            AssistSettingsUiState(
                isLoading = false,
                isDefaultAssistant = true,
                isWakeWordEnabled = true,
                selectedWakeWordModel = microWakeWordModelConfigs[0],
                availableModels = microWakeWordModelConfigs,
                isTestingWakeWord = true,
            ),
            onStopTestWakeWord = { stopCalled = true },
        )

        composeTestRule.onNodeWithText(string(commonR.string.assist_wake_word_stop_test)).performScrollTo().performClick()

        assertEquals(true, stopCalled)
    }

    @Test
    fun `Given a wake word is detected then the detected text is displayed`() {
        setContent(
            AssistSettingsUiState(
                isLoading = false,
                isDefaultAssistant = true,
                isWakeWordEnabled = true,
                selectedWakeWordModel = microWakeWordModelConfigs[0],
                availableModels = microWakeWordModelConfigs,
                isTestingWakeWord = true,
                wakeWordDetected = true,
            ),
        )

        composeTestRule.onNodeWithText(string(commonR.string.assist_wake_word_detected)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Given the model selector when another model is chosen then onSelectWakeWord is invoked with it`() {
        var selected: MicroWakeWordModelConfig? = null
        setContent(
            AssistSettingsUiState(
                isLoading = false,
                isDefaultAssistant = true,
                isWakeWordEnabled = true,
                selectedWakeWordModel = microWakeWordModelConfigs[0],
                availableModels = microWakeWordModelConfigs,
            ),
            onSelectWakeWord = { selected = it },
        )

        // Open the collapsed field (showing the current model) then pick the other one.
        composeTestRule.onNodeWithText(microWakeWordModelConfigs[0].wakeWord).performScrollTo().performClick()
        composeTestRule.onNodeWithText(microWakeWordModelConfigs[1].wakeWord).performClick()

        assertEquals(microWakeWordModelConfigs[1], selected)
    }

    private fun string(@StringRes id: Int): String = composeTestRule.stringResource(id)

    private fun setContent(
        uiState: AssistSettingsUiState,
        hasAudioPermission: Boolean = true,
        onSetDefaultAssistant: () -> Unit = {},
        onToggleListeningChime: (Boolean) -> Unit = {},
        onToggleWakeWord: (Boolean) -> Unit = {},
        onSelectWakeWord: (MicroWakeWordModelConfig) -> Unit = {},
        onStartTestWakeWord: () -> Unit = {},
        onStopTestWakeWord: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            HAThemeForPreview {
                AssistSettingsContent(
                    uiState = uiState,
                    hasAudioPermission = hasAudioPermission,
                    onSetDefaultAssistant = onSetDefaultAssistant,
                    onToggleListeningChime = onToggleListeningChime,
                    onToggleWakeWord = onToggleWakeWord,
                    onSelectWakeWord = onSelectWakeWord,
                    onStartTestWakeWord = onStartTestWakeWord,
                    onStopTestWakeWord = onStopTestWakeWord,
                )
            }
        }
    }
}
