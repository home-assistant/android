package io.homeassistant.companion.android.widgets.mediaplayer

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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

    @Test
    fun `Given playing entity when calling playback action then request pause`() = runTest {
        val integrationRepository = mockk<IntegrationRepository>(relaxUnitFun = true)

        callMediaPlayerPlaybackAction(integrationRepository, createEntity("playing"))

        coVerify {
            integrationRepository.callAction(
                "media_player",
                "media_pause",
                mapOf("entity_id" to "media_player.test"),
            )
        }
    }

    @Test
    fun `Given paused entity when calling playback action then request play`() = runTest {
        val integrationRepository = mockk<IntegrationRepository>(relaxUnitFun = true)

        callMediaPlayerPlaybackAction(integrationRepository, createEntity("paused"))

        coVerify {
            integrationRepository.callAction(
                "media_player",
                "media_play",
                mapOf("entity_id" to "media_player.test"),
            )
        }
    }

    private fun createEntity(state: String) = Entity(
        entityId = "media_player.test",
        state = state,
        attributes = emptyMap(),
        lastChanged = LocalDateTime.MIN,
        lastUpdated = LocalDateTime.MIN,
    )
}
