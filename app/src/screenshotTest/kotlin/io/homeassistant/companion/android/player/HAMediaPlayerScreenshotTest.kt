package io.homeassistant.companion.android.player

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.util.compose.media.player.FakePlayer
import io.homeassistant.companion.android.util.compose.media.player.HAMediaPlayerUI
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

    @Preview
    @Composable
    fun `Media Player is too small to display bottom controls`() {
        HAMediaPlayerUI(
            FakePlayer(muted = true, playing = true),
            false,
            ContentScale.Inside,
            modifier = Modifier.size(100.dp),
        )
    }
}
