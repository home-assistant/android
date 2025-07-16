package io.homeassistant.companion.android.common.util.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Implementation of [TextToSpeechEngine] that uses the default [TextToSpeech] engine found on the device.
 */
class AndroidTextToSpeechEngine(private val applicationContext: Context) : TextToSpeechEngine {

    private val initMutex = Mutex()
    private var textToSpeech: TextToSpeech? = null
    private var lastVolumeOverridingUtterance: Utterance? = null

    override suspend fun initialize(): Result<Unit> = initMutex.withLock {
        if (textToSpeech != null) {
            Result.success(Unit)
        } else {
            suspendCancellableCoroutine { continuation ->
                textToSpeech = TextToSpeech(applicationContext) { code ->
                    if (code == TextToSpeech.SUCCESS) {
                        continuation.resume(Result.success(Unit))
                    } else {
                        textToSpeech?.shutdown()
                        textToSpeech = null
                        continuation.resume(
                            Result.failure(RuntimeException("Failed to initialize TTS client. Code: $code.")),
                        )
                    }
                }
            }
        }
    }

    override suspend fun play(utterance: Utterance): Result<Unit> {
        val textToSpeech = initMutex.withLock { textToSpeech }
        return suspendCancellableCoroutine { continuation ->
            if (textToSpeech == null) {
                continuation.resume(Result.failure(IllegalStateException("TextToSpeechEngine not initialized.")))
            } else {
                textToSpeech.setAudioAttributes(utterance.audioAttributes)
                val listener = object : UtteranceProgressListener() {
                    override fun onStart(p0: String?) {
                        utterance.streamVolumeAdjustment.overrideVolume()
                        lastVolumeOverridingUtterance = utterance
                    }

                    override fun onDone(p0: String?) {
                        Timber.d("Done speaking; utterance ID: $p0")
                        utterance.streamVolumeAdjustment.resetVolume()
                        continuation.resume(Result.success(Unit))
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utterance.streamVolumeAdjustment.resetVolume()
                        continuation.resume(
                            Result.failure(RuntimeException("Playback error; utterance ID: $utteranceId")),
                        )
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        utterance.streamVolumeAdjustment.resetVolume()
                        continuation.resume(
                            Result.failure(
                                RuntimeException("Playback error; utterance ID: $utteranceId; error code: $errorCode"),
                            ),
                        )
                    }
                }
                textToSpeech.setOnUtteranceProgressListener(listener)
                textToSpeech.speak(utterance.text, TextToSpeech.QUEUE_FLUSH, null, utterance.id)
                Timber.d("Speaking; utterance ID: ${utterance.id}")
            }
        }
    }

    override fun release() {
        if (textToSpeech?.isSpeaking == true) {
            // resets the volume back if the playback was interrupted
            lastVolumeOverridingUtterance?.streamVolumeAdjustment?.resetVolume()
        }
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
