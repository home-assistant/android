package io.homeassistant.companion.android.assist.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Check
import io.github.timoptr.mdiicons.generated.ChevronDown
import io.github.timoptr.mdiicons.generated.KeyboardOutline
import io.github.timoptr.mdiicons.generated.Microphone
import io.github.timoptr.mdiicons.generated.Send
import io.github.timoptr.mdiicons.rememberImageVector
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

// A text input adds the user message and the response placeholder at once, so the list anchor can
// lag at most two items behind the newest message while it is still at the bottom.
private const val SCROLL_SNAP_ITEM_THRESHOLD = 2

private const val TYPING_DOT_COUNT = 3
private const val TYPING_DOT_CYCLE_DURATION_MS = 1500
private const val TYPING_DOT_PULSE_DURATION_MS = 600
private const val TYPING_DOT_STAGGER_MS = 150
private const val TYPING_DOT_MIN_ALPHA = 0.4f
private const val TYPING_DOT_MIN_SCALE = 0.75f

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
 * @param initialInputText Initial content of the text input, exposed so screenshots can capture a
 * filled field.
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
    initialInputText: String = "",
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
            initialInputText = initialInputText,
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
    initialInputText: String,
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
            initialInputText = initialInputText,
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
                imageVector = Mdi.ChevronDown.rememberImageVector(),
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
                                imageVector = Mdi.Check.rememberImageVector(),
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
    LaunchedEffect(conversation.size) {
        if (conversation.isEmpty()) return@LaunchedEffect
        // Only animate scroll when not at the bottom of the list to avoid conflicting animations.
        if (lazyListState.firstVisibleItemIndex <= SCROLL_SNAP_ITEM_THRESHOLD) {
            lazyListState.scrollToItem(0)
        } else {
            lazyListState.animateScrollToItem(0)
        }
    }

    // Enter transitions are hoisted out of the lazy items: state remembered inside an item is lost
    // whenever the item leaves composition (scrolled away, or the sheet resizing), which would
    // replay the animation on messages that were already shown. In inspection mode (previews and
    // screenshot tests) bubbles start visible, otherwise the static frame would capture them
    // before they animated in.
    val startVisible = LocalInspectionMode.current
    val enterTransitions = remember { mutableMapOf<Int, MutableTransitionState<Boolean>>() }
    LaunchedEffect(conversation.size) {
        enterTransitions.keys.removeAll { it >= conversation.size }
    }

    // The list is reversed to anchor it to its bottom edge, so that while the newest bubble grows
    // (typing indicator being replaced by the response, or streaming updates) the end of the
    // message stays visible without having to coordinate scrolling with the size animation.
    // Keys stay the message's index in [conversation] so they are stable when messages are added.
    val reversedConversation = conversation.asReversed()
    LazyColumn(
        state = lazyListState,
        reverseLayout = true,
        modifier = modifier.padding(vertical = HADimens.SPACE4),
    ) {
        itemsIndexed(reversedConversation, key = { index, _ -> conversation.lastIndex - index }) { index, message ->
            SpeechBubble(
                message = message,
                enterTransition = enterTransitions.getOrPut(conversation.lastIndex - index) {
                    MutableTransitionState(startVisible).apply { targetState = true }
                },
                // The placement animation matches the enter animation of the new bubble, so a new
                // message and the older messages it pushes up move in lockstep.
                modifier = Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = tween(BUBBLE_ENTER_DURATION_MS),
                ),
            )
        }
    }
}

@Composable
private fun AssistSheetControls(
    inputMode: AssistInputMode?,
    onChangeInput: () -> Unit,
    onTextInput: (String) -> Unit,
    onMicrophoneInput: () -> Unit,
    initialInputText: String,
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
            initialInputText = initialInputText,
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
    initialInputText: String,
) {
    var text by rememberSaveable { mutableStateOf(initialInputText) }
    val submit = {
        if (text.isNotBlank()) {
            onTextInput(text)
            text = ""
        }
    }
    HATextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(stringResource(commonR.string.assist_enter_a_request)) },
        modifier = Modifier
            .weight(1f)
            .focusRequester(focusRequester),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { submit() }),
    )
    val inputIsSend = text.isNotBlank() || textOnly
    HAIconButton(
        icon = if (inputIsSend) {
            Mdi.Send.rememberImageVector(
                autoMirror = true,
            )
        } else {
            Mdi.Microphone.rememberImageVector()
        },
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
        icon = Mdi.KeyboardOutline.rememberImageVector(),
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
            MicrophoneRipples()
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
                imageVector = Mdi.Microphone.rememberImageVector(),
                contentDescription = stringResource(
                    if (isActive) commonR.string.assist_stop_listening else commonR.string.assist_start_listening,
                ),
                tint = if (isActive) colorScheme.colorOnPrimaryLoud else colorScheme.colorTextPrimary,
                modifier = Modifier.size(MIC_ICON_SIZE),
            )
        }
    }
}

