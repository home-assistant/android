package io.homeassistant.companion.android.common.assist

import android.content.Context
import android.media.MediaPlayer
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AssistChimeManagerTest {

    private val prefsRepository: PrefsRepository = mockk()
    private val context: Context = mockk(relaxed = true)
    private val player: MediaPlayer = mockk(relaxed = true)

    private var createdPlayers = 0
    private val onCompletion = slot<MediaPlayer.OnCompletionListener>()
    private val onError = slot<MediaPlayer.OnErrorListener>()

    @BeforeEach
    fun setUp() {
        createdPlayers = 0
        every { player.setOnCompletionListener(capture(onCompletion)) } just Runs
        every { player.setOnErrorListener(capture(onError)) } just Runs
    }

    @Test
    fun `Given chime disabled when playListeningChimeIfEnabled then do not create a player`() = runTest {
        coEvery { prefsRepository.isAssistListeningChimeEnabled() } returns false
        val manager = AssistChimeManager(prefsRepository, context, ::trackedPlayer, StandardTestDispatcher(testScheduler))

        manager.playListeningChimeIfEnabled()

        assertEquals(0, createdPlayers)
    }

    @Test
    fun `Given chime enabled when playback completes then suspend until completion and release the player`() = runTest {
        coEvery { prefsRepository.isAssistListeningChimeEnabled() } returns true
        val manager = AssistChimeManager(prefsRepository, context, ::trackedPlayer, StandardTestDispatcher(testScheduler))

        val job = launch { manager.playListeningChimeIfEnabled() }
        runCurrent()

        verify { player.start() }
        assertFalse(job.isCompleted)

        onCompletion.captured.onCompletion(player)
        runCurrent()

        assertTrue(job.isCompleted)
        verify { player.release() }
    }

    @Test
    fun `Given chime enabled when playback errors then resume and release the player`() = runTest {
        coEvery { prefsRepository.isAssistListeningChimeEnabled() } returns true
        val manager = AssistChimeManager(prefsRepository, context, ::trackedPlayer, StandardTestDispatcher(testScheduler))

        val job = launch { manager.playListeningChimeIfEnabled() }
        runCurrent()

        onError.captured.onError(player, 1, 2)
        runCurrent()

        assertTrue(job.isCompleted)
        verify { player.release() }
    }

    @Test
    fun `Given MediaPlayer delivers two terminal callbacks when playback finishes then resume only once`() = runTest {
        coEvery { prefsRepository.isAssistListeningChimeEnabled() } returns true
        val manager = AssistChimeManager(prefsRepository, context, ::trackedPlayer, StandardTestDispatcher(testScheduler))

        val job = launch { manager.playListeningChimeIfEnabled() }
        runCurrent()

        onError.captured.onError(player, 1, 2)
        onCompletion.captured.onCompletion(player)
        runCurrent()

        assertTrue(job.isCompleted)
        verify(exactly = 1) { player.release() }
    }

    @Test
    fun `Given start throws when playback begins then resume and release the player`() = runTest {
        coEvery { prefsRepository.isAssistListeningChimeEnabled() } returns true
        every { player.start() } throws IllegalStateException()
        val manager = AssistChimeManager(prefsRepository, context, ::trackedPlayer, StandardTestDispatcher(testScheduler))

        manager.playListeningChimeIfEnabled()

        verify { player.release() }
    }

    @Test
    fun `Given preparing fails when playListeningChimeIfEnabled then do not start and release the player`() = runTest {
        coEvery { prefsRepository.isAssistListeningChimeEnabled() } returns true
        every { player.prepare() } throws IOException()
        val manager = AssistChimeManager(prefsRepository, context, ::trackedPlayer, StandardTestDispatcher(testScheduler))

        manager.playListeningChimeIfEnabled()

        verify(exactly = 0) { player.start() }
        verify { player.release() }
    }

    @Test
    fun `Given chime never finishes when playListeningChimeIfEnabled then release the player after the timeout`() = runTest {
        coEvery { prefsRepository.isAssistListeningChimeEnabled() } returns true
        val manager = AssistChimeManager(prefsRepository, context, ::trackedPlayer, StandardTestDispatcher(testScheduler))

        val job = launch { manager.playListeningChimeIfEnabled() }
        runCurrent() // Starts playback and suspends; no terminal callback is ever delivered.

        advanceTimeBy(CHIME_TIMEOUT)
        runCurrent()

        assertTrue(job.isCompleted)
        verify { player.release() }
        assertEquals(CHIME_TIMEOUT.inWholeMilliseconds, currentTime)
    }

    private fun trackedPlayer(): MediaPlayer {
        createdPlayers++
        return player
    }
}
