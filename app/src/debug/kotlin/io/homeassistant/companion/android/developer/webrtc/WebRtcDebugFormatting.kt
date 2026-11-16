package io.homeassistant.companion.android.developer.webrtc

import io.homeassistant.companion.android.webrtc.core.ConnectionState
import io.homeassistant.companion.android.webrtc.core.MicState
import io.homeassistant.companion.android.webrtc.core.RtcStatsSnapshot
import io.homeassistant.companion.android.webrtc.core.SessionFailure

/**
 * Raw, intentionally unlocalized formatting of the WebRTC session state and statistics for the
 * developer debug overlay.
 */

internal fun ConnectionState.toDebugLabel(): String = when (this) {
    is ConnectionState.Failed -> "${this::class.simpleName}: ${failure.toDebugLabel()}"
    else -> this::class.simpleName.orEmpty()
}

internal fun SessionFailure.toDebugLabel(): String = when (this) {
    is SessionFailure.Signaling -> "Signaling(${code ?: "unknown"}) ${message.orEmpty()}"
    is SessionFailure.Internal -> "Internal: ${message.orEmpty()}"
    SessionFailure.ConnectionLost -> "ConnectionLost"
}

internal fun MicState.toDebugLabel(): String = this::class.simpleName.orEmpty()

internal fun StreamTypesState.toDebugLabel(): String = when (this) {
    StreamTypesState.None -> "-"
    StreamTypesState.Loading -> "checking..."
    StreamTypesState.Unavailable -> "unavailable"
    is StreamTypesState.Loaded -> if (types.isEmpty()) "none" else types.joinToString()
}

internal fun RtcStatsSnapshot.toDebugLines(): List<Pair<String, String>> = buildList {
    candidatePair?.let { pair ->
        val rtt = pair.currentRtt?.let { " rtt ${it.inWholeMilliseconds}ms" }.orEmpty()
        add("ICE" to "${pair.localCandidateType ?: "?"} → ${pair.remoteCandidateType ?: "?"}$rtt")
    }
    inboundVideo?.let { add("Video in" to it.describe(video = true)) }
    inboundAudio?.let { add("Audio in" to it.describe(video = false)) }
    outboundAudio?.let { add("Audio out" to it.describe(video = false)) }
}

private fun RtcStatsSnapshot.RtpStreamStats.describe(video: Boolean): String = buildString {
    append(codec ?: "?")
    if (video && frameWidth != null && frameHeight != null) append(" ${frameWidth}x$frameHeight")
    if (video && fps != null) append(" @$fps")
    append(" $kbps kbps")
    packetsLost?.let { append(" lost $it") }
}
