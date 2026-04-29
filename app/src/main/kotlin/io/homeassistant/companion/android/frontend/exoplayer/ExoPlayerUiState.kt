package io.homeassistant.companion.android.frontend.exoplayer

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

/**
 * UI state for the ExoPlayer overlay rendered on top of the WebView.
 *
 * @param player The active player instance
 * @param size The size of the player overlay, or null while waiting for resize
 * @param top Top offset of the overlay
 * @param left Left offset of the overlay
 * @param isFullScreen Whether the player is in fullscreen mode
 * @param videoAspectRatio Native video aspect ratio (height / width) once known, used to compute
 *   a sensible height when the frontend sends a zero-height DOMRect
 */
data class ExoPlayerUiState(
    val player: Player,
    val size: DpSize? = null,
    val top: Dp = 0.dp,
    val left: Dp = 0.dp,
    val isFullScreen: Boolean = false,
    val videoAspectRatio: Double? = null,
)
