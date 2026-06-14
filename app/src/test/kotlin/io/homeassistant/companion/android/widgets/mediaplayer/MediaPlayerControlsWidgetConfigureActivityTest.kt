package io.homeassistant.companion.android.widgets.mediaplayer

import io.homeassistant.companion.android.common.data.integration.Entity
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MediaPlayerControlsWidgetConfigureActivityTest {
    @Test
    fun `Given selected entity ids when getting selected media player entity ids then returns valid ids in selection order`() {
        val result = getSelectedMediaPlayerEntityIds(
            " media_player.living_room , media_player.kitchen ",
            listOf(
                entity("media_player.kitchen"),
                entity("media_player.living_room"),
            ),
        )

        assertEquals("media_player.living_room,media_player.kitchen", result)
    }

    @Test
    fun `Given selected entity ids with unknown entries when getting selected media player entity ids then ignores unknown entries`() {
        val result = getSelectedMediaPlayerEntityIds(
            "media_player.living_room, light.kitchen, media_player.kitchen",
            listOf(
                entity("media_player.living_room"),
                entity("media_player.kitchen"),
            ),
        )

        assertEquals("media_player.living_room,media_player.kitchen", result)
    }

    @Test
    fun `Given no matching selected entity ids when getting selected media player entity ids then returns null`() {
        val result = getSelectedMediaPlayerEntityIds(
            "light.kitchen, switch.fan",
            listOf(entity("media_player.living_room")),
        )

        assertNull(result)
    }

    @Test
    fun `Given empty selected entity ids when getting selected media player entity ids then returns null`() {
        val result = getSelectedMediaPlayerEntityIds(
            " , ",
            listOf(entity("media_player.living_room")),
        )

        assertNull(result)
    }

    private fun entity(entityId: String) = Entity(
        entityId = entityId,
        state = "playing",
        attributes = emptyMap(),
        lastChanged = LocalDateTime.MIN,
        lastUpdated = LocalDateTime.MIN,
    )
}
