package io.homeassistant.companion.android.widgets.mediaplayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaPlayerControlsWidgetTest {

    @Test
    fun `Given media player widget action then use media play pause`() {
        assertEquals("media_play_pause", mediaPlayerPlaybackAction())
    }
}
