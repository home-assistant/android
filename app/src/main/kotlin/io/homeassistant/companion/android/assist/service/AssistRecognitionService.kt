package io.homeassistant.companion.android.assist.service

import android.content.Intent
import android.os.Build
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import timber.log.Timber

/**
 * Minimal RecognitionService required by the voice interaction service metadata.
 *
 * The actual Assist flow is driven by [AssistVoiceInteractionService] and
 * [AssistVoiceInteractionSessionService]. This recognizer exists only because the framework
 * requires a recognition service in the voice interaction metadata.
 */
class AssistRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        Timber.d("Ignoring RecognitionService start listening request: ${recognizerIntent.action}")
        listener.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCheckRecognitionSupport(recognizerIntent: Intent, supportCallback: SupportCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            supportCallback.onError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    override fun onCancel(listener: Callback) {
        Timber.d("RecognitionService canceled")
    }

    override fun onStopListening(listener: Callback) {
        Timber.d("RecognitionService stop requested")
    }
}
