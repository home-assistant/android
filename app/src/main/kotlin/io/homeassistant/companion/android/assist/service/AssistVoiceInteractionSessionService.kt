package io.homeassistant.companion.android.assist.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import timber.log.Timber

/**
 * Service that creates [VoiceInteractionSession] instances when the assistant is triggered.
 *
 * This service runs in a separate process from [AssistVoiceInteractionService] to keep
 * the always-running service lightweight. Sessions are created on-demand when the user
 * activates the assistant.
 */
class AssistVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        Timber.d("Creating new VoiceInteractionSession")
        return AssistVoiceInteractionSession(applicationContext)
    }
}
