package io.homeassistant.companion.android.notifications.callscreen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.GestureTapButton
import io.github.timoptr.mdiicons.generated.Microphone
import io.github.timoptr.mdiicons.generated.MicrophoneOff
import io.github.timoptr.mdiicons.generated.PhoneHangup
import io.github.timoptr.mdiicons.generated.PhoneInTalk
import io.github.timoptr.mdiicons.generated.VolumeHigh
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
import io.homeassistant.companion.android.common.compose.theme.HAColorScheme
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.util.fromHaName
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

// The call UI is drawn over camera video, so text and scrims need a fixed contrast that doesn't follow the theme
private val OVERLAY_TEXT = Color.White
private val OVERLAY_SCRIM = Color.Black.copy(alpha = 0.6f)
private val CALL_BUTTON_SIZE = HADimens.SPACE16
private const val SECONDS_PER_MINUTE = 60

/** Call UI of the call screen notification, backed by [viewModel]. */
@Composable
internal fun CallScreen(viewModel: CallScreenViewModel, onAnswer: () -> Unit, onAction: (CallAction) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CallScreenEvent.Error -> snackbarHostState.showSnackbar(context.getString(event.message))
            }
        }
    }
    state?.let {
        CallScreenContent(
            state = it,
            eglBaseContext = { viewModel.eglBaseContext },
            now = viewModel::now,
            snackbarHostState = snackbarHostState,
            onAnswer = onAnswer,
            onDecline = viewModel::onDecline,
            onHangUp = viewModel::onHangUp,
            onAction = onAction,
            onToggleMic = viewModel::onToggleMic,
            onToggleSpeaker = viewModel::onToggleSpeaker,
        )
    }
}

@Composable
@Suppress("LongParameterList")
internal fun CallScreenContent(
    state: CallScreenUiState,
    eglBaseContext: () -> EglBase.Context,
    now: () -> Instant,
    snackbarHostState: SnackbarHostState,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onAction: (CallAction) -> Unit,
    onToggleMic: (muted: Boolean) -> Unit,
    onToggleSpeaker: (on: Boolean) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize().background(LocalHAColorScheme.current.colorSurfaceDefault)) {
        CallBackground(state.videoTrack, state.poster, eglBaseContext)
        Scrims()
        CallHeader(state, now, Modifier.align(Alignment.TopCenter))
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(HADimens.SPACE6),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
        ) {
            SnackbarHost(snackbarHostState)
            if (state.phase == CallPhase.Ringing) {
                RingingControls(state.buttons, onAnswer, onDecline, onAction) { showMenu = true }
            } else {
                InCallControls(state, onHangUp, onAction, onToggleMic, onToggleSpeaker) { showMenu = true }
            }
        }
        if (showMenu) {
            MenuSheet(state.menuItems, onDismiss = { showMenu = false }, onAction = onAction)
        }
    }
}

