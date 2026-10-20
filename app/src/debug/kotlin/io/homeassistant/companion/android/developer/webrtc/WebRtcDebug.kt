package io.homeassistant.companion.android.developer.webrtc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.webrtc.compose.WebRtcVideo
import io.homeassistant.companion.android.webrtc.core.ConnectionState
import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.MicState
import io.homeassistant.companion.android.webrtc.core.RtcStatsSnapshot
import io.homeassistant.companion.android.webrtc.core.VideoReceiver
import io.homeassistant.companion.android.webrtc.core.WebRtcSession

private const val VIDEO_ASPECT_RATIO = 16f / 9f

@Composable
fun WebRtcDebug(
    state: WebRtcDebugState,
    session: WebRtcSession?,
    connectionState: ConnectionState,
    micState: MicState,
    stats: RtcStatsSnapshot,
    onServerSelected: (Int) -> Unit,
    onEntitySelected: (String?) -> Unit,
    onMediaOptionsChange: (MediaOptions) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Media directions are fixed for a session, so they can only be changed while idle
    val isIdle = session == null

    val currentState by rememberUpdatedState(state)
    val onStartUpdated by rememberUpdatedState(onStart)
    val onMediaOptionsChangeUpdated by rememberUpdatedState(onMediaOptionsChange)
    val micPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                onStartUpdated()
            } else {
                // Without the permission we cannot send audio, so drop it and let the user start again
                onMediaOptionsChangeUpdated(currentState.mediaOptions.copy(audioOut = false))
            }
        }

    fun start() {
        if (state.mediaOptions.audioOut && !context.hasRecordAudioPermission()) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            onStart()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.showServerSelector) {
            HADropdownMenu(
                items = state.serversDropdownItems,
                selectedKey = state.selectedServerId,
                onItemSelected = onServerSelected,
                label = "Server",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        EntityPicker(
            displayState = state.entityDisplayState,
            selectedEntityId = state.selectedEntityId,
            onSelectionChanged = onEntitySelected,
            addButtonText = "Camera entity",
        )
        if (state.selectedEntityId != null) {
            DebugLine("Stream types", state.streamTypes.toDebugLabel())
        }
        MediaOptionsSwitches(options = state.mediaOptions, enabled = isIdle, onChange = onMediaOptionsChange)
        SessionControls(
            canStart = isIdle && state.canStart,
            canStop = session != null,
            onStart = ::start,
            onStop = onStop,
        )
        DebugInfoPanel(connectionState = connectionState, micState = micState, stats = stats)
        session?.videoIn?.let { video ->
            CameraVideo(video = video, state = connectionState)
        }
    }
}

@Composable
private fun MediaOptionsSwitches(options: MediaOptions, enabled: Boolean, onChange: (MediaOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StreamSwitch(
            label = "Receive video",
            checked = options.videoIn,
            enabled = enabled,
            onCheckedChange = { onChange(options.copy(videoIn = it)) },
        )
        StreamSwitch(
            label = "Receive audio",
            checked = options.audioIn,
            enabled = enabled,
            onCheckedChange = { onChange(options.copy(audioIn = it)) },
        )
        StreamSwitch(
            label = "Send audio",
            checked = options.audioOut,
            enabled = enabled,
            onCheckedChange = { onChange(options.copy(audioOut = it)) },
        )
    }
}

@Composable
private fun SessionControls(canStart: Boolean, canStop: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onStart, enabled = canStart) {
            Text("Start")
        }
        Button(onClick = onStop, enabled = canStop) {
            Text("Stop")
        }
    }
}

@Composable
private fun CameraVideo(video: VideoReceiver, state: ConnectionState) {
    WebRtcVideo(
        video = video,
        state = state,
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

@Composable
private fun StreamSwitch(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun DebugInfoPanel(connectionState: ConnectionState, micState: MicState, stats: RtcStatsSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DebugLine("State", connectionState.toDebugLabel())
        DebugLine("Mic", micState.toDebugLabel())
        stats.toDebugLines().forEach { (label, value) -> DebugLine(label, value) }
    }
}

@Composable
private fun DebugLine(label: String, value: String) {
    Text(text = "$label: $value", style = MaterialTheme.typography.bodyMedium)
}

private fun Context.hasRecordAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
