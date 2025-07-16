package io.homeassistant.companion.android.util.compose.media.player

import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPresentationState
import io.homeassistant.companion.android.common.R
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.delay

// Useful links
// https://github.com/androidx/media/blob/52387bb97511bb88242321e4689aa5952d45784f/demos/compose/src/main/java/androidx/media3/demo/compose/MainActivity.kt
// https://developer.android.com/media/media3/ui/compose

// Delay before auto-hiding controls
private val AUTO_HIDE_DELAY = 2.seconds

// Interval between updates of the current progress of the player
private val TIME_TRACKING_UPDATE_DELAY = 1.seconds

private val CenterButtonSize = 48.dp
private val BottomControlsHeight = 60.dp
private val BottomControlButtonSize = 24.dp
private val StartPaddingTime = (BottomControlsHeight - BottomControlButtonSize) / 2

private val ControlBackgroundColorStart = Color(0x30000000)
private val ControlBackgroundColorEnd = Color(0xB0000000)

/**
 * A Composable function that displays a video player.
 *
 * This composable handles the rendering of the video surface and optionally displays playback controls.
 * It allows for customization of content scaling and provides a callback for interactions
 * with the player area itself.
 *
 * It automatically hides the controls after a delay of 2s and let the user control if they are displayed or not.
 *
 * @param player The Player instance to be used for playback.
 * @param contentScale The `ContentScale` to apply to the video content (e.g., Fit, Crop).
 * @param modifier The `Modifier` to be applied to the main player container.
 * @param fullscreenModifier The `Modifier` to be applied to the player container when it is in fullscreen mode.
 */
@Composable
fun HAMediaPlayer(
    player: Player,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    fullscreenModifier: Modifier = Modifier,
    onFullscreenClicked: (isFullscreen: Boolean) -> Unit = {},
) {
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(AUTO_HIDE_DELAY)
            showControls = false
        }
    }

    HAMediaPlayer(player, showControls, contentScale, modifier, fullscreenModifier, onPlayerClicked = {
        showControls = !showControls
    }, onFullscreenClicked = onFullscreenClicked)
}

/**
 * A Composable function that displays a video player.
 *
 * This composable handles the rendering of the video surface and optionally displays playback controls.
 * It allows for customization of content scaling and provides a callback for interactions
 * with the player area itself.
 *
 * @param player The Player instance to be used for playback.
 * @param showControls Whether to display the playback controls (e.g., play/pause, mute/unmute, fullscreen, time).
 * @param contentScale The `ContentScale` to apply to the video content (e.g., Fit, Crop).
 * @param modifier The `Modifier` to be applied to the main player container.
 * @param fullscreenModifier The `Modifier` to be applied to the player container when it is in fullscreen mode.
 * @param onPlayerClicked Callback invoked when the player's surface (outside of controls) is clicked.
 * @param onFullscreenClicked Callback invoked when the fullscreen button is clicked `isFullscreen` indicates the new state.
 */
@Composable
@OptIn(UnstableApi::class)
fun HAMediaPlayer(
    player: Player,
    showControls: Boolean,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    fullscreenModifier: Modifier = Modifier,
    onPlayerClicked: () -> Unit = {},
    onFullscreenClicked: (isFullscreen: Boolean) -> Unit = {},
) {
    var isFullscreen by remember { mutableStateOf(false) }
    val presentationState = rememberPresentationState(player)
    val scaledModifier =
        Modifier.resizeWithContentScale(contentScale, presentationState.videoSizeDp)

    BoxWithConstraints(modifier = (if (isFullscreen) fullscreenModifier else modifier).fillMaxSize()) {
        PlayerSurface(
            player = player,
            modifier = scaledModifier,
        )

        Controls(
            player = player,
            showControls = showControls,
            isFullScreen = isFullscreen,
            onClickFullscreen = {
                isFullscreen = !isFullscreen
                onFullscreenClicked(isFullscreen)
            },
            onPlayerClicked = onPlayerClicked,
        )
    }
}

