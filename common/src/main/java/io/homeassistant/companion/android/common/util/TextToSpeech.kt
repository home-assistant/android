package io.homeassistant.companion.android.common.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.notifications.NotificationData

object TextToSpeechData {
    const val TTS = "TTS"
    const val TTS_TEXT = "tts_text"

    const val COMMAND_STOP_TTS = "command_stop_tts"
}

private const val TAG = "TextToSpeech"
private var textToSpeech: TextToSpeech? = null

fun speakText(
    context: Context,
    data: Map<String, String>
) {
    Log.d(TAG, "Sending text to TTS")
    var tts = data[TextToSpeechData.TTS_TEXT]
    val audioManager = context.getSystemService<AudioManager>()
    val currentAlarmVolume = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM)
    val maxAlarmVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    if (tts.isNullOrEmpty()) {
        tts = context.getString(R.string.tts_no_text)
    }
    textToSpeech = TextToSpeech(
        context
    ) {
        if (it == TextToSpeech.SUCCESS) {
            val listener = object : UtteranceProgressListener() {
                override fun onStart(p0: String?) {
                    if (data[NotificationData.MEDIA_STREAM] == NotificationData.ALARM_STREAM_MAX) {
                        audioManager?.setStreamVolume(
                            AudioManager.STREAM_ALARM,
                            maxAlarmVolume!!,
                            0
                        )
                    }
                }

                override fun onDone(p0: String?) {
                    textToSpeech?.stop()
                    textToSpeech?.shutdown()
                    if (data[NotificationData.MEDIA_STREAM] == NotificationData.ALARM_STREAM_MAX) {
                        audioManager?.setStreamVolume(
                            AudioManager.STREAM_ALARM,
                            currentAlarmVolume!!,
                            0
                        )
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(p0: String?) {
                    textToSpeech?.stop()
                    textToSpeech?.shutdown()
                    if (data[NotificationData.MEDIA_STREAM] == NotificationData.ALARM_STREAM_MAX) {
                        audioManager?.setStreamVolume(
                            AudioManager.STREAM_ALARM,
                            currentAlarmVolume!!,
                            0
                        )
                    }
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (data[NotificationData.MEDIA_STREAM] == NotificationData.ALARM_STREAM_MAX) {
                        audioManager?.setStreamVolume(
                            AudioManager.STREAM_ALARM,
                            currentAlarmVolume!!,
                            0
                        )
                    }
                }
            }
            textToSpeech?.setOnUtteranceProgressListener(listener)
            if (data[NotificationData.MEDIA_STREAM] in NotificationData.ALARM_STREAMS) {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                textToSpeech?.setAudioAttributes(audioAttributes)
            }
            textToSpeech?.speak(tts, TextToSpeech.QUEUE_ADD, null, "")
            Log.d(TAG, "speaking text")
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    context.getString(R.string.tts_error, tts),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

fun stopTTS() {
    Log.d(TAG, "Stopping TTS")
    textToSpeech?.stop()
    textToSpeech?.shutdown()
}
