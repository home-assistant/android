package io.homeassistant.companion.android.assist.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.assist.AssistViewModelBase.AssistInputMode
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews

class AssistSheetScreenshotTest {

    private val singleServerPipelines = listOf(
        AssistUiPipeline(serverId = 1, serverName = "Home", id = "assist", name = "Home Assistant"),
        AssistUiPipeline(serverId = 1, serverName = "Home", id = "custom", name = "Custom assistant"),
    )

    private val multiServerPipelines = listOf(
        AssistUiPipeline(serverId = 1, serverName = "Home", id = "assist", name = "Home Assistant"),
        AssistUiPipeline(serverId = 2, serverName = "Vacation house", id = "assist", name = "Home Assistant"),
    )

    private val shortConversation = listOf(
        AssistMessage("How can I assist?", isInput = false),
        AssistMessage("Turn on the living room lights", isInput = true),
        AssistMessage("Turned on the lights", isInput = false),
    )

    private val longAnswerConversation = listOf(
        AssistMessage("How can I assist?", isInput = false),
        AssistMessage("Tell me everything about lorem ipsum", isInput = true),
        AssistMessage(
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut " +
                "labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco " +
                "laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in " +
                "voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat " +
                "cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. " +
                "Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque " +
                "laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi " +
                "architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit " +
                "aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione " +
                "voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit " +
                "amet, consectetur, adipisci velit, sed quia non numquam eius modi tempora incidunt ut " +
                "labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum " +
                "exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi " +
                "consequatur. Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam " +
                "nihil molestiae consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur.",
            isInput = false,
        ),
    )

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Assist conversation with voice input`() {
        AssistSheetScreenshot(
            conversation = shortConversation,
            inputMode = AssistInputMode.VOICE_INACTIVE,
        )
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Assist listening`() {
        AssistSheetScreenshot(
            conversation = listOf(AssistMessage("How can I assist?", isInput = false)),
            inputMode = AssistInputMode.VOICE_ACTIVE,
        )
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Assist waiting for a response`() {
        AssistSheetScreenshot(
            conversation = listOf(
                AssistMessage("How can I assist?", isInput = false),
                AssistMessage("Turn on the living room lights", isInput = true),
                AssistMessage.placeholder(isInput = false),
            ),
            inputMode = AssistInputMode.TEXT,
        )
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Assist text input with pipelines from multiple servers`() {
        AssistSheetScreenshot(
            conversation = shortConversation,
            inputMode = AssistInputMode.TEXT,
            pipelines = multiServerPipelines,
            currentPipeline = multiServerPipelines.first(),
        )
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Assist error response`() {
        AssistSheetScreenshot(
            conversation = listOf(
                AssistMessage("How can I assist?", isInput = false),
                AssistMessage("Turn on the living room lights", isInput = true),
                AssistMessage("Something went wrong", isInput = false, isError = true),
            ),
            inputMode = AssistInputMode.VOICE_INACTIVE,
        )
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Assist blocked without pipeline`() {
        AssistSheetScreenshot(
            conversation = listOf(AssistMessage("Blocked", isInput = false)),
            inputMode = AssistInputMode.BLOCKED,
            pipelines = emptyList(),
            currentPipeline = null,
        )
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Assist long answer filling the screen`() {
        AssistSheetScreenshot(
            conversation = longAnswerConversation,
            inputMode = AssistInputMode.VOICE_INACTIVE,
        )
    }

    /**
     * Renders [AssistSheet] with a [SheetState] already settled at [SheetValue.Expanded]: the
     * default state starts hidden or partially expanded, which a static frame would capture as a
     * closed or half-open sheet.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AssistSheetScreenshot(
        conversation: List<AssistMessage>,
        inputMode: AssistInputMode?,
        pipelines: List<AssistUiPipeline> = singleServerPipelines,
        currentPipeline: AssistUiPipeline? = singleServerPipelines.first(),
    ) {
        HAThemeForPreview(modifier = Modifier.fillMaxSize()) {
            AssistSheet(
                conversation = conversation,
                pipelines = pipelines,
                inputMode = inputMode,
                currentPipeline = currentPipeline,
                fromFrontend = true,
                onSelectPipeline = { _, _ -> },
                onManagePipelines = null,
                onChangeInput = {},
                onTextInput = {},
                onMicrophoneInput = {},
                onHide = {},
                bottomSheetState = remember {
                    SheetState(
                        skipPartiallyExpanded = true,
                        // Thresholds only affect drag gestures, which never happen in screenshots.
                        positionalThreshold = { 0f },
                        velocityThreshold = { 0f },
                        initialValue = SheetValue.Expanded,
                    )
                },
            )
        }
    }
}
