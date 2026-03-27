package io.homeassistant.companion.android.assist.service

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AssistRecognitionServiceTest {

    @get:Rule
    val consoleLogRule = ConsoleLogRule()

    @Test
    fun `Given onStartListening when invoked then report client error`() {
        val service = Robolectric.buildService(AssistRecognitionService::class.java).get()
        val callback = mockk<RecognitionService.Callback>(relaxed = true)

        invokeOnStartListening(service, Intent(Intent.ACTION_VOICE_COMMAND), callback)

        verify { callback.error(SpeechRecognizer.ERROR_CLIENT) }
    }

    private fun invokeOnStartListening(
        service: AssistRecognitionService,
        intent: Intent,
        callback: RecognitionService.Callback,
    ) {
        val method = AssistRecognitionService::class.java.getDeclaredMethod(
            "onStartListening",
            Intent::class.java,
            RecognitionService.Callback::class.java,
        )
        method.isAccessible = true
        method.invoke(service, intent, callback)
    }
}
