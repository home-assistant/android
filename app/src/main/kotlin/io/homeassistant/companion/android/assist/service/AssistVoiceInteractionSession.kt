package io.homeassistant.companion.android.assist.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import io.homeassistant.companion.android.assist.AssistActivity
import timber.log.Timber

/**
 * Handles a single voice interaction session.
 *
 * When the user triggers the assistant (via button, gesture, or voice command),
 * a new session is created. This session launches our existing [AssistActivity]
 * to handle the actual voice interaction.
 *
 * The session provides system-level integration features like:
 * - Showing UI above other apps
 * - Receiving assist context from the current app
 * - Working from the lock screen
 */
class AssistVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Timber.d("VoiceInteractionSession onShow, flags: $showFlags")

        val wakeWord = args?.getString(AssistVoiceInteractionService.EXTRA_WAKE_WORD)

        // Launch AssistActivity to handle the interaction
        // We use the activity because it already has all the Assist logic implemented
        val intent = AssistActivity.newInstance(
            context = context,
            fromWakeWord = wakeWord,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        // Finish this session since the activity will handle everything
        finish()
    }

    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)
        Timber.d("onHandleAssist called")
        // This provides context about the current app (screenshots, text, etc.)
    }
}
