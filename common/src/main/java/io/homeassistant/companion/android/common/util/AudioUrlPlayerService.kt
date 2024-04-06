package io.homeassistant.companion.android.common.util

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import androidx.core.content.getSystemService
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import java.lang.ref.WeakReference

/**
 * A simple service for playing short streaming audio from URLs.
 */
class AudioUrlPlayerService() : Service() {

    companion object {
        const val MSG_START_PLAYBACK = 1
        const val MSG_STOP_PLAYBACK = 2
        private const val TAG = "AudioUrlPlayerService"
    }

    data class PlaybackRequestMessage(
        val path: String,
        val isAssistant: Boolean
    )

    private var audioManager: AudioManager? = null
    private var player: MediaPlayer? = null

    private var focusRequest: AudioFocusRequestCompat? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { /* Not used */ }

    private lateinit var messenger: Messenger

    internal class IncomingHandler(
        private val service: WeakReference<AudioUrlPlayerService>
    ) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_START_PLAYBACK -> {
                    val request = msg.obj as PlaybackRequestMessage
                    service.get()?.playAudio(request.path, request.isAssistant)
                }
                MSG_STOP_PLAYBACK -> service.get()?.stop()
                else -> super.handleMessage(msg)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService<AudioManager>()
    }

    fun playAudio(url: String, isAssistant: Boolean = true) {
        if (player != null) {
            stop()
        }

        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(
                        if (isAssistant) AudioAttributes.CONTENT_TYPE_SPEECH else AudioAttributes.CONTENT_TYPE_MUSIC
                    )
                    .setUsage(
                        if (isAssistant && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            AudioAttributes.USAGE_ASSISTANT
                        } else {
                            AudioAttributes.USAGE_MEDIA
                        }
                    )
                    .build()
            )
            setOnPreparedListener {
                requestFocus(isAssistant)
                player?.start()
            }
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "Media player encountered error: $what ($extra)")
                releasePlayer()
                true
            }
            setOnCompletionListener {
                releasePlayer()
            }
        }
        try {
            player?.setDataSource(url)
            player?.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Media player couldn't be prepared", e)
            releasePlayer()
        }
    }

    fun stop() {
        try {
            player?.stop()
        } catch (e: IllegalStateException) {
            // Player wasn't initialized, ignore
        }
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        abandonFocus()
    }

    private fun requestFocus(isAssistant: Boolean) {
        if (audioManager == null) return
        if (focusRequest == null) {
            focusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).run {
                setAudioAttributes(
                    AudioAttributesCompat.Builder().run {
                        if (isAssistant) {
                            setUsage(AudioAttributesCompat.USAGE_ASSISTANT)
                            setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                        } else {
                            setUsage(AudioAttributesCompat.USAGE_MEDIA)
                            setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                        }
                        build()
                    }
                )
                setOnAudioFocusChangeListener(focusListener)
                build()
            }
        }

        try {
            AudioManagerCompat.requestAudioFocus(audioManager!!, focusRequest!!)
        } catch (e: Exception) {
            // We don't use the result / focus if available but if not still continue
        }
    }

    private fun abandonFocus() {
        if (audioManager == null || focusRequest == null) return
        AudioManagerCompat.abandonAudioFocusRequest(audioManager!!, focusRequest!!)
    }

    override fun onBind(intent: Intent?): IBinder? {
        messenger = Messenger(IncomingHandler(WeakReference(this)))
        return messenger.binder
    }
}
