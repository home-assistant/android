package io.homeassistant.companion.android.common.util

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource
import android.os.Build
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Wrapper around [AudioRecord] providing pre-configured audio recording functionality.
 *
 * This recorder handles Bluetooth SCO (Synchronous Connection Oriented) audio routing when a
 * Bluetooth headset with microphone is available. When Bluetooth SCO is active:
 * - Audio source is set to [AudioSource.VOICE_COMMUNICATION] for optimal voice quality
 * - Sample rate is adjusted to 8kHz as required by Android's Bluetooth SCO restrictions
 * - The VOICE_COMMUNICATION source provides echo cancellation and automatic gain control
 *
 * Note: Bluetooth SCO connection is asynchronous. The system may take several seconds to
 * establish the connection. Applications should register for ACTION_SCO_AUDIO_STATE_UPDATED
 * to be notified when the connection is ready (SCO_AUDIO_STATE_CONNECTED).
 */
class AudioRecorder(private val audioManager: AudioManager?) {

    companion object {
        // Standard sample rate for regular audio recording
        // Docs: 'currently the only rate that is guaranteed to work on all devices'
        const val SAMPLE_RATE = 44100
        
        // Sample rate for Bluetooth SCO - required by Android for SCO connections
        const val BLUETOOTH_SAMPLE_RATE = 8000

        // Docs: only format '[g]uaranteed to be supported by devices'
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())

    private var recorder: AudioRecord? = null
    private var recorderJob: Job? = null
    private var scoStarted = false
    private var currentSampleRate = SAMPLE_RATE

    private val _audioBytes = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Flow emitting audio recording bytes as they come in */
    val audioBytes = _audioBytes.asSharedFlow()

    private var focusRequest: AudioFocusRequestCompat? = null
    private val focusListener = OnAudioFocusChangeListener { /* Not used */ }

    /**
     * Determine the appropriate audio source based on connected devices.
     * Returns VOICE_COMMUNICATION when Bluetooth SCO is available off-call,
     * otherwise returns MIC.
     *
     * VOICE_COMMUNICATION source is tuned for voice communications such as VoIP and provides
     * benefits like echo cancellation and automatic gain control.
     */
    private fun getAudioSource(): Int {
        if (audioManager == null) {
            return AudioSource.MIC
        }

        // Check if Bluetooth SCO is available
        return if (audioManager.isBluetoothScoAvailableOffCall) {
            AudioSource.VOICE_COMMUNICATION
        } else {
            AudioSource.MIC
        }
    }

    /**
     * Get the appropriate sample rate based on audio configuration.
     * Returns 8kHz for Bluetooth SCO (required by Android restrictions),
     * otherwise returns standard 44.1kHz.
     *
     * Bluetooth SCO restrictions:
     * - Format must be mono
     * - Sampling must be 8kHz or 16kHz for input streams
     * Using 44100Hz with Bluetooth SCO can result in unexpected behavior.
     */
    private fun getSampleRate(): Int {
        return if (audioManager?.isBluetoothScoAvailableOffCall == true) {
            BLUETOOTH_SAMPLE_RATE
        } else {
            SAMPLE_RATE
        }
    }

    /**
     * Start the recorder. After calling this function, data will be available via [audioBytes].
     * @throws SecurityException when missing permission to record audio
     * @return `true` if the recorder started, or `false` if not
     */
    fun startRecording(): Boolean {
        if (recorder == null) {
            setupRecorder()
        }
        val ready = recorder?.state == AudioRecord.STATE_INITIALIZED
        if (!ready) return false

        if (recorderJob == null || recorderJob?.isActive == false) {
            requestFocus()
            recorder?.startRecording()
            recorderJob = ioScope.launch {
                val dataSize = minBufferSize()
                while (isActive) {
                    // We're recording in 16-bit as that is guaranteed to be supported but bytes are
                    // 8-bit. So first read as shorts, then manually split them into two bytes, and
                    // finally send all pairs of two as one array to the flow.
                    // Split/conversion based on https://stackoverflow.com/a/47905328/4214819.
                    val data = ShortArray(dataSize)
                    recorder?.read(data, 0, dataSize) // blocking!
                    _audioBytes.emit(
                        data
                            .flatMap {
                                val first = (it.toInt() and 0x00FF).toByte()
                                val last = ((it.toInt() and 0xFF00) shr 8).toByte()
                                listOf(first, last)
                            }
                            .toByteArray(),
                    )
                }
            }
        }
        return true
    }

    fun stopRecording() {
        recorder?.stop()
        recorderJob?.cancel()
        recorderJob = null
        abandonFocus()
        releaseRecorder()
    }

    @SuppressLint("MissingPermission")
    private fun setupRecorder() {
        if (recorder != null) stopRecording()

        val audioSource = getAudioSource()
        currentSampleRate = getSampleRate()
        val bufferSize = minBufferSize() * 10
        recorder = AudioRecord(audioSource, currentSampleRate, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
    }

    private fun releaseRecorder() {
        recorder?.release()
        recorder = null
    }

    private fun minBufferSize() = AudioRecord.getMinBufferSize(currentSampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)

    private fun requestFocus() {
        if (audioManager == null) return

        // Enable Bluetooth SCO if available
        // Note: SCO connection is asynchronous and may take several seconds
        if (audioManager.isBluetoothScoAvailableOffCall) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Modern API (Android 12+)
                    audioManager.setCommunicationDevice(
                        audioManager.availableCommunicationDevices
                            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                    )
                } else {
                    // Fallback for older versions
                    audioManager.startBluetoothSco()
                }
                scoStarted = true
            } catch (e: Exception) {
                // Log but continue if SCO fails
                scoStarted = false
            }
        }

        if (focusRequest == null) {
            focusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).run {
                setAudioAttributes(
                    AudioAttributesCompat.Builder().run {
                        setUsage(AudioAttributesCompat.USAGE_ASSISTANT)
                        setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                        build()
                    },
                )
                setOnAudioFocusChangeListener(focusListener)
                build()
            }
        }

        focusRequest?.let {
            try {
                AudioManagerCompat.requestAudioFocus(audioManager, it)
            } catch (e: Exception) {
                // We don't use the result / focus if available but if not still continue
            }
        }
    }

    private fun abandonFocus() {
        if (audioManager == null) return

        // Disable Bluetooth SCO only if this instance started it
        if (scoStarted) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Modern API (Android 12+)
                    audioManager.clearCommunicationDevice()
                } else {
                    // Fallback for older versions
                    audioManager.stopBluetoothSco()
                }
            } catch (e: Exception) {
                // Log but continue if SCO stop fails
            }
            scoStarted = false
        }

        if (focusRequest != null) {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest!!)
        }
    }
}