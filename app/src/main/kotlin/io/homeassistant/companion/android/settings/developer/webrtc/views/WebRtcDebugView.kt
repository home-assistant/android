package io.homeassistant.companion.android.settings.developer.webrtc.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.webrtc.compose.WebRtcVideo
import io.homeassistant.companion.android.webrtc.core.CameraPlayer
import io.homeassistant.companion.android.webrtc.core.PlayerFailure
import io.homeassistant.companion.android.webrtc.core.PlayerState
import livekit.org.webrtc.EglBase

private const val VIDEO_ASPECT_RATIO = 16f / 9f

/**
 * Developer screen to exercise the native WebRTC camera player against a camera entity, without
 * any dashboard integration.
 */
@Composable
fun WebRtcDebugView(
    player: CameraPlayer?,
    playerState: PlayerState,
    eglContext: EglBase.Context?,
    onStart: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var entityId by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextField(
            value = entityId,
            onValueChange = { entityId = it },
            label = { Text(stringResource(commonR.string.webrtc_debug_entity_id)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onStart(entityId) }, enabled = entityId.isNotBlank()) {
                Text(stringResource(commonR.string.webrtc_debug_start))
            }
            Button(onClick = onStop, enabled = player != null) {
                Text(stringResource(commonR.string.webrtc_debug_stop))
            }
        }
        // Raw technical state, on purpose not localized on this developer-only screen
        Text(
            text = playerState.toDebugLabel(),
            style = MaterialTheme.typography.bodyMedium,
        )
        player?.let {
            WebRtcVideo(
                player = it,
                eglContext = eglContext,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(VIDEO_ASPECT_RATIO),
                failedContent = { failure ->
                    Text(
                        text = failure.toDebugLabel(),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center),
                    )
                },
            )
        }
    }
}

private fun PlayerState.toDebugLabel(): String = when (this) {
    is PlayerState.Failed -> "${this::class.simpleName}: ${failure.toDebugLabel()}"
    else -> this::class.simpleName.orEmpty()
}

private fun PlayerFailure.toDebugLabel(): String = when (this) {
    is PlayerFailure.Signaling -> "Signaling(${code ?: "unknown"}) ${message.orEmpty()}"
    is PlayerFailure.Internal -> "Internal: ${message.orEmpty()}"
    PlayerFailure.ConnectionLost -> "ConnectionLost"
}