/**
 * Ripples expanding and fading out from behind the microphone button while it is listening.
 */
@Composable
private fun MicrophoneRipples(modifier: Modifier = Modifier) {
    val colorScheme = LocalHAColorScheme.current
    // In inspection mode (previews and screenshot tests) the ripples are drawn at fixed points of
    // the cycle, otherwise the static frame would capture them at their invisible start.
    val transition = if (LocalInspectionMode.current) null else rememberInfiniteTransition(label = "micRipples")
    repeat(MIC_RIPPLE_COUNT) { index ->
        val progress = transition?.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(MIC_RIPPLE_DURATION_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(index * MIC_RIPPLE_DURATION_MS / MIC_RIPPLE_COUNT),
            ),
            label = "micRipple$index",
        ) ?: remember { mutableFloatStateOf((index + 1f) / (MIC_RIPPLE_COUNT + 1)) }
        Box(
            modifier = modifier
                .size(MIC_BUTTON_SIZE)
                .graphicsLayer {
                    val scale = 1f + (MIC_RIPPLE_MAX_SCALE - 1f) * progress.value
                    scaleX = scale
                    scaleY = scale
                    alpha = MIC_RIPPLE_START_ALPHA * (1f - progress.value)
                }
                .background(color = colorScheme.colorFillPrimaryLoudResting, shape = CircleShape),
        )
    }
}

@Composable
private fun SpeechBubble(
    message: AssistMessage,
    modifier: Modifier = Modifier,
    enterTransition: MutableTransitionState<Boolean> = remember { MutableTransitionState(true) },
) {
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

    AnimatedVisibility(
        visibleState = enterTransition,
        enter = fadeIn(tween(BUBBLE_ENTER_DURATION_MS)) +
            slideInVertically(tween(BUBBLE_ENTER_DURATION_MS)) { it },
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
                    .animateContentSize(tween(BUBBLE_ENTER_DURATION_MS))
                    .padding(horizontal = HADimens.SPACE3, vertical = HADimens.SPACE2),
            ) {
                // animateContentSize on the bubble animates its size for both the placeholder to
                // response swap and streaming text updates, so the AnimatedContent SizeTransform
                // is disabled to not animate the size twice.
                AnimatedContent(
                    targetState = message.isPlaceholder,
                    transitionSpec = {
                        (fadeIn(tween(BUBBLE_ENTER_DURATION_MS)) togetherWith fadeOut(tween(BUBBLE_ENTER_DURATION_MS)))
                            .using(null)
                    },
                    label = "bubbleContent",
                ) { isPlaceholder ->
                    if (isPlaceholder) {
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
}

/**
 * Three dots scaling and fading one after the other, with a rest between cycles, shown while
 * waiting for the other side of the conversation.
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
            val pulseStart = index * TYPING_DOT_STAGGER_MS
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = TYPING_DOT_CYCLE_DURATION_MS
                        0f at pulseStart
                        1f at pulseStart + TYPING_DOT_PULSE_DURATION_MS / 2
                        0f at pulseStart + TYPING_DOT_PULSE_DURATION_MS
                    },
                ),
                label = "typingDot$index",
            )
            Box(
                modifier = Modifier
                    .size(HASize.X2S)
                    .graphicsLayer {
                        alpha = TYPING_DOT_MIN_ALPHA + (1f - TYPING_DOT_MIN_ALPHA) * progress
                        val scale = TYPING_DOT_MIN_SCALE + (1f - TYPING_DOT_MIN_SCALE) * progress
                        scaleX = scale
                        scaleY = scale
                    }
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
