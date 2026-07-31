package io.homeassistant.companion.android.assist.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.assist.AssistViewModelBase.AssistInputMode
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HAIconButton
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
import io.homeassistant.companion.android.common.compose.theme.HABorderWidth
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

private val MIC_BUTTON_CONTAINER_SIZE = HADimens.SPACE16
private val MIC_BUTTON_SIZE = HADimens.SPACE12
private val MIC_ICON_SIZE = HADimens.SPACE7
private const val MIC_RIPPLE_COUNT = 2
private const val MIC_RIPPLE_DURATION_MS = 1400
private const val MIC_RIPPLE_MAX_SCALE = 1.6f
private const val MIC_RIPPLE_START_ALPHA = 0.5f

private const val BUBBLE_ENTER_DURATION_MS = 220

private const val TYPING_DOT_COUNT = 3
private const val TYPING_DOT_PULSE_DURATION_MS = 300
private const val TYPING_DOT_MIN_ALPHA = 0.3f

/**
 * Modal bottom sheet hosting the Assist conversation: a header with the pipeline selector, the
 * conversation history, and the text/voice input controls.
 *
 * @param conversation Messages exchanged so far, oldest first.
 * @param pipelines All pipelines the user can select from, across servers.
 * @param inputMode Current input mode, or null while the pipeline is loading.
 * @param currentPipeline The pipeline in use, or null while loading.
 * @param fromFrontend Whether Assist was opened from the frontend, used to pick the sheet title.
 * @param onSelectPipeline Invoked with the server ID and pipeline ID of the selected pipeline.
 * @param onManagePipelines Invoked when the user opens pipeline management, or null to hide the entry.
 * @param onChangeInput Invoked when the user toggles between text and voice input.
 * @param onTextInput Invoked with the text the user submitted.
 * @param onMicrophoneInput Invoked when the user taps the microphone button.
 * @param onHide Invoked when the user dismisses the sheet.
 * @param bottomSheetState State of the sheet, exposed so tests can provide an already expanded state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistSheet(
    conversation: List<AssistMessage>,
    pipelines: List<AssistUiPipeline>,
    inputMode: AssistInputMode?,
    currentPipeline: AssistUiPipeline?,
    fromFrontend: Boolean,
    onSelectPipeline: (Int, String) -> Unit,
    onManagePipelines: (() -> Unit)?,
    onChangeInput: () -> Unit,
    onTextInput: (String) -> Unit,
    onMicrophoneInput: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    bottomSheetState: SheetState = rememberHAModalBottomSheetState(skipPartiallyExpanded = true),
) {
    HAModalBottomSheet(
        bottomSheetState = bottomSheetState,
        onDismissRequest = onHide,
        dragHandle = {},
        modifier = modifier,
    ) {
        AssistSheetContent(
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
        )
    }
}

@Composable
private fun AssistSheetContent(
    conversation: List<AssistMessage>,
    pipelines: List<AssistUiPipeline>,
    inputMode: AssistInputMode?,
    currentPipeline: AssistUiPipeline?,
    fromFrontend: Boolean,
    onSelectPipeline: (Int, String) -> Unit,
    onManagePipelines: (() -> Unit)?,
    onChangeInput: () -> Unit,
    onTextInput: (String) -> Unit,
    onMicrophoneInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HADimens.SPACE4)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .imePadding(),
    ) {
        AssistSheetHeader(
            pipelines = pipelines,
            currentPipeline = currentPipeline,
            fromFrontend = fromFrontend,
            onSelectPipeline = onSelectPipeline,
            onManagePipelines = onManagePipelines,
            modifier = Modifier.padding(top = HADimens.SPACE4),
        )
        AssistConversation(
            conversation = conversation,
            modifier = Modifier.weight(1f, fill = false),
        )
        AssistSheetControls(
            inputMode = inputMode,
            onChangeInput = onChangeInput,
            onTextInput = onTextInput,
            onMicrophoneInput = onMicrophoneInput,
        )
        Spacer(modifier = Modifier.height(HADimens.SPACE2))
    }
}

@Composable
private fun AssistSheetHeader(
    pipelines: List<AssistUiPipeline>,
    currentPipeline: AssistUiPipeline?,
    fromFrontend: Boolean,
    onSelectPipeline: (Int, String) -> Unit,
    onManagePipelines: (() -> Unit)?,
    modifier: Modifier = Modifier,
) = Column(verticalArrangement = Arrangement.Center, modifier = modifier) {
    Text(
        text = stringResource(if (fromFrontend) commonR.string.assist else commonR.string.app_name),
        style = HATextStyle.HeadlineMedium.copy(textAlign = TextAlign.Start),
    )
    if (currentPipeline != null) {
        AssistPipelineSelector(
            pipelines = pipelines,
            currentPipeline = currentPipeline,
            onSelectPipeline = onSelectPipeline,
            onManagePipelines = onManagePipelines,
        )
    }
}

@Composable
private fun AssistPipelineSelector(
    pipelines: List<AssistUiPipeline>,
    currentPipeline: AssistUiPipeline,
    onSelectPipeline: (Int, String) -> Unit,
    onManagePipelines: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = LocalHAColorScheme.current
    Box(modifier = modifier) {
        var showPipelineList by remember { mutableStateOf(false) }
        val showServerName = remember(pipelines) { pipelines.distinctBy { it.serverId }.size > 1 }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(HARadius.S))
                .clickable(role = Role.DropdownList) { showPipelineList = !showPipelineList },
        ) {
            Text(
                text = pipelineDisplayName(currentPipeline, showServerName),
                style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(commonR.string.assist_change_pipeline),
                tint = colorScheme.colorTextSecondary,
                modifier = Modifier
                    .padding(start = HADimens.SPACE1)
                    .size(HADimens.SPACE4),
            )
        }

        DropdownMenu(
            expanded = showPipelineList,
            onDismissRequest = { showPipelineList = false },
            containerColor = colorScheme.colorSurfaceDefault,
            shape = RoundedCornerShape(HARadius.XL),
        ) {
            pipelines.forEach { pipeline ->
                val isSelected = pipeline.serverId == currentPipeline.serverId && pipeline.id == currentPipeline.id
                DropdownMenuItem(
                    text = {
                        Text(
                            text = pipelineDisplayName(pipeline, showServerName),
                            style = HATextStyle.Body,
                            color = colorScheme.colorTextPrimary,
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = colorScheme.colorFillPrimaryLoudResting,
                                modifier = Modifier.size(HADimens.SPACE5),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onSelectPipeline(pipeline.serverId, pipeline.id)
                        showPipelineList = false
                    },
                )
            }
            if (onManagePipelines != null) {
                HAHorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(commonR.string.assist_manage_pipelines),
                            style = HATextStyle.Body,
                            color = colorScheme.colorTextPrimary,
                        )
                    },
                    onClick = onManagePipelines,
                )
            }
        }
    }
}

private fun pipelineDisplayName(pipeline: AssistUiPipeline, showServerName: Boolean): String =
    if (showServerName) "${pipeline.serverName}: ${pipeline.name}" else pipeline.name

@Composable
private fun AssistConversation(conversation: List<AssistMessage>, modifier: Modifier = Modifier) {
    val lazyListState = rememberLazyListState()
    LaunchedEffect(conversation.size, conversation.lastOrNull()?.message?.length) {
        lazyListState.animateScrollToItem(conversation.size)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.padding(vertical = HADimens.SPACE4),
    ) {
        itemsIndexed(conversation, key = { index, _ -> index }) { _, message ->
            SpeechBubble(message = message, modifier = Modifier.animateItem())
        }
    }
}

@Composable
private fun AssistSheetControls(
    inputMode: AssistInputMode?,
    onChangeInput: () -> Unit,
    onTextInput: (String) -> Unit,
    onMicrophoneInput: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
    if (inputMode == null) { // Pipeline info has not yet loaded, empty space for now
        Spacer(modifier = Modifier.height(HADimens.SPACE16))
        return@Row
    }

    if (inputMode == AssistInputMode.BLOCKED) { // No info and not recoverable, no space
        return@Row
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(inputMode) {
        if (inputMode == AssistInputMode.TEXT || inputMode == AssistInputMode.TEXT_ONLY) {
            focusRequester.requestFocus()
        }
    }

    if (inputMode == AssistInputMode.TEXT || inputMode == AssistInputMode.TEXT_ONLY) {
        AssistTextInput(
            textOnly = inputMode == AssistInputMode.TEXT_ONLY,
            focusRequester = focusRequester,
            onChangeInput = onChangeInput,
            onTextInput = onTextInput,
        )
    } else {
        AssistVoiceInput(
            isActive = inputMode == AssistInputMode.VOICE_ACTIVE,
            onChangeInput = onChangeInput,
            onMicrophoneInput = onMicrophoneInput,
        )
    }
}

@Composable
private fun RowScope.AssistTextInput(
    textOnly: Boolean,
    focusRequester: FocusRequester,
    onChangeInput: () -> Unit,
    onTextInput: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) {
            onTextInput(text)
            text = ""
        }
    }
    HATextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(commonR.string.assist_enter_a_request)) },
        modifier = Modifier
            .weight(1f)
            .focusRequester(focusRequester),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { submit() }),
    )
    val inputIsSend = text.isNotBlank() || textOnly
    HAIconButton(
        icon = if (inputIsSend) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic,
        contentDescription = stringResource(
            if (inputIsSend) commonR.string.assist_send_text else commonR.string.assist_start_listening,
        ),
        onClick = {
            if (text.isNotBlank()) {
                submit()
            } else if (!textOnly) {
                onChangeInput()
            }
        },
        enabled = !textOnly || text.isNotBlank(),
        variant = ButtonVariant.PRIMARY,
    )
}

@Composable
private fun RowScope.AssistVoiceInput(isActive: Boolean, onChangeInput: () -> Unit, onMicrophoneInput: () -> Unit) {
    Spacer(modifier = Modifier.size(HADimens.SPACE12))
    Spacer(modifier = Modifier.weight(0.5f))
    AssistMicrophoneButton(isActive = isActive, onClick = onMicrophoneInput)
    Spacer(modifier = Modifier.weight(0.5f))
    HAIconButton(
        icon = Icons.Outlined.Keyboard,
        contentDescription = stringResource(commonR.string.assist_enter_text),
        onClick = onChangeInput,
        variant = ButtonVariant.NEUTRAL,
    )
}

@Composable
private fun AssistMicrophoneButton(isActive: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colorScheme = LocalHAColorScheme.current
    Box(
        modifier = modifier.size(MIC_BUTTON_CONTAINER_SIZE),
        contentAlignment = Alignment.Center,
    ) {
        if (isActive) {
            val transition = rememberInfiniteTransition(label = "micRipples")
            repeat(MIC_RIPPLE_COUNT) { index ->
                val progress by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(MIC_RIPPLE_DURATION_MS, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                        initialStartOffset = StartOffset(index * MIC_RIPPLE_DURATION_MS / MIC_RIPPLE_COUNT),
                    ),
                    label = "micRipple$index",
                )
                Box(
                    modifier = Modifier
                        .size(MIC_BUTTON_SIZE)
                        .graphicsLayer {
                            val scale = 1f + (MIC_RIPPLE_MAX_SCALE - 1f) * progress
                            scaleX = scale
                            scaleY = scale
                            alpha = MIC_RIPPLE_START_ALPHA * (1f - progress)
                        }
                        .background(color = colorScheme.colorFillPrimaryLoudResting, shape = CircleShape),
                )
            }
        }
        val buttonBackground = if (isActive) {
            Modifier.background(color = colorScheme.colorFillPrimaryLoudResting, shape = CircleShape)
        } else {
            Modifier.border(
                width = HABorderWidth.S,
                color = colorScheme.colorBorderNeutralNormal,
                shape = CircleShape,
            )
        }
        Box(
            modifier = Modifier
                .size(MIC_BUTTON_SIZE)
                .clip(CircleShape)
                .then(buttonBackground)
                .clickable(role = Role.Button) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = stringResource(
                    if (isActive) commonR.string.assist_stop_listening else commonR.string.assist_start_listening,
                ),
                tint = if (isActive) colorScheme.colorOnPrimaryLoud else colorScheme.colorTextPrimary,
                modifier = Modifier.size(MIC_ICON_SIZE),
            )
        }
    }
}

@Composable
private fun SpeechBubble(message: AssistMessage, modifier: Modifier = Modifier) {
    val colorScheme = LocalHAColorScheme.current
    val isInput = message.isInput
    val backgroundColor = when {
        message.isError -> colorScheme.colorFillDangerLoudResting
        isInput -> colorScheme.colorFillPrimaryLoudResting
        else -> colorScheme.colorFillPrimaryQuietResting
    }
    val contentColor = when {
        message.isError -> colorScheme.colorOnDangerLoud
        isInput -> colorScheme.colorOnPrimaryLoud
        else -> colorScheme.colorTextPrimary
    }

    // Animate each bubble in once, when it first appears in the conversation.
    // In inspection mode (previews and screenshot tests) start visible, otherwise the static
    // frame would capture the bubble before it animated in.
    val startVisible = LocalInspectionMode.current
    val enterTransition = remember { MutableTransitionState(startVisible).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = enterTransition,
        enter = fadeIn(tween(BUBBLE_ENTER_DURATION_MS)) +
            slideInVertically(tween(BUBBLE_ENTER_DURATION_MS)) { it / 2 },
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = if (isInput) Arrangement.End else Arrangement.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (isInput) HADimens.SPACE6 else HADimens.SPACE0,
                    end = if (isInput) HADimens.SPACE0 else HADimens.SPACE6,
                    top = HADimens.SPACE2,
                    bottom = HADimens.SPACE2,
                ),
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = backgroundColor,
                        shape = RoundedCornerShape(
                            topStart = HARadius.XL,
                            topEnd = HARadius.XL,
                            bottomStart = if (isInput) HARadius.XL else HARadius.S,
                            bottomEnd = if (isInput) HARadius.S else HARadius.XL,
                        ),
                    )
                    .padding(horizontal = HADimens.SPACE3, vertical = HADimens.SPACE2),
            ) {
                if (message.isPlaceholder) {
                    TypingIndicator(color = contentColor)
                } else {
                    Text(
                        text = message.message,
                        style = HATextStyle.Body.copy(textAlign = TextAlign.Start, color = contentColor),
                    )
                }
            }
        }
    }
}

/**
 * Three dots pulsing one after the other, shown while waiting for the other side of the
 * conversation, mirroring the typing indicator on iOS.
 */
