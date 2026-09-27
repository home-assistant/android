package io.homeassistant.companion.android.calls

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import io.homeassistant.companion.android.common.data.call.NativeCallAudioPaths
import io.homeassistant.companion.android.common.data.call.NativeCallIoDispatcher
import io.homeassistant.companion.android.common.data.call.NativeCallPcmFormat
import io.homeassistant.companion.android.common.data.call.NativeCallRepository
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

private const val AUDIO_FRAME_TAG: Byte = 1
private const val AUDIO_QUEUE_FRAMES = 8
private const val AUDIO_DEVICE_BUFFER_FRAMES = 4
private const val MAX_AUDIO_MESSAGE_BYTES = 4096
private const val MAX_PENDING_SEND_BYTES = 32768L

internal data class CallAudioFormats(
    val tx: NativeCallPcmFormat,
    val rx: NativeCallPcmFormat,
    val paths: NativeCallAudioPaths,
)

/** Owns native capture/playback only; the server remains the call owner. */
internal class NativeCallAudio @Inject constructor(
    private val repository: NativeCallRepository,
    @NativeCallIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /** Run PCM audio until the server closes the session or the owner cancels it. */
    suspend fun run(serverId: Int, path: String) = coroutineScope {
        val ended = CompletableDeferred<Unit>()
        val formats = MutableStateFlow<CallAudioFormats?>(null)
        val playback = Channel<ByteArray>(AUDIO_QUEUE_FRAMES, BufferOverflow.DROP_OLDEST)
        var negotiation = JsonObject(emptyMap())
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = kotlinJsonMapper.parseToJsonElement(text).jsonObject
                    negotiation = JsonObject(negotiation + message)
                    val tx = negotiation["tx_format"]?.jsonPrimitive?.content ?: return
                    val rx = negotiation["rx_format"]?.jsonPrimitive?.content ?: return
                    val paths = NativeCallAudioPaths.from(
                        negotiation["audio_mode"]?.jsonPrimitive?.content ?: "full_duplex",
                        negotiation["audio_direction"]?.jsonPrimitive?.content ?: "sendrecv",
                        negotiation["remote_connection_held"]?.jsonPrimitive?.booleanOrNull ?: false,
                    )
                    formats.value =
                        CallAudioFormats(NativeCallPcmFormat.parse(tx), NativeCallPcmFormat.parse(rx), paths)
                } catch (error: IllegalArgumentException) {
                    ended.completeExceptionally(IOException("Invalid call audio negotiation", error))
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (bytes.size !in 2..MAX_AUDIO_MESSAGE_BYTES || bytes[0] != AUDIO_FRAME_TAG) {
                    ended.completeExceptionally(IOException("Invalid call audio frame"))
                    return
                }
                playback.trySend(bytes.substring(1).toByteArray())
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ended.complete(Unit)
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                ended.complete(Unit)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ended.completeExceptionally(IOException("Call audio transport failed", t))
            }
        }
        val socket = repository.openMedia(serverId, path, listener)
        val mediaJob = launch {
            formats.filterNotNull().collectLatest { format ->
                while (playback.tryReceive().isSuccess) { /* Drop frames from the previous format. */ }
                playAndRecord(format, playback, socket)
            }
        }
        try {
            ended.await()
        } finally {
            mediaJob.cancel()
            socket.cancel()
            playback.close()
        }
    }

    @SuppressLint("MissingPermission") // The answer UI obtains RECORD_AUDIO before entering this scope.
    private suspend fun playAndRecord(
        formats: CallAudioFormats,
        playback: Channel<ByteArray>,
        socket: WebSocket,
    ): Nothing = withContext(ioDispatcher) {
        var track: AudioTrack? = null
        var record: AudioRecord? = null
        try {
            if (formats.paths.playback) track = openPlayback(formats.rx)
            if (formats.paths.capture) record = openCapture(formats.tx)
            if (!socket.send("{\"type\":\"audio_ready\"}")) throw IOException("Call audio transport closed")
            val microphone = record
            val speaker = track
            coroutineScope {
                if (speaker != null) launch(ioDispatcher) { playFrames(speaker, formats.rx, playback) }
                if (microphone != null) launch(ioDispatcher) { sendFrames(microphone, formats.tx, socket) }
                awaitCancellation()
            }
        } finally {
            if (record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            record?.release()
            if (track?.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track?.release()
        }
    }

    private fun openPlayback(format: NativeCallPcmFormat): AudioTrack {
        val mask = if (format.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minimum = AudioTrack.getMinBufferSize(format.sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT)
        require(minimum > 0) { "Audio route does not support negotiated playback" }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(format.sampleRate).setChannelMask(mask)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minimum, format.frameBytes * AUDIO_DEVICE_BUFFER_FRAMES)).build()
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED)
            track.play()
            return track
        } catch (error: IllegalStateException) {
            track.release()
            throw error
        }
    }

    @SuppressLint("MissingPermission") // The answer UI obtains RECORD_AUDIO before entering this scope.
    private fun openCapture(format: NativeCallPcmFormat): AudioRecord {
        val mask = if (format.channels == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
        val minimum = AudioRecord.getMinBufferSize(format.sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT)
        require(minimum > 0) { "Audio route does not support negotiated capture" }
        val record = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(format.sampleRate).setChannelMask(mask)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
            )
            .setBufferSizeInBytes(maxOf(minimum, format.frameBytes * AUDIO_DEVICE_BUFFER_FRAMES)).build()
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED)
            record.startRecording()
            return record
        } catch (error: IllegalStateException) {
            record.release()
            throw error
        }
    }

    private suspend fun playFrames(track: AudioTrack, format: NativeCallPcmFormat, frames: Channel<ByteArray>) {
        for (frame in frames) {
            require(frame.size % (format.channels * 2) == 0) { "Unaligned PCM frame" }
            var offset = 0
            while (kotlinx.coroutines.currentCoroutineContext().isActive && offset < frame.size) {
                val written = track.write(frame, offset, frame.size - offset, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) throw IOException("Call playback failed: $written")
                offset += written
            }
        }
    }

    private suspend fun sendFrames(record: AudioRecord, format: NativeCallPcmFormat, socket: WebSocket) {
        val frame = ByteArray(format.frameBytes + 1)
        frame[0] = AUDIO_FRAME_TAG
        val context = kotlinx.coroutines.currentCoroutineContext()
        while (context.isActive) {
            var offset = 1
            while (context.isActive && offset < frame.size) {
                val read = record.read(frame, offset, frame.size - offset, AudioRecord.READ_BLOCKING)
                if (read <= 0) throw IOException("Call capture failed: $read")
                offset += read
            }
            if (context.isActive && socket.queueSize() < MAX_PENDING_SEND_BYTES && !socket.send(frame.toByteString())) {
                throw IOException("Call audio transport closed")
            }
        }
    }
}