@Composable
private fun CallBackground(videoTrack: VideoTrack?, poster: Bitmap?, eglBaseContext: () -> EglBase.Context) {
    when {
        videoTrack != null && !LocalInspectionMode.current -> VideoSurface(videoTrack, eglBaseContext)
        poster != null -> Image(
            bitmap = rememberImageBitmap(poster),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun rememberImageBitmap(bitmap: Bitmap): ImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

@Composable
private fun VideoSurface(track: VideoTrack, eglBaseContext: () -> EglBase.Context) {
    val context = LocalContext.current
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(eglBaseContext(), null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
        }
    }
    // Effects are disposed in reverse order: the sink is removed before the renderer is released
    DisposableEffect(renderer) { onDispose { renderer.release() } }
    DisposableEffect(track, renderer) {
        track.addSink(renderer)
        onDispose { track.removeSink(renderer) }
    }
    AndroidView(factory = { renderer }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun Scrims() {
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to OVERLAY_SCRIM,
                0.3f to Color.Transparent,
                0.7f to Color.Transparent,
                1f to OVERLAY_SCRIM,
            ),
        ),
    )
}

@Composable
private fun CallHeader(state: CallScreenUiState, now: () -> Instant, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().safeDrawingPadding().padding(HADimens.SPACE6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        state.title?.let {
            Text(
                text = it,
                style = HATextStyle.Headline.copy(color = OVERLAY_TEXT),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        state.message?.let {
            Text(
                text = it,
                style = HATextStyle.Body.copy(color = OVERLAY_TEXT),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(text = statusText(state, now), style = HATextStyle.BodyMedium.copy(color = OVERLAY_TEXT))
    }
}

@Composable
private fun statusText(state: CallScreenUiState, now: () -> Instant): String = when (state.phase) {
    CallPhase.Ringing -> stringResource(commonR.string.call_screen_incoming)
    CallPhase.Connecting -> stringResource(commonR.string.call_screen_connecting)
    is CallPhase.Failed -> stringResource(commonR.string.call_screen_stream_failed)
    CallPhase.Connected -> state.connectedAt?.let { elapsedText(it, now) }.orEmpty()
}

@Composable
private fun elapsedText(connectedAt: Instant, now: () -> Instant): String {
    var current by remember(connectedAt) { mutableStateOf(now()) }
    LaunchedEffect(connectedAt) {
        while (true) {
            delay(1.seconds)
            current = now()
        }
    }
    val seconds = (current - connectedAt).inWholeSeconds.coerceAtLeast(0)
    return "%02d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

@Composable
private fun RingingControls(
    buttons: List<CallAction>,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onAction: (CallAction) -> Unit,
    onMenu: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        buttons.forEach { action ->
            val colors = action.role.colors()
            CallButton(
                icon = actionIcon(action),
                label = action.title,
                containerColor = colors.first,
                contentColor = colors.second,
                onClick = {
                    when (action.role) {
                        CallActionRole.Answer -> onAnswer()
                        CallActionRole.Decline -> onDecline()
                        CallActionRole.Menu -> onMenu()
                        else -> onAction(action)
                    }
                },
            )
        }
    }
}

@Composable
private fun InCallControls(
    state: CallScreenUiState,
    onHangUp: () -> Unit,
    onAction: (CallAction) -> Unit,
    onToggleMic: (Boolean) -> Unit,
    onToggleSpeaker: (Boolean) -> Unit,
    onMenu: () -> Unit,
) {
    val colors = LocalHAColorScheme.current
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6)) {
        val actionButtons = state.buttons.filter {
            it.role == CallActionRole.Custom || it.role == CallActionRole.Menu
        }
        if (actionButtons.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                actionButtons.forEach { action ->
                    CallButton(
                        icon = actionIcon(action),
                        label = action.title,
                        containerColor = colors.colorFillNeutralNormalResting,
                        contentColor = colors.colorOnNeutralNormal,
                        onClick = { if (action.role == CallActionRole.Menu) onMenu() else onAction(action) },
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (state.micAvailable) {
                val micIcon = if (state.micMuted) Mdi.MicrophoneOff else Mdi.Microphone
                val micLabel =
                    if (state.micMuted) commonR.string.call_screen_unmute else commonR.string.call_screen_mute
                CallButton(
                    icon = micIcon.rememberImageVector(),
                    label = stringResource(micLabel),
                    containerColor = colors.toggleContainer(state.micMuted),
                    contentColor = colors.toggleContent(state.micMuted),
                    onClick = { onToggleMic(!state.micMuted) },
                )
            }
            val speakerIcon = if (state.speakerOn) Mdi.VolumeHigh else Mdi.PhoneInTalk
            CallButton(
                icon = speakerIcon.rememberImageVector(),
                label = stringResource(commonR.string.call_screen_speaker),
                containerColor = colors.toggleContainer(state.speakerOn),
                contentColor = colors.toggleContent(state.speakerOn),
                onClick = { onToggleSpeaker(!state.speakerOn) },
            )
            CallButton(
                icon = Mdi.PhoneHangup.rememberImageVector(),
                label = stringResource(commonR.string.call_screen_hang_up),
                containerColor = colors.colorFillDangerLoudResting,
                contentColor = colors.colorOnDangerLoud,
                onClick = onHangUp,
            )
        }
    }
}

private fun HAColorScheme.toggleContainer(active: Boolean) =
    if (active) colorFillNeutralLoudResting else colorFillNeutralNormalResting

private fun HAColorScheme.toggleContent(active: Boolean) = if (active) colorOnNeutralLoud else colorOnNeutralNormal

@Composable
private fun CallActionRole.colors(): Pair<Color, Color> {
    val colors = LocalHAColorScheme.current
    return when (this) {
        CallActionRole.Answer -> colors.colorFillSuccessLoudResting to colors.colorOnSuccessLoud
        CallActionRole.Decline -> colors.colorFillDangerLoudResting to colors.colorOnDangerLoud
        else -> colors.colorFillNeutralNormalResting to colors.colorOnNeutralNormal
    }
}

@Composable
private fun CallButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        Box(
            modifier = Modifier
                .size(CALL_BUTTON_SIZE)
                .clip(CircleShape)
                .background(containerColor)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor)
        }
        Text(
            text = label,
            style = HATextStyle.BodyMedium.copy(color = OVERLAY_TEXT),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(HADimens.SPACE10),
        )
    }
}

@Composable
private fun actionIcon(action: CallAction): ImageVector {
    val icon = remember(action.icon, action.role) {
        Mdi.fromHaName(action.icon) ?: Mdi.fromHaName(action.role.defaultIcon) ?: Mdi.GestureTapButton
    }
    return icon.rememberImageVector()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuSheet(items: List<CallAction>, onDismiss: () -> Unit, onAction: (CallAction) -> Unit) {
    HAModalBottomSheet(bottomSheetState = rememberHAModalBottomSheetState(), onDismissRequest = onDismiss) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) {
                        onAction(item)
                        onDismiss()
                    }
                    .padding(HADimens.SPACE4),
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = actionIcon(item),
                    contentDescription = null,
                    tint = LocalHAColorScheme.current.colorOnNeutralNormal,
                )
                Text(text = item.title, style = HATextStyle.UserInput)
            }
        }
    }
}

