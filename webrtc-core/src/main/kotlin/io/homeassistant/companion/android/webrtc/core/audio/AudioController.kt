package io.homeassistant.companion.android.webrtc.core.audio

import android.content.Context
import android.media.AudioManager
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import timber.log.Timber

/**
 * Puts the device audio stack in and out of communication mode while a microphone is live.
 *
 * [WebRtcSession][io.homeassistant.companion.android.webrtc.core.session.WebRtcSession] drives
 * this structurally: it acquires when the microphone track starts sending and guarantees the
 * release on every exit path (microphone off, stop, failure, disposal), so consumers cannot leak
 * the communication audio mode.
 */
interface AudioController {
    /**
     * Enter communication mode. Balanced by [release]; implementations must support concurrent
     * holders (reference counting).
     */
    fun acquire()

    /** Leave communication mode once all holders released it, restoring the previous state. */
    fun release()

    /**
     * Controller that leaves the audio stack untouched, for consumers that do not send
     * microphone audio and for tests.
     */
    object None : AudioController {
        override fun acquire() {}
        override fun release() {}
    }
}

/**
 * [AudioController] backed by [AudioManager]: while held, the audio mode is
 * [AudioManager.MODE_IN_COMMUNICATION] (enabling the platform echo cancellation path used for
 * calls) and transient audio focus for voice communication is taken. On the last [release] the
 * focus is abandoned and the previous audio mode restored.
 */
class AndroidAudioController(context: Context) : AudioController {

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val lock = Any()
    private var holders = 0
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var focusRequest: AudioFocusRequestCompat? = null

    override fun acquire() {
        synchronized(lock) {
            holders++
            if (holders > 1) return

            previousAudioMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributesCompat.Builder()
                        .setUsage(AudioAttributesCompat.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener {
                    // The stream must keep running to not drop the camera session, there is
                    // nothing sensible to pause on focus loss
                    Timber.d("WebRTC audio focus changed: $it")
                }
                .build()
            focusRequest = request
            AudioManagerCompat.requestAudioFocus(audioManager, request)
        }
    }

    override fun release() {
        synchronized(lock) {
            if (holders == 0) return
            holders--
            if (holders > 0) return

            focusRequest?.let { AudioManagerCompat.abandonAudioFocusRequest(audioManager, it) }
            focusRequest = null
            audioManager.mode = previousAudioMode
        }
    }
}