@Composable
private fun TypingIndicator(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typingIndicator")
    Row(
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.heightIn(min = HADimens.SPACE6),
    ) {
        repeat(TYPING_DOT_COUNT) { index ->
            val alpha by transition.animateFloat(
                initialValue = TYPING_DOT_MIN_ALPHA,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(TYPING_DOT_PULSE_DURATION_MS * TYPING_DOT_COUNT),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * TYPING_DOT_PULSE_DURATION_MS),
                ),
                label = "typingDotAlpha$index",
            )
            Box(
                modifier = Modifier
                    .size(HASize.X2S)
                    .graphicsLayer { this.alpha = alpha }
                    .background(color = color, shape = CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun AssistSheetContentPreview() {
    HAThemeForPreview(modifier = Modifier.fillMaxSize()) {
        AssistSheet(
            conversation = listOf(
                AssistMessage("How can I assist?", isInput = false),
                AssistMessage("Turn on the living room lights", isInput = true),
                AssistMessage.placeholder(isInput = false),
            ),
            pipelines = emptyList(),
            inputMode = AssistInputMode.VOICE_INACTIVE,
            currentPipeline = AssistUiPipeline(serverId = 0, serverName = "Home", id = "pipeline", name = "Assist"),
            fromFrontend = true,
            onSelectPipeline = { _, _ -> },
            onManagePipelines = null,
            onChangeInput = {},
            onTextInput = {},
            onMicrophoneInput = {},
            onHide = {},
        )
    }
}

@Preview
@Composable
private fun SpeechBubblePreview() {
    HAThemeForPreview {
        Column {
            SpeechBubble(message = AssistMessage("How can I assist?", isInput = false))
            SpeechBubble(message = AssistMessage("Turn on the lights", isInput = true))
            SpeechBubble(message = AssistMessage("Something went wrong", isInput = false, isError = true))
            SpeechBubble(message = AssistMessage.placeholder(isInput = false))
        }
    }
}
