package io.homeassistant.companion.android.assist

import android.content.Context
import android.media.AudioManager
import io.homeassistant.companion.android.assist.wakeword.WakeWordListenerFactory
import io.homeassistant.companion.android.common.assist.DefaultAssistAudioStrategy
import io.homeassistant.companion.android.common.util.VoiceAudioRecorder
import io.homeassistant.companion.android.settings.assist.AssistConfigManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AssistAudioStrategyFactoryTest {

    private lateinit var voiceAudioRecorder: VoiceAudioRecorder
    private lateinit var wakeWordListenerFactory: WakeWordListenerFactory
    private lateinit var assistConfigManager: AssistConfigManager
    private lateinit var context: Context
    private lateinit var factory: AssistAudioStrategyFactory

    @BeforeEach
    fun setUp() {
        voiceAudioRecorder = mockk(relaxed = true)
        wakeWordListenerFactory = mockk(relaxed = true)
        assistConfigManager = mockk(relaxed = true)
        context = mockk(relaxed = true) {
            every { getSystemService(Context.AUDIO_SERVICE) } returns mockk<AudioManager>()
        }
        factory = AssistAudioStrategyFactory(
            voiceAudioRecorder = voiceAudioRecorder,
            wakeWordListenerFactory = wakeWordListenerFactory,
            assistConfigManager = assistConfigManager,
        )
    }

    @Nested
    inner class `Given no wake word phrase` {

        @Test
        fun `When create called Then returns DefaultAssistAudioStrategy`() {
            val strategy = factory.create(context = context, wakeWordPhrase = null)

            assertInstanceOf(DefaultAssistAudioStrategy::class.java, strategy)
        }
    }

    @Nested
    inner class `Given wake word phrase provided` {

        @Test
        fun `When create called Then returns WakeWordAssistAudioStrategy`() {
            val strategy = factory.create(context = context, wakeWordPhrase = "hey_jarvis")

            assertInstanceOf(WakeWordAssistAudioStrategy::class.java, strategy)
        }
    }
}
