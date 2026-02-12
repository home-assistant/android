package io.homeassistant.companion.android.common.util

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource
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
 */
class AudioRecorder(private val audioManager: AudioManager?) {

    companion object {
        // Docs: 'currently the only rate that is guaranteed to work on all devices'
        const val SAMPLE_RATE = 44100

        // Docs: only format '[g]uaranteed to be supported by devices'
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())

    private var recorder: AudioRecord? = null
    private var recorderJob: Job? = null

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
     * Prefers Bluetooth SCO if available, falls back to VOICE_COMMUNICATION,
     * and finally defaults to MIC if neither is available.
     */
    private fun getAudioSource(): Int {
        if (audioManager == null) {
            return AudioSource.MIC
        }

        // Check if Bluetooth SCO is available
        if (audioManager.isBluetoothScoAvailableOffCall()) {
            // Additional check: see if a Bluetooth device is actually connected
            val bluetoothDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUT)
            for (device in bluetoothDevices) {
                if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    return AudioSource.BLUETOOTH_SCO
                }
            }
        }

        // Fall back to VOICE_COMMUNICATION if Bluetooth is not available
        // This is better than MIC as it applies proper audio processing for voice
        return AudioSource.VOICE_COMMUNICATION
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
        val bufferSize = minBufferSize() * 10
        recorder = AudioRecord(audioSource, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
    }

    private fun releaseRecorder() {
        recorder?.release()
        recorder = null
    }

    private fun minBufferSize() = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

    private fun requestFocus() {
        if (audioManager == null) return
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
        if (audioManager == null || focusRequest == null) return
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest!!)
    }
}
