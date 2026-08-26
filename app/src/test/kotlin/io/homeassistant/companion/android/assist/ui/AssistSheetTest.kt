package io.homeassistant.companion.android.assist.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.assist.AssistViewModelBase.AssistInputMode
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class AssistSheetTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    private val singleServerPipelines = listOf(
        AssistUiPipeline(serverId = 1, serverName = "Home", id = "assist", name = "Preferred assistant"),
        AssistUiPipeline(serverId = 1, serverName = "Home", id = "custom", name = "Custom assistant"),
    )

    private val multiServerPipelines = listOf(
        AssistUiPipeline(serverId = 1, serverName = "Home", id = "assist", name = "Preferred assistant"),
        AssistUiPipeline(serverId = 2, serverName = "Vacation house", id = "assist", name = "Preferred assistant"),
    )

    private val conversation = listOf(
        AssistMessage("How can I assist?", isInput = false),
        AssistMessage("Turn on the living room lights", isInput = true),
    )

    @OptIn(ExperimentalMaterial3Api::class)
    private fun setAssistSheetContent(
        pipelines: List<AssistUiPipeline> = singleServerPipelines,
        inputMode: AssistInputMode? = AssistInputMode.TEXT,
        currentPipeline: AssistUiPipeline? = singleServerPipelines.first(),
        fromFrontend: Boolean = true,
        onSelectPipeline: (Int, String) -> Unit = { _, _ -> },
        onManagePipelines: (() -> Unit)? = null,
        onChangeInput: () -> Unit = {},
        onTextInput: (String) -> Unit = {},
        onMicrophoneInput: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            HAThemeForPreview {
                AssistSheet(
                    conversation = conversation,
                    pipelines = pipelines,
                    inputMode = inputMode,
                    currentPipeline = currentPipeline,
                    fromFrontend = fromFrontend,
                    onSelectPipeline = onSelectPipeline,
                    onManagePipelines = onManagePipelines,
                    onChangeInput = onChangeInput,
                    onTextInput = onTextInput,
                    onMicrophoneInput = onMicrophoneInput,
                    onHide = {},
                    bottomSheetState = remember {
                        SheetState(
                            skipPartiallyExpanded = true,
                            // Thresholds only affect drag gestures, which never happen in tests.
                            positionalThreshold = { 0f },
                            velocityThreshold = { 0f },
                            initialValue = SheetValue.Expanded,
                        )
                    },
                )
            }
        }
    }

    @Test
    fun `Given a conversation then the input and output messages are displayed`() {
        setAssistSheetContent()

        composeTestRule.apply {
            onNodeWithText("How can I assist?").assertIsDisplayed()
            onNodeWithText("Turn on the living room lights").assertIsDisplayed()
        }
    }

    @Test
    fun `Given text input when submitting with the send button then onTextInput is invoked and the field is cleared`() {
        var submittedText: String? = null
        setAssistSheetContent(onTextInput = { submittedText = it })

        composeTestRule.apply {
            onNode(hasSetTextAction()).performTextInput("Turn off the lights")
            onNodeWithContentDescription(stringResource(commonR.string.assist_send_text)).performClick()

            assertEquals("Turn off the lights", submittedText)
            // The placeholder is only composed while the field is empty.
            onNodeWithText(stringResource(commonR.string.assist_enter_a_request)).assertIsDisplayed()
        }
    }

    @Test
    fun `Given text input when submitting with the IME action then onTextInput is invoked`() {
        var submittedText: String? = null
        setAssistSheetContent(onTextInput = { submittedText = it })

        composeTestRule.apply {
            onNode(hasSetTextAction()).performTextInput("Turn off the lights")
            onNode(hasSetTextAction()).performImeAction()

            assertEquals("Turn off the lights", submittedText)
        }
    }

    @Test
    fun `Given text input with a blank field when the microphone button is clicked then onChangeInput is invoked`() {
        var changeInputCalled = false
        var textInputCalled = false
        setAssistSheetContent(
            onChangeInput = { changeInputCalled = true },
            onTextInput = { textInputCalled = true },
        )

        composeTestRule.apply {
            onNodeWithContentDescription(stringResource(commonR.string.assist_start_listening)).performClick()

            assertTrue("onChangeInput should be invoked", changeInputCalled)
            assertFalse("onTextInput should not be invoked", textInputCalled)
        }
    }

    @Test
    fun `Given text only input with a blank field then the send button is disabled`() {
        setAssistSheetContent(inputMode = AssistInputMode.TEXT_ONLY)

        composeTestRule.apply {
            onNodeWithContentDescription(stringResource(commonR.string.assist_send_text)).assertIsNotEnabled()
        }
    }

    @Test
    fun `Given voice inactive input when the microphone button is clicked then onMicrophoneInput is invoked`() {
        var microphoneInputCalled = false
        setAssistSheetContent(
            inputMode = AssistInputMode.VOICE_INACTIVE,
            onMicrophoneInput = { microphoneInputCalled = true },
        )

        composeTestRule.apply {
            onNodeWithContentDescription(stringResource(commonR.string.assist_start_listening)).performClick()

            assertTrue("onMicrophoneInput should be invoked", microphoneInputCalled)
        }
    }

    @Test
    fun `Given voice inactive input when the keyboard button is clicked then onChangeInput is invoked`() {
        var changeInputCalled = false
        setAssistSheetContent(
            inputMode = AssistInputMode.VOICE_INACTIVE,
            onChangeInput = { changeInputCalled = true },
        )

        composeTestRule.apply {
            onNodeWithContentDescription(stringResource(commonR.string.assist_enter_text)).performClick()

            assertTrue("onChangeInput should be invoked", changeInputCalled)
        }
    }

    @Test
    fun `Given voice active input then the microphone button offers to stop listening`() {
        var microphoneInputCalled = false
        setAssistSheetContent(
            inputMode = AssistInputMode.VOICE_ACTIVE,
            onMicrophoneInput = { microphoneInputCalled = true },
        )

        composeTestRule.apply {
            onNodeWithContentDescription(stringResource(commonR.string.assist_stop_listening)).performClick()

            assertTrue("onMicrophoneInput should be invoked", microphoneInputCalled)
        }
    }

    @Test
    fun `Given no input mode then no input controls are displayed`() {
        setAssistSheetContent(inputMode = null, currentPipeline = null)

        assertNoInputControls()
    }

    @Test
    fun `Given blocked input mode then no input controls are displayed`() {
        setAssistSheetContent(inputMode = AssistInputMode.BLOCKED)

        assertNoInputControls()
    }

    private fun assertNoInputControls() {
        composeTestRule.apply {
            onNode(hasSetTextAction()).assertDoesNotExist()
            onNodeWithContentDescription(stringResource(commonR.string.assist_start_listening)).assertDoesNotExist()
            onNodeWithContentDescription(stringResource(commonR.string.assist_enter_text)).assertDoesNotExist()
        }
    }

    @Test
    fun `Given multiple pipelines when another pipeline is selected then onSelectPipeline is invoked`() {
        var selectedServerId: Int? = null
        var selectedPipelineId: String? = null
        setAssistSheetContent(
            onSelectPipeline = { serverId, pipelineId ->
                selectedServerId = serverId
                selectedPipelineId = pipelineId
            },
        )

        composeTestRule.apply {
            onNodeWithText("Custom assistant").assertDoesNotExist()
            onNodeWithText("Preferred assistant").performClick()
            onNodeWithText("Custom assistant").performClick()

            assertEquals(1, selectedServerId)
            assertEquals("custom", selectedPipelineId)
        }
    }

    @Test
    fun `Given pipelines from multiple servers then the server name is shown in the selector`() {
        setAssistSheetContent(
            pipelines = multiServerPipelines,
            currentPipeline = multiServerPipelines.first(),
        )

        composeTestRule.onNodeWithText("Home: Preferred assistant").assertIsDisplayed()
    }

    @Test
    fun `Given onManagePipelines when the manage entry is clicked then it is invoked`() {
        var managePipelinesCalled = false
        setAssistSheetContent(onManagePipelines = { managePipelinesCalled = true })

        composeTestRule.apply {
            onNodeWithText("Preferred assistant").performClick()
            onNodeWithText(stringResource(commonR.string.assist_manage_pipelines)).performClick()

            assertTrue("onManagePipelines should be invoked", managePipelinesCalled)
        }
    }

    @Test
    fun `Given no onManagePipelines then the manage entry is not shown`() {
        setAssistSheetContent(onManagePipelines = null)

        composeTestRule.apply {
            onNodeWithText("Preferred assistant").performClick()
            onNodeWithText(stringResource(commonR.string.assist_manage_pipelines)).assertDoesNotExist()
        }
    }

    @Test
    fun `Given opened from the frontend then the Assist title is shown`() {
        setAssistSheetContent(fromFrontend = true)

        composeTestRule.apply {
            onNodeWithText(stringResource(commonR.string.assist)).assertIsDisplayed()
        }
    }

    @Test
    fun `Given opened from outside the app then the app name is shown as title`() {
        setAssistSheetContent(fromFrontend = false)

        composeTestRule.apply {
            onNodeWithText(stringResource(commonR.string.app_name)).assertIsDisplayed()
        }
    }
}
