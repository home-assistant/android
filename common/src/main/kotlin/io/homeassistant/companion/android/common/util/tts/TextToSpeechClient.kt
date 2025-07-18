package io.homeassistant.companion.android.common.util.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.widget.Toast
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.notifications.NotificationData
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Entry point for speech synthesis and playback.
 *
 * Maintains a FIFO queue of utterances. To initiate playback or further add messages to the queue use [speakText].
 * The queue can be cleared and playback immediately interrupted with [stopTTS].
 *
 * @param textToSpeechEngine [TextToSpeechEngine] implementation to synthesize and play back a single message
 */
class TextToSpeechClient(private val applicationContext: Context, private val textToSpeechEngine: TextToSpeechEngine) {
    private val utteranceQueue: ArrayDeque<Utterance> = ArrayDeque()

    private val mainJob = Job()
    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + mainJob)

    private var isPlaying = false

    /**
     * Queues a text message to be played back if [data] with a [TextToSpeechData.TTS_TEXT] key is provided.
     *
     * If [data] also contains [NotificationData.MEDIA_STREAM] key and it's one of [NotificationData.ALARM_STREAMS], an [AudioManager.STREAM_ALARM] will be used for playback.
     * Additionally, if it's specifically [NotificationData.ALARM_STREAM_MAX], the channel's volume will be maximized during playback.
     */
    fun speakText(data: Map<String, String>) {
        mainScope.launch {
            val utteranceId = UUID.randomUUID().toString()
            var tts = data[TextToSpeechData.TTS_TEXT]
            if (tts.isNullOrEmpty()) {
                tts = applicationContext.getString(R.string.tts_no_text)
            }
            Timber.d("processing utterance ID: $utteranceId; msg: $tts")

            val streamVolumeAdjustment = getStreamVolumeAdjustment(applicationContext, data)
            val audioAttributes = getAudioAttributes(data)
            utteranceQueue.add(
                Utterance(
                    id = utteranceId,
                    text = tts,
                    streamVolumeAdjustment = streamVolumeAdjustment,
                    audioAttributes = audioAttributes,
                ),
            )
            if (!isPlaying) {
                play()
            }
        }
    }

    /**
     * Interrupts any playback and clears the queue.
     */
    fun stopTTS() {
        Timber.d("stopped TTS")
        mainJob.cancelChildren()
        utteranceQueue.clear()
        textToSpeechEngine.release()
        isPlaying = false
    }

    /**
     * Plays each queued [Utterance] in sequence until [utteranceQueue] is empty.
     * There can be further additions to the queue while a message is playing which will be picked up in the running playback loop.
     */
    private suspend fun play() {
        isPlaying = true
        textToSpeechEngine.initialize().onFailure { throwable ->
            Timber.e(
                throwable,
                "Failed to initialize engine.",
            )
            handleError(applicationContext.getString(R.string.tts_error_init))
            utteranceQueue.clear()
        }.onSuccess {
            while (utteranceQueue.isNotEmpty()) {
                utteranceQueue.removeFirst().let { utterance ->
                    textToSpeechEngine.play(utterance).onFailure { throwable ->
                        Timber.e(throwable, "Failed to play utterance '${utterance.id}'")
                        handleError(
                            applicationContext.getString(R.string.tts_error_utterance, utterance.text),
                        )
                    }
                }
            }
            textToSpeechEngine.release()
        }
        isPlaying = false
    }

    private fun handleError(msg: String) {
        mainScope.launch {
            Toast.makeText(
                applicationContext,
                msg,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private companion object {
        private fun getStreamVolumeAdjustment(context: Context, data: Map<String, String>): StreamVolumeAdjustment {
            val audioManager = context.getSystemService<AudioManager>()
            return if (
                audioManager != null &&
                data[NotificationData.MEDIA_STREAM] in NotificationData.ALARM_STREAMS &&
                data[NotificationData.MEDIA_STREAM] == NotificationData.ALARM_STREAM_MAX
            ) {
                StreamVolumeAdjustment.Maximize(
                    audioManager = audioManager,
                    streamId = AudioManager.STREAM_ALARM,
                )
            } else {
                StreamVolumeAdjustment.None
            }
        }

        private fun getAudioAttributes(data: Map<String, String>): AudioAttributes {
            return if (data[NotificationData.MEDIA_STREAM] in NotificationData.ALARM_STREAMS) {
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            } else {
                AudioAttributes.Builder().build()
            }
        }
    }
}
