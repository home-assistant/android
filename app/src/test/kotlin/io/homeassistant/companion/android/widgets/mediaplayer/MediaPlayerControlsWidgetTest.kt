package io.homeassistant.companion.android.widgets.mediaplayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class MediaPlayerControlsWidgetTest {

    @ParameterizedTest
    @CsvSource(
        "playing, media_pause",
        "paused, media_play",
        "idle, media_play",
    )
    fun `Given media player state when selecting playback action then use explicit action`(
        state: String,
        expectedAction: String,
    ) {
        assertEquals(expectedAction, mediaPlayerPlaybackAction(state))
    }

}
