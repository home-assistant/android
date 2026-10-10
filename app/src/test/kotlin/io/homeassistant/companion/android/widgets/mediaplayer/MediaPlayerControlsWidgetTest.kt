package io.homeassistant.companion.android.widgets.mediaplayer

import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class MediaPlayerControlsWidgetTest {

    @ParameterizedTest
    @CsvSource(
        "playing, 1, media_pause",
        "playing, 4096, media_stop",
        "paused, 16384, media_play",
    )
    fun `Given media player state when selecting playback action then use supported action`(
        state: String,
        supportedFeatures: Int,
        expectedAction: String,
    ) {
        assertEquals(
            expectedAction,
            mediaPlayerPlaybackAction(createEntity(state, supportedFeatures)),
        )
    }

    @ParameterizedTest
    @CsvSource(
        "playing, 1, ic_pause",
        "playing, 4096, ic_stop",
        "paused, 16384, ic_play",
    )
    fun `Given media player state when selecting playback icon then use matching icon`(
        state: String,
        supportedFeatures: Int,
        expectedIcon: String,
    ) {
        val expectedResource = when (expectedIcon) {
            "ic_pause" -> R.drawable.ic_pause
            "ic_stop" -> R.drawable.ic_stop
            else -> R.drawable.ic_play
        }

        assertEquals(
            expectedResource,
            mediaPlayerPlaybackIcon(createEntity(state, supportedFeatures)),
        )
    }

    private fun createEntity(state: String, supportedFeatures: Int) = Entity(
        entityId = "media_player.test",
        state = state,
        attributes = mapOf("supported_features" to supportedFeatures),
        lastChanged = LocalDateTime.MIN,
        lastUpdated = LocalDateTime.MIN,
    )
}
