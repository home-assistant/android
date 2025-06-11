package io.homeassistant.companion.android.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import kotlin.time.Duration.Companion.hours

class HAMediaPlayerScreenshotTest {

    @Preview
    @Composable
    fun `Media Player with 1 hour position`() {
        HAMediaPlayerUI(FakePlayer(currentPosition = 1.hours), false, ContentScale.Inside)
    }

    @Preview
    @Composable
    fun `Media Player with 26 hours position`() {
        HAMediaPlayerUI(FakePlayer(currentPosition = 26.hours), false, ContentScale.Inside)
    }

    @Preview
    @Composable
    fun `Media Player is muted`() {
        HAMediaPlayerUI(FakePlayer(muted = true), false, ContentScale.Inside)
    }

    @Preview
    @Composable
    fun `Media Player is playing`() {
        HAMediaPlayerUI(FakePlayer(muted = true, playing = true), false, ContentScale.Inside)
    }
}
