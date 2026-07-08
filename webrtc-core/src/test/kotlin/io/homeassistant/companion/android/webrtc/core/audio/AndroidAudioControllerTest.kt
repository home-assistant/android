package io.homeassistant.companion.android.webrtc.core.audio

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidAudioControllerTest {

    private lateinit var audioManager: AudioManager
    private lateinit var controller: AndroidAudioController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        controller = AndroidAudioController(context)
    }

    @Test
    fun `Given a normal audio mode When acquiring Then communication mode is set and focus requested`() {
        controller.acquire()

        assertEquals(AudioManager.MODE_IN_COMMUNICATION, audioManager.mode)
        assertNotNull(shadowOf(audioManager).lastAudioFocusRequest)
    }

    @Test
    fun `Given a held controller When releasing Then the previous mode is restored and focus abandoned`() {
        audioManager.mode = AudioManager.MODE_RINGTONE
        controller.acquire()

        controller.release()

        assertEquals(AudioManager.MODE_RINGTONE, audioManager.mode)
        assertNotNull(shadowOf(audioManager).lastAbandonedAudioFocusRequest)
    }

    @Test
    fun `Given two holders When one releases Then communication mode is kept until the last release`() {
        controller.acquire()
        controller.acquire()

        controller.release()
        assertEquals(AudioManager.MODE_IN_COMMUNICATION, audioManager.mode)
        assertNull(shadowOf(audioManager).lastAbandonedAudioFocusRequest)

        controller.release()
        assertEquals(AudioManager.MODE_NORMAL, audioManager.mode)
        assertNotNull(shadowOf(audioManager).lastAbandonedAudioFocusRequest)
    }

    @Test
    fun `Given no holder When releasing Then nothing happens`() {
        audioManager.mode = AudioManager.MODE_RINGTONE

        controller.release()

        assertEquals(AudioManager.MODE_RINGTONE, audioManager.mode)
        assertNull(shadowOf(audioManager).lastAbandonedAudioFocusRequest)
    }

    @Test
    fun `Given a fully released controller When acquiring again Then communication mode is entered again`() {
        controller.acquire()
        controller.release()

        controller.acquire()

        assertEquals(AudioManager.MODE_IN_COMMUNICATION, audioManager.mode)
    }
}
