package io.homeassistant.companion.android.frontend

import android.util.Rational
import androidx.media3.common.Player
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.frontend.exoplayer.ExoPlayerUiState
import io.homeassistant.companion.android.launch.PipReadiness
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric is required because `android.util.Rational#equals` is stubbed in pure JVM unit
 * tests — comparing two distinct `Rational` instances always returns false without it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class PipReadinessTest {

    private val player: Player = mockk(relaxed = true)

    @Test
    fun `Given neither customView nor fullscreen player when computed then result is null`() {
        val result = PipReadiness.from(customViewShown = false, exoState = null)

        assertNull(result)
    }

    @Test
    fun `Given non-fullscreen player without customView when computed then result is null`() {
        val state = ExoPlayerUiState(player = player, isFullScreen = false, videoAspectRatio = 9.0 / 16.0)

        val result = PipReadiness.from(customViewShown = false, exoState = state)

        assertNull(result)
    }

    @Test
    fun `Given customView shown when computed then result uses 16 to 9 default`() {
        val result = PipReadiness.from(customViewShown = true, exoState = null)

        assertEquals(Rational(16, 9), result?.aspectRatio)
        assertNull(result?.sourceRect)
    }

    @Test
    fun `Given fullscreen player without aspect ratio when computed then result uses 16 to 9 default`() {
        val state = ExoPlayerUiState(player = player, isFullScreen = true, videoAspectRatio = null)

        val result = PipReadiness.from(customViewShown = false, exoState = state)

        assertEquals(Rational(16, 9), result?.aspectRatio)
    }

    @Test
    fun `Given fullscreen player and customView when computed then player aspect wins`() {
        val state = ExoPlayerUiState(player = player, isFullScreen = true, videoAspectRatio = 3.0 / 4.0)

        val result = PipReadiness.from(customViewShown = true, exoState = state)

        // 4:3 -> width 1000, height 750 -> Rational(4, 3) after reduction
        assertEquals(Rational(4, 3), result?.aspectRatio)
    }

    @Test
    fun `Given fullscreen player with 16 to 9 aspect when computed then aspect is preserved`() {
        // 9.0 / 16.0 = 0.5625; widthScaled=1000, heightScaled=562 -> Rational(500, 281)
        val result = computeFor(heightOverWidth = 9.0 / 16.0)

        assertEquals(Rational(500, 281), result?.aspectRatio)
    }

    @Test
    fun `Given fullscreen player with 4 to 3 aspect when computed then aspect is preserved`() {
        val result = computeFor(heightOverWidth = 3.0 / 4.0)

        assertEquals(Rational(4, 3), result?.aspectRatio)
    }

    @Test
    fun `Given fullscreen player with 3 to 1 ultra-wide aspect when computed then aspect is clamped to max`() {
        val result = computeFor(heightOverWidth = 1.0 / 3.0)

        assertEquals(Rational(239, 100), result?.aspectRatio)
    }

    @Test
    fun `Given fullscreen player with 1 to 3 ultra-tall aspect when computed then aspect is clamped to min`() {
        val result = computeFor(heightOverWidth = 3.0)

        assertEquals(Rational(100, 239), result?.aspectRatio)
    }

    private fun computeFor(heightOverWidth: Double) = PipReadiness.from(
        customViewShown = false,
        exoState = ExoPlayerUiState(player = player, isFullScreen = true, videoAspectRatio = heightOverWidth),
    )
}
