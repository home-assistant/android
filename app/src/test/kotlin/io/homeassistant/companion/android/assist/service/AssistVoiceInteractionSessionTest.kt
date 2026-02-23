package io.homeassistant.companion.android.assist.service

import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.assist.AssistActivity
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowVoiceInteractionSession

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AssistVoiceInteractionSessionTest {

    @get:Rule
    val consoleLogRule = ConsoleLogRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `Given session when onShow then start AssistActivity and finish session`() {
        val session = AssistVoiceInteractionSession(context)
        val shadow = Shadows.shadowOf(session) as ShadowVoiceInteractionSession
        shadow.create()

        session.onShow(Bundle(), 0)

        val shadowApplication = Shadows.shadowOf(context)
        val startedIntent = shadowApplication.nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(AssistActivity::class.java.name, startedIntent.component?.className)
        assertTrue(startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

        // finish() is posted to the handler to avoid BadTokenException
        ShadowLooper.idleMainLooper()
        assertTrue(shadow.isFinishing)
    }
}
