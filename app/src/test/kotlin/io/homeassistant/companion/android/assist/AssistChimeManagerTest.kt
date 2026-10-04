package io.homeassistant.companion.android.assist

import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AssistChimeManagerTest {

    private val prefsRepository: PrefsRepository = mockk()

    @Test
    fun `Given chime disabled when playListeningChimeIfEnabled then do not play`() = runTest {
        coEvery { prefsRepository.isAssistListeningChimeEnabled() } returns false
        var played = false
        val manager = AssistChimeManager(prefsRepository) { played = true }

        manager.playListeningChimeIfEnabled()

        assertFalse(played)
    }

    @Test
    fun `Given chime enabled when playListeningChimeIfEnabled then return once the chime has finished`() = runTest {
        coEvery { prefsRepository.isAssistListeningChimeEnabled() } returns true
        var played = false
        val manager = AssistChimeManager(prefsRepository) {
            delay(1.seconds)
            played = true
        }

        manager.playListeningChimeIfEnabled()

        assertTrue(played)
        assertEquals(1.seconds.inWholeMilliseconds, currentTime)
    }

    @Test
    fun `Given chime never finishes when playListeningChimeIfEnabled then return after the timeout`() = runTest {
        coEvery { prefsRepository.isAssistListeningChimeEnabled() } returns true
        val manager = AssistChimeManager(prefsRepository) { awaitCancellation() }

        manager.playListeningChimeIfEnabled()

        assertEquals(CHIME_TIMEOUT.inWholeMilliseconds, currentTime)
    }
}
