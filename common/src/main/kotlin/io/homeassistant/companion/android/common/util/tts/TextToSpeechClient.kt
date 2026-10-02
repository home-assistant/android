package io.homeassistant.companion.android.common.util.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.notifications.NotificationData
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    private var playbackJob: Job? = null
    private val hasFocus = MutableStateFlow(false)
    private var isTransientLoss = false
    private var currentUtterance: Utterance? = null
    private var focusRequest: AudioFocusRequestCompat? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        mainScope.launch {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    Timber.d("Audio focus gained")
                    hasFocus.value = true
                    if (utteranceQueue.isNotEmpty() && !isPlaying) {
                        startPlayback()
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    Timber.d("Audio focus lost permanently")
                    hasFocus.value = false
                    stopTTS()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    Timber.d("Audio focus lost temporarily")
                    hasFocus.value = false
                    isTransientLoss = true
                    playbackJob?.cancel()
                    playbackJob = null
                    textToSpeechEngine.release()
                }
            }
        }
    }

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
                startPlayback()
            }
        }
    }

    /**
     * Interrupts any playback and clears the queue.
     */
    fun stopTTS() {
        Timber.d("stopped TTS")
        playbackJob?.cancel()
        playbackJob = null
        mainJob.cancelChildren()
        utteranceQueue.clear()
        textToSpeechEngine.release()
        abandonAudioFocus()
        hasFocus.value = false
        isPlaying = false
    }

    private fun startPlayback() {
        if (isPlaying) return
        isPlaying = true
        playbackJob = mainScope.launch {
            try {
                play()
            } finally {
                isPlaying = false
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        return try {
            val audioManager = applicationContext.getSystemService<AudioManager>() ?: return false

            val audioAttributes = utteranceQueue.firstOrNull()?.audioAttributes
            val compatAttributes = if (audioAttributes != null) AudioAttributesCompat.wrap(audioAttributes) else null
            val usage = if (audioAttributes !=
                null
            ) {
                compatAttributes?.usage ?: AudioAttributesCompat.USAGE_MEDIA
            } else {
                AudioAttributesCompat.USAGE_MEDIA
            }
            val contentType = if (audioAttributes !=
                null
            ) {
                compatAttributes?.contentType ?: AudioAttributesCompat.CONTENT_TYPE_SPEECH
            } else {
                AudioAttributesCompat.CONTENT_TYPE_SPEECH
            }

            val audioAttributesCompat = AudioAttributesCompat.Builder()
                .setUsage(usage)
                .setContentType(contentType)
                .build()

            val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributesCompat)
                .setOnAudioFocusChangeListener(focusListener)
                .build()

            focusRequest = request

            val result = AudioManagerCompat.requestAudioFocus(audioManager, request)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                hasFocus.value = true
                true
            } else {
                hasFocus.value = false
                false
            }
        } catch (e: Throwable) {
            Timber.w(e, "Failed to request audio focus")
            hasFocus.value = false
            false
        }
    }

    private fun abandonAudioFocus() {
        val audioManager = applicationContext.getSystemService<AudioManager>() ?: return
        try {
            val request = focusRequest
            if (request != null) {
                AudioManagerCompat.abandonAudioFocusRequest(audioManager, request)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to abandon audio focus")
        }
        focusRequest = null
    }

    /**
     * Plays each queued [Utterance] in sequence until [utteranceQueue] is empty.
     * There can be further additions to the queue while a message is playing which will be picked up in the running playback loop.
     */
    private suspend fun play() {
        isTransientLoss = false
        val focusGranted = requestAudioFocus()

        if (!focusGranted && !hasFocus.value) {
            Timber.e("Audio focus request denied")
            handleError(applicationContext.getString(R.string.tts_error_focus_denied))
            utteranceQueue.clear()
            abandonAudioFocus()
            return
        }

        if (!hasFocus.value) {
            Timber.d("Audio focus not granted yet, waiting...")
            val regained = withTimeoutOrNull(FOCUS_TIMEOUT_MS) {
                hasFocus.first { it }
                true
            } ?: false

            if (!regained) {
                Timber.e("Timed out waiting for initial audio focus")
                handleError(applicationContext.getString(R.string.tts_error_focus_denied))
                utteranceQueue.clear()
                abandonAudioFocus()
                return
            }
        }

        textToSpeechEngine.initialize().onFailure { throwable ->
            Timber.e(
                throwable,
                "Failed to initialize engine.",
            )
            handleError(applicationContext.getString(R.string.tts_error_init))
            utteranceQueue.clear()
            abandonAudioFocus()
        }.onSuccess {
            try {
                // Over bluetooth connections, the first syllable or even word can be cut off.
                // Adding an initial empty utterance seems to fix this.
                // Testing shows this is more effective than utilizing the
                // textToSpeech.playSilentUtterance method.
                if (utteranceQueue.isNotEmpty()) {
                    utteranceQueue.addFirst(
                        Utterance(
                            id = UUID.randomUUID().toString(),
                            text = " ",
                            streamVolumeAdjustment = utteranceQueue.first().streamVolumeAdjustment,
                            audioAttributes = utteranceQueue.first().audioAttributes,
                        ),
                    )
                }
                while (utteranceQueue.isNotEmpty()) {
                    if (!hasFocus.value) {
                        Timber.d("Audio focus lost before playing utterance, waiting...")
                        val regained = withTimeoutOrNull(FOCUS_TIMEOUT_MS) {
                            hasFocus.first { it }
                            true
                        } ?: false

                        if (!regained) {
                            Timber.e("Timed out waiting to regain audio focus")
                            handleError(applicationContext.getString(R.string.tts_error_focus_denied))
                            utteranceQueue.clear()
                            abandonAudioFocus()
                            return@onSuccess
                        }
                    }

                    val utterance = utteranceQueue.removeFirst()
                    currentUtterance = utterance
                    textToSpeechEngine.play(utterance).onFailure { throwable ->
                        Timber.e(throwable, "Failed to play utterance '${utterance.id}'")
                        handleError(
                            applicationContext.getString(R.string.tts_error_utterance, utterance.text),
                        )
                    }
                    currentUtterance = null
                }
            } catch (e: CancellationException) {
                if (isTransientLoss) {
                    currentUtterance?.let {
                        utteranceQueue.addFirst(it)
                    }
                }
                throw e
            } finally {
                textToSpeechEngine.release()
                if (utteranceQueue.isEmpty()) {
                    abandonAudioFocus()
                }
            }
        }
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
        private const val FOCUS_TIMEOUT_MS = 10000L

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
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            }
        }
    }
}