private fun previewAction(index: Int, key: String, title: String, icon: String, role: CallActionRole) = CallAction(
    index = index,
    key = key,
    title = title,
    icon = icon,
    role = role,
    uri = null,
    endCall = false,
    authenticationRequired = false,
)

private fun previewState(phase: CallPhase) = CallScreenUiState(
    title = "Front door",
    message = "Someone is at the door",
    poster = null,
    videoTrack = null,
    buttons = listOf(
        previewAction(0, "DECLINE", "Decline", "mdi:phone-hangup", CallActionRole.Decline),
        previewAction(1, "BUZZER", "Open door", "mdi:door-open", CallActionRole.Custom),
        previewAction(2, "MORE", "More", "mdi:dots-horizontal", CallActionRole.Menu),
        previewAction(0, "ANSWER", "Answer", "mdi:phone", CallActionRole.Answer),
    ),
    menuItems = emptyList(),
    phase = phase,
    micAvailable = true,
    micMuted = false,
    speakerOn = true,
    connectedAt = Clock.System.now(),
)

@Composable
private fun PreviewContent(phase: CallPhase) {
    HAThemeForPreview {
        CallScreenContent(
            state = previewState(phase),
            eglBaseContext = { error("No video in previews") },
            now = { Clock.System.now() },
            snackbarHostState = remember { SnackbarHostState() },
            onAnswer = {},
            onDecline = {},
            onHangUp = {},
            onAction = {},
            onToggleMic = {},
            onToggleSpeaker = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun CallScreenRingingPreview() {
    PreviewContent(CallPhase.Ringing)
}

@PreviewLightDark
@Composable
private fun CallScreenConnectedPreview() {
    PreviewContent(CallPhase.Connected)
}
