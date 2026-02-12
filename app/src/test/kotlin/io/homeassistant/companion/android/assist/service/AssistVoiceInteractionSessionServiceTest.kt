package io.homeassistant.companion.android.assist.service

import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AssistVoiceInteractionSessionServiceTest {

    @get:Rule
    val consoleLogRule = ConsoleLogRule()

    @Test
    fun `Given service when onNewSession then return AssistVoiceInteractionSession`() {
        val serviceController = Robolectric.buildService(AssistVoiceInteractionSessionService::class.java)
        val service = serviceController.get()

        val session = service.onNewSession(null)

        assertTrue(session is AssistVoiceInteractionSession)
    }
}
