package io.homeassistant.companion.android.notifications.callscreen.webrtc

import io.homeassistant.companion.android.notifications.callscreen.Go2RtcSource
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import timber.log.Timber

private const val TYPE_OFFER = "webrtc/offer"
private const val TYPE_ANSWER = "webrtc/answer"
private const val TYPE_CANDIDATE = "webrtc/candidate"
private const val TYPE_ERROR = "error"
private const val KEY_TYPE = "type"
private const val KEY_VALUE = "value"

/** Message received from the go2rtc WebSocket endpoint of the WebRTC integration. */
internal sealed interface Go2RtcMessage {
    /** SDP answer to the offer we sent. */
    data class Answer(val sdp: String) : Go2RtcMessage

    /** Remote ICE candidate, blank when the remote gathering is complete. */
    data class Candidate(val candidate: String) : Go2RtcMessage

    /** Error reported by go2rtc. */
    data class Error(val value: String) : Go2RtcMessage
}

/**
 * Build the WebSocket URL of the WebRTC integration, mirroring `webrtc-camera.js`.
 *
 * @param baseUrl Home Assistant base URL, its path prefix is kept
 * @param signedPath result of `auth/sign_path` for `/api/webrtc/ws`, already containing `?authSig=`
 */
internal fun buildGo2RtcWsUrl(baseUrl: URL, signedPath: String, source: Go2RtcSource, server: String?): String {
    val scheme = if (baseUrl.protocol.equals("https", ignoreCase = true)) "wss" else "ws"
    val sourceQuery = when (source) {
        is Go2RtcSource.Url -> "&url=${source.url.urlEncoded()}"
        is Go2RtcSource.Entity -> "&entity=${source.entityId.urlEncoded()}"
    }
    val serverQuery = server?.let { "&server=${it.urlEncoded()}" }.orEmpty()
    return "$scheme://${baseUrl.authority}${baseUrl.path.trimEnd('/')}$signedPath$sourceQuery$serverQuery"
}

private fun String.urlEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

/** Parse a go2rtc text frame, `null` for malformed frames and unknown message types. */
@Suppress("ReturnCount")
internal fun parseGo2RtcMessage(text: String): Go2RtcMessage? {
    val obj: JsonObject = try {
        Json.parseToJsonElement(text).jsonObject
    } catch (e: SerializationException) {
        Timber.w(e, "Ignoring malformed go2rtc message")
        return null
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "Ignoring malformed go2rtc message")
        return null
    }
    val value = obj[KEY_VALUE]?.jsonPrimitive?.contentOrNull.orEmpty()
    return when (obj[KEY_TYPE]?.jsonPrimitive?.contentOrNull) {
        TYPE_ANSWER -> Go2RtcMessage.Answer(value)
        TYPE_CANDIDATE -> Go2RtcMessage.Candidate(value)
        TYPE_ERROR -> Go2RtcMessage.Error(value)
        else -> null
    }
}

/** Encode the SDP offer frame. */
internal fun encodeOffer(sdp: String): String = encodeMessage(TYPE_OFFER, sdp)

/** Encode a local ICE candidate frame, an empty [candidate] signals the end of gathering. */
internal fun encodeCandidate(candidate: String): String = encodeMessage(TYPE_CANDIDATE, candidate)

private fun encodeMessage(type: String, value: String): String = buildJsonObject {
    put(KEY_TYPE, type)
    put(KEY_VALUE, value)
}.toString()
