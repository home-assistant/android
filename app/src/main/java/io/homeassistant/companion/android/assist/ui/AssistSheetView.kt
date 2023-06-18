package io.homeassistant.companion.android.assist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.assist.AssistViewModel
import kotlinx.coroutines.launch
import io.homeassistant.companion.android.common.R as commonR

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AssistSheetView(
    conversation: List<AssistMessage>,
    pipelines: List<AssistUiPipeline>,
    inputMode: AssistViewModel.AssistInputMode?,
    currentPipeline: AssistUiPipeline?,
    fromFrontend: Boolean,
    onSelectPipeline: (Int, String) -> Unit,
    onChangeInput: () -> Unit,
    onTextInput: (String) -> Unit,
    onMicrophoneInput: () -> Unit,
    onHide: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Expanded,
        skipHalfExpanded = true,
        confirmValueChange = {
            if (it == ModalBottomSheetValue.Hidden) {
                coroutineScope.launch { onHide() }
            }
            true
        }
    )
    val configuration = LocalConfiguration.current

    val sheetCornerRadius = dimensionResource(R.dimen.bottom_sheet_corner_radius)

    ModalBottomSheetLayout(
        sheetState = state,
        sheetShape = RoundedCornerShape(topStart = sheetCornerRadius, topEnd = sheetCornerRadius),
        scrimColor = Color.Transparent,
        modifier = Modifier.fillMaxSize(),
        sheetContent = {
            Box(
                Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp)
            ) {
                Column {
                    val lazyListState = rememberLazyListState()
                    LaunchedEffect(conversation.size) {
                        lazyListState.animateScrollToItem(conversation.size)
                    }

                    AssistSheetHeader(
                        pipelines = pipelines,
                        currentPipeline = currentPipeline,
                        fromFrontend = fromFrontend,
                        onSelectPipeline = onSelectPipeline
                    )
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .heightIn(
                                max = configuration.screenHeightDp.dp -
                                    WindowInsets.safeContent.asPaddingValues().calculateBottomPadding() -
                                    WindowInsets.safeContent.asPaddingValues().calculateTopPadding() -
                                    80.dp
                            )
                    ) {
                        items(conversation) {
                            SpeechBubble(text = it.message, isResponse = !it.isInput)
                        }
                    }
                    AssistSheetControls(
                        inputMode,
                        onChangeInput,
                        onTextInput,
                        onMicrophoneInput
                    )
                }
            }
        }
    ) {
        // The rest of the screen is empty
    }
}

@Composable
fun AssistSheetHeader(
    pipelines: List<AssistUiPipeline>,
    currentPipeline: AssistUiPipeline?,
    fromFrontend: Boolean,
    onSelectPipeline: (Int, String) -> Unit
) = Column(verticalArrangement = Arrangement.Center) {
    Text(
        text = stringResource(if (fromFrontend) commonR.string.assist else commonR.string.app_name),
        fontSize = 20.sp,
        letterSpacing = 0.25.sp
    )
    if (currentPipeline != null) {
        val color = colorResource(commonR.color.colorOnSurfaceVariant)
        val weight = if (currentPipeline.attributionName != null) 0.5f else 1f

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(Modifier.weight(weight, fill = false)) {
                var pipelineShowList by remember { mutableStateOf(false) }
                val pipelineShowServer by rememberSaveable(pipelines.size) {
                    mutableStateOf(pipelines.distinctBy { it.serverId }.size > 1)
                }
                Row(
                    modifier = Modifier.clickable { pipelineShowList = !pipelineShowList }
                ) {
                    Text(
                        text = if (pipelineShowServer) "${currentPipeline.serverName}: ${currentPipeline.name}" else currentPipeline.name,
                        color = color,
                        style = MaterialTheme.typography.caption
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(commonR.string.assist_change_pipeline),
                        modifier = Modifier
                            .height(16.dp)
                            .padding(start = 4.dp),
                        tint = color
                    )
                }
                DropdownMenu(
                    expanded = pipelineShowList,
                    onDismissRequest = { pipelineShowList = false }
                ) {
                    pipelines.forEach {
                        val isSelected =
                            it.serverId == currentPipeline.serverId && it.id == currentPipeline.id
                        DropdownMenuItem(onClick = {
                            onSelectPipeline(it.serverId, it.id)
                            pipelineShowList = false
                        }) {
                            Text(
                                text = if (pipelineShowServer) "${it.serverName}: ${it.name}" else it.name,
                                fontWeight = if (isSelected) FontWeight.SemiBold else null
                            )
                        }
                    }
                }
            }
            if (currentPipeline.attributionName != null) {
                val uriHandler = LocalUriHandler.current
                val baseModifier = Modifier.weight(weight, fill = false).padding(start = 8.dp)
                val modifier = currentPipeline.attributionUrl?.let {
                    Modifier
                        .clickable { uriHandler.openUri(it) }
                        .then(baseModifier)
                } ?: baseModifier
                Text(
                    text = currentPipeline.attributionName,
                    textDecoration = if (currentPipeline.attributionUrl != null) TextDecoration.Underline else null,
                    color = color,
                    style = MaterialTheme.typography.caption,
                    modifier = modifier
                )
            }
        }
    }
}

