package io.homeassistant.companion.android.common.util

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Sample rate in Hz used by all voice pipelines and wake word detection.
 * Anything above would get downsample to 16kHz in core, so we don't need
 * to capture at a higher frequency.
 * */
const val VOICE_SAMPLE_RATE = 16000

// Only format "[g]uaranteed to be supported by devices"
private const val VOICE_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

private const val VOICE_AUDIO_SOURCE = AudioSource.MIC
private const val VOICE_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

/** Number of samples per read chunk (10ms at 16kHz). */
private const val READ_CHUNK_SIZE = 160

/**
 * Converts a [ShortArray] of PCM 16-bit audio samples to a little-endian [ByteArray].
 *
 * Each 16-bit sample is split into two bytes (low byte first) matching the format
 * expected by the Home Assistant voice pipeline.
 */
fun ShortArray.toAudioBytes(): ByteArray {
    val byteArray = ByteArray(size * 2)
    for (i in indices) {
        val sample = this[i].toInt()
        byteArray[i * 2] = (sample and 0xFF).toByte()
        byteArray[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
    }
    return byteArray
}

/**
 * Provides audio recording as a shared [Flow] of [ShortArray] chunks.
 *
 * Multiple collectors share the same underlying [AudioRecord] instance. The first
 * collector triggers [AudioRecord] creation and starts recording; additional collectors
 * receive the same audio stream. When the last collector cancels, recording stops
 * and the [AudioRecord] is released immediately.
 *
 * This sharing is critical on pre-Android 10 devices where only one [AudioRecord]
 * can be active at a time. Both the Assist pipeline and wake word detection can
 * safely collect from the same [VoiceAudioRecorder] concurrently.
 *
 */
class VoiceAudioRecorder(
    private val audioRecordFactory: () -> AudioRecord = ::createVoiceAudioRecord,
    private val recorderContext: CoroutineContext = Dispatchers.IO,
    private val sharingScope: CoroutineScope = CoroutineScope(Job() + recorderContext),
) {
    private val mutex = Mutex()
    private var sharedFlow: Flow<ShortArray>? = null

    /**
     * Returns a shared flow of audio samples as [ShortArray] chunks.
     *
     * The underlying [AudioRecord] read loop runs on [recorderContext] (defaults to
     * [Dispatchers.IO]) because [AudioRecord.read] is a blocking call. Each emission
     * contains one chunk of PCM 16-bit mono samples.
     *
     * The flow is shared via [shareIn] with [SharingStarted.WhileSubscribed]:
     * - First collector starts the [AudioRecord] and begins emitting
     * - Additional collectors receive the same audio stream
     * - When the last collector cancels, [AudioRecord] stops immediately
     */
    suspend fun audioData(): Flow<ShortArray> = mutex.withLock {
        sharedFlow ?: createSharedFlow().also { sharedFlow = it }
    }

    private fun createSharedFlow(): Flow<ShortArray> {
        val upstream = channelFlow {
            val recorder = audioRecordFactory()

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                Timber.e("AudioRecord failed to initialize")
                return@channelFlow
            }

            recorder.startRecording()

            val buffer = ShortArray(READ_CHUNK_SIZE)

            try {
                while (isActive) {
                    val readResult = recorder.read(buffer, 0, READ_CHUNK_SIZE)
                    Timber.e("Hello")
                    when {
                        readResult > 0 -> send(buffer.copyOf(readResult))
                        readResult < 0 -> {
                            Timber.e("AudioRecord read error: $readResult")
                            break
                        }
                    }
                }
            } finally {
                try {
                    recorder.stop()
                } catch (e: IllegalStateException) {
                    Timber.e(e, "Error stopping AudioRecord")
                }
                recorder.release()
            }
        }.flowOn(recorderContext)

        return upstream.shareIn(
            scope = sharingScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 0,
        )
    }
}

/**
 * Creates a pre-configured [AudioRecord] for voice input at [VOICE_SAMPLE_RATE].
 */
@SuppressLint("MissingPermission")
private fun createVoiceAudioRecord(): AudioRecord {
    // Use a buffer size that's a multiple of our chunk size (160 samples = 10ms)
    val adjustedBufferSize = maxOf(minBufferSize(), READ_CHUNK_SIZE * 4)
    return AudioRecord(
        VOICE_AUDIO_SOURCE,
        VOICE_SAMPLE_RATE,
        VOICE_CHANNEL_CONFIG,
        VOICE_AUDIO_FORMAT,
        adjustedBufferSize,
    )
}

private fun minBufferSize(): Int =
    AudioRecord.getMinBufferSize(VOICE_SAMPLE_RATE, VOICE_CHANNEL_CONFIG, VOICE_AUDIO_FORMAT)
