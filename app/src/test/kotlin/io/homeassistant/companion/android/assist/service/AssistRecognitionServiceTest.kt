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

    @Test
    fun `Given onCheckRecognitionSupport when invoked then report client error`() {
        val service = Robolectric.buildService(AssistRecognitionService::class.java).get()
        val callback = mockk<RecognitionService.SupportCallback>(relaxed = true)

        invokeOnCheckRecognitionSupport(service, Intent(Intent.ACTION_VOICE_COMMAND), callback)

        verify { callback.onError(SpeechRecognizer.ERROR_CLIENT) }
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

    private fun invokeOnCheckRecognitionSupport(
        service: AssistRecognitionService,
        recognizerIntent: Intent,
        supportCallback: RecognitionService.SupportCallback,
    ) {
        val method = AssistRecognitionService::class.java.getDeclaredMethod(
            "onCheckRecognitionSupport",
            Intent::class.java,
            RecognitionService.SupportCallback::class.java,
        )
        method.isAccessible = true
        method.invoke(service, recognizerIntent, supportCallback)
    }
}
