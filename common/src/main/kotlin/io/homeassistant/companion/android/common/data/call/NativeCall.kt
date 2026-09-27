package io.homeassistant.companion.android.common.data.call

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

private const val MAX_CALL_ID_LENGTH = 256
private const val MAX_CALL_PATH_LENGTH = 2048
private const val PCM_FORMAT_FIELDS = 4
private const val MIN_PCM_FRAME_MS = 5
private const val MAX_PCM_FRAME_MS = 40
private const val MILLISECONDS_PER_SECOND = 1000
private const val MAX_PCM_MESSAGE_BYTES = 4096
private val PCM_SAMPLE_RATES = setOf(8000, 16000, 24000, 32000, 48000)

/** One server-scoped invitation; its path identifies the call generation. */
data class NativeCallInvitation(val serverId: Int, val callId: String, val path: String) {
    init {
        require(serverId > 0) { "An explicit registered server is required" }
        require(callId.isNotBlank() && callId.length <= MAX_CALL_ID_LENGTH) { "Invalid call identity" }
        require(path.length <= MAX_CALL_PATH_LENGTH && path.startsWith("/api/") && !path.contains('\\')) {
            "Invalid call path"
        }
    }
}

/** Resolve only API paths on the authenticated Home Assistant origin. */
fun nativeCallUrl(base: HttpUrl, path: String): HttpUrl {
    require(path.startsWith("/api/") && !path.contains('\\')) { "Invalid call API path" }
    val url = requireNotNull(base.resolve(path)) { "Invalid call URL" }
    require(url.scheme == base.scheme && url.host == base.host && url.port == base.port) { "Call origin changed" }
    require(
        url.encodedPath.startsWith("/api/") && url.fragment == null && url.username.isEmpty() && url.password.isEmpty(),
    ) {
        "Invalid call API URL"
    }
    return url
}

/** Authoritative server state, checked before ringing and again before answering. */
@Serializable
data class NativeCallDescription(
    val id: String,
    val state: String,
    val caller: String,
    @SerialName("remaining_ms") val remainingMs: Long,
    @SerialName("media_path") val mediaPath: String? = null,
)

/** PCM framing shared by the native media adapter and its server. */
data class NativeCallPcmFormat(val sampleRate: Int, val channels: Int, val frameMs: Int) {
    val frameBytes: Int get() = sampleRate * channels * frameMs / MILLISECONDS_PER_SECOND * 2

    companion object {
        /** Reject unsupported encodings before allocating Android audio resources. */
        fun parse(token: String): NativeCallPcmFormat {
            val parts = token.split(':')
            require(parts.size == PCM_FORMAT_FIELDS && parts[1] == "s16le") { "Unsupported call audio encoding" }
            val rate = parts[0].toInt()
            val channels = parts[2].toInt()
            val duration = parts[3].toInt()
            require(rate in PCM_SAMPLE_RATES && channels in 1..2 && duration in MIN_PCM_FRAME_MS..MAX_PCM_FRAME_MS) {
                "Unsupported call audio format"
            }
            require(rate * duration % MILLISECONDS_PER_SECOND == 0) { "Fractional call audio frame" }
            return NativeCallPcmFormat(rate, channels, duration).also {
                require(it.frameBytes < MAX_PCM_MESSAGE_BYTES) { "Call audio frame exceeds transport limit" }
            }
        }
    }
}

/** Local capture/playback permissions derived from the negotiated call media. */
data class NativeCallAudioPaths(val capture: Boolean, val playback: Boolean) {
    companion object {
        /** Apply the peer capability and local SDP direction before opening audio devices. */
        fun from(mode: String, direction: String, held: Boolean): NativeCallAudioPaths {
            require(mode in setOf("full_duplex", "mic_only", "speaker_only")) { "Invalid call audio mode" }
            require(direction in setOf("sendrecv", "sendonly", "recvonly", "inactive")) {
                "Invalid call audio direction"
            }
            return NativeCallAudioPaths(
                capture = !held && mode != "mic_only" && direction in setOf("sendrecv", "sendonly"),
                playback = !held && mode != "speaker_only" && direction in setOf("sendrecv", "recvonly"),
            )
        }
    }
}