@Composable
fun AssistSheetControls(
    inputMode: AssistViewModel.AssistInputMode?,
    onChangeInput: () -> Unit,
    onTextInput: (String) -> Unit,
    onMicrophoneInput: () -> Unit
) = Row(verticalAlignment = Alignment.CenterVertically) {
    if (inputMode == null) { // Pipeline info has not yet loaded, empty space for now
        Spacer(modifier = Modifier.height(64.dp))
        return
    }

    if (inputMode == AssistViewModel.AssistInputMode.BLOCKED) { // No info and not recoverable, no space
        return
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(inputMode) {
        if (inputMode == AssistViewModel.AssistInputMode.TEXT || inputMode == AssistViewModel.AssistInputMode.TEXT_ONLY) {
            focusRequester.requestFocus()
        }
    }

    if (inputMode == AssistViewModel.AssistInputMode.TEXT || inputMode == AssistViewModel.AssistInputMode.TEXT_ONLY) {
        var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue())
        }
        TextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(commonR.string.assist_enter_a_request)) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (text.text.isNotBlank()) {
                    onTextInput(text.text)
                    text = TextFieldValue("")
                }
            })
        )
        IconButton(
            onClick = {
                if (text.text.isNotBlank()) {
                    onTextInput(text.text)
                    text = TextFieldValue("")
                } else if (inputMode != AssistViewModel.AssistInputMode.TEXT_ONLY) {
                    onChangeInput()
                }
            },
            enabled = (inputMode != AssistViewModel.AssistInputMode.TEXT_ONLY || text.text.isNotBlank())
        ) {
            val inputIsSend = text.text.isNotBlank() || inputMode == AssistViewModel.AssistInputMode.TEXT_ONLY
            Image(
                asset = if (inputIsSend) CommunityMaterial.Icon3.cmd_send else CommunityMaterial.Icon3.cmd_microphone,
                contentDescription = stringResource(
                    if (inputIsSend) commonR.string.assist_send_text else commonR.string.assist_start_listening
                ),
                colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
                modifier = Modifier.size(24.dp)
            )
        }
    } else {
        Spacer(Modifier.size(48.dp))
        Spacer(Modifier.weight(0.5f))
        OutlinedButton({ onMicrophoneInput() }) {
            val inputIsActive = inputMode == AssistViewModel.AssistInputMode.VOICE_ACTIVE
            Image(
                asset = CommunityMaterial.Icon3.cmd_microphone,
                contentDescription = stringResource(
                    if (inputIsActive) commonR.string.assist_stop_listening else commonR.string.assist_start_listening
                ),
                colorFilter = ColorFilter.tint(
                    if (inputIsActive) {
                        LocalContentColor.current
                    } else {
                        MaterialTheme.colors.onSurface
                    }
                ),
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.weight(0.5f))
        IconButton({ onChangeInput() }) {
            Icon(
                imageVector = Icons.Outlined.Keyboard,
                contentDescription = stringResource(commonR.string.assist_enter_text),
                tint = MaterialTheme.colors.onSurface
            )
        }
    }
}

@Composable
fun SpeechBubble(text: String, isResponse: Boolean) {
    Row(
        horizontalArrangement = if (isResponse) Arrangement.Start else Arrangement.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isResponse) 0.dp else 24.dp,
                end = if (isResponse) 24.dp else 0.dp,
                top = 8.dp,
                bottom = 8.dp
            )
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (isResponse) {
                        colorResource(commonR.color.colorAccent)
                    } else {
                        colorResource(commonR.color.colorSpeechText)
                    },
                    AbsoluteRoundedCornerShape(
                        topLeft = 12.dp,
                        topRight = 12.dp,
                        bottomLeft = if (isResponse) 0.dp else 12.dp,
                        bottomRight = if (isResponse) 12.dp else 0.dp
                    )
                )
                .padding(4.dp)
        ) {
            Text(
                text = text,
                color = if (isResponse) {
                    Color.White
                } else {
                    Color.Black
                },
                modifier = Modifier
                    .padding(2.dp)
            )
        }
    }
}
