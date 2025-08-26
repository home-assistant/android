package io.homeassistant.companion.android.util.compose.media.player

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Duration.Companion.hours

class HAMediaPlayerScreenshotTest {

    @PreviewTest
    @Preview
    @Composable
    fun `Media Player with 1 hour position`() {
        HAMediaPlayer(FakePlayer(currentPosition = 1.hours), true, ContentScale.Companion.Inside)
    }

    @PreviewTest
    @Preview
    @Composable
    fun `Media Player with 26 hours position`() {
        HAMediaPlayer(FakePlayer(currentPosition = 26.hours), true, ContentScale.Companion.Inside)
    }

    @PreviewTest
    @Preview
    @Composable
    fun `Media Player is muted`() {
        HAMediaPlayer(FakePlayer(muted = true), true, ContentScale.Companion.Inside)
    }

    @PreviewTest
    @Preview
    @Composable
    fun `Media Player is playing`() {
        HAMediaPlayer(FakePlayer(playing = true), true, ContentScale.Companion.Inside)
    }

    @PreviewTest
    @Preview
    @Composable
    fun `Media Player is playing controls hidden`() {
        HAMediaPlayer(FakePlayer(playing = true), false, ContentScale.Companion.Inside)
    }

    @PreviewTest
    @Preview
    @Composable
    fun `Media Player is too small to display bottom controls`() {
        HAMediaPlayer(
            FakePlayer(playing = true),
            true,
            ContentScale.Companion.Inside,
            modifier = Modifier.Companion.size(100.dp),
        )
    }
}