@Composable
private fun BoxWithConstraintsScope.Controls(
    player: Player,
    showControls: Boolean,
    isFullScreen: Boolean,
    onClickFullscreen: () -> Unit,
    onPlayerClicked: () -> Unit,
) {
    val bufferingState = rememberBufferingState(player)

    val animatedAlpha by animateFloatAsState(
        targetValue = if (showControls) 1.0f else 0f,
        label = "ControlsAlpha",
    )
    val modifier = Modifier.graphicsLayer {
        alpha = animatedAlpha
    }

    ShowControlsButton(onPlayerClicked)

    val centerModifier = modifier
        .size(CenterButtonSize)
        .align(Alignment.Center)
    if (!bufferingState.isBuffering) {
        PlayPauseButton(
            player,
            modifier = centerModifier,
        )
    } else {
        CircularProgressIndicator(
            modifier = centerModifier,
        )
    }

    val isBottomControlOverlapping = maxHeight / 2 < CenterButtonSize / 2 + BottomControlsHeight

    if (!isBottomControlOverlapping) {
        BottomControls(
            player = player,
            isFullScreen = isFullScreen,
            onClickFullscreen = onClickFullscreen,
            modifier = modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }

    if (!showControls) {
        // Draw over all the control to capture the click instead of the buttons that are transparent
        ShowControlsButton(onPlayerClicked)
    }
}

@Composable
private fun BoxScope.ShowControlsButton(action: () -> Unit) {
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                // Prevent ripple on tap
                indication = null,
                onClick = action,
            ),
    )
}

@Composable
private fun BottomControls(player: Player, isFullScreen: Boolean, onClickFullscreen: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .height(BottomControlsHeight)
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ControlBackgroundColorStart, ControlBackgroundColorEnd),
                ),
            ),
    ) {
        TimeText(player)
        Spacer(modifier = Modifier.weight(1f))
        MuteButton(player)
        FullscreenButton(isFullScreen, onClickFullscreen)
    }
}

@Composable
private fun RowScope.FullscreenButton(isFullScreen: Boolean, onClickFullscreen: () -> Unit) {
    IconButton(
        onClick = onClickFullscreen,
        modifier = Modifier
            .align(Alignment.CenterVertically)
            .size(BottomControlsHeight),
    ) {
        Icon(
            if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
            contentDescription = stringResource(R.string.fullscreen),
            modifier = Modifier.size(BottomControlButtonSize),
            tint = Color.White,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun RowScope.MuteButton(player: Player) {
    val muteState = rememberMuteUnmuteButtonState(player)
    if (muteState.isEnabled) {
        IconButton(
            onClick = muteState::onClick,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .size(BottomControlsHeight),
        ) {
            Icon(
                if (muteState.showMute) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(R.string.mute_unmute),
                modifier = Modifier.size(BottomControlButtonSize),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun RowScope.TimeText(player: Player) {
    var currentPosition by remember { mutableStateOf(player.currentPositionDuration()) }

    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPositionDuration()
            delay(TIME_TRACKING_UPDATE_DELAY)
        }
    }

    Text(
        currentPosition.toComponents { hours, minutes, seconds, _ ->
            if (hours > 0L) {
                String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
        },
        modifier = Modifier
            .padding(start = StartPaddingTime)
            .align(Alignment.CenterVertically),
        color = Color.White,
    )
}

@Composable
@OptIn(UnstableApi::class)
private fun PlayPauseButton(player: Player, modifier: Modifier) {
    val state = rememberPlayPauseButtonState(player)
    val icon = if (state.showPlay) Icons.Default.PlayArrow else Icons.Default.Pause
    val contentDescription =
        if (state.showPlay) stringResource(R.string.play) else stringResource(R.string.pause)
    IconButton(
        onClick = state::onClick,
        modifier = modifier,
        enabled = state.isEnabled,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(CenterButtonSize),
            tint = Color.White,
        )
    }
}

private fun Player.currentPositionDuration(): Duration = currentPosition.toDuration(DurationUnit.MILLISECONDS)

@Preview(showSystemUi = true)
@Composable
private fun PreviewPlayer() {
    HAMediaPlayer(FakePlayer(), true, ContentScale.Fit)
}
