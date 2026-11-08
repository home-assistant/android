package io.homeassistant.companion.android.notifications.callscreen

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.util.SdkVersion
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

private const val PROXIMITY_WAKE_LOCK_TAG = "HomeAssistant:CallScreenProximity"

/**
 * Puts the audio stack in communication mode for the duration of a call and routes it to the speaker
 * or the earpiece. Must be used from the main thread.
 */
@Singleton
internal class CallAudioRouter @Inject constructor(@ApplicationContext private val context: Context) {

    private val audioManager: AudioManager? = context.getSystemService()
    private val powerManager: PowerManager? = context.getSystemService()
    private var focusRequest: AudioFocusRequest? = null
    private var proximityLock: PowerManager.WakeLock? = null

    /** Enter communication mode, take audio focus and route to the speaker or earpiece. */
    fun acquire(speakerOn: Boolean) {
        val manager = audioManager ?: return
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        setSpeaker(speakerOn)
    }

    /** Route the audio to the speaker, or to the earpiece with the screen turned off near the face. */
    fun setSpeaker(on: Boolean) {
        val manager = audioManager ?: return
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.S)) {
            val wanted = if (on) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            val device = manager.availableCommunicationDevices.firstOrNull { it.type == wanted }
            if (device == null || !manager.setCommunicationDevice(device)) {
                Timber.w("Unable to route call audio to ${if (on) "speaker" else "earpiece"}")
            }
        } else {
            @Suppress("DEPRECATION")
            manager.isSpeakerphoneOn = on
        }
        updateProximityLock(!on)
    }

    /** Restore normal audio mode, routing and focus. */
    fun release() {
        updateProximityLock(false)
        val manager = audioManager ?: return
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.S)) {
            manager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            manager.isSpeakerphoneOn = false
        }
        manager.mode = AudioManager.MODE_NORMAL
        val request = focusRequest
        if (request != null) {
            manager.abandonAudioFocusRequest(request)
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(null)
        }
        focusRequest = null
    }

    private fun updateProximityLock(held: Boolean) {
        if (held) {
            if (proximityLock == null &&
                powerManager?.isWakeLockLevelSupported(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                ) == true
            ) {
                proximityLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    PROXIMITY_WAKE_LOCK_TAG,
                ).also { it.acquire() }
            }
        } else {
            proximityLock?.takeIf { it.isHeld }?.release()
            proximityLock = null
        }
    }
}
