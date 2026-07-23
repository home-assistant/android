package io.homeassistant.companion.android.settings.qs

import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.settings.qs.ManageTilesState.Companion.changeServer
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManageTilesStateTest {

    private fun serverItems(vararg ids: Int) = ids.map { HADropdownItem(key = it, label = "Server $it") }

    private fun fakeEntity(entityId: String) = EntityDisplayItem(
        entityId = entityId,
        name = entityId,
        icon = mockk(),
    )

    @Test
    fun `Given a blank tile label when submitEnabled then it is false`() {
        val state = ManageTilesState(
            tileLabel = "",
            serversDropdownItems = serverItems(1),
            selectedServerId = 1,
            entityDisplayState = EntityDisplayState.Loaded(listOf(fakeEntity("light.test"))),
            selectedEntityId = "light.test",
        )
        assertFalse(state.submitEnabled)
    }

    @Test
    fun `Given no server matching the selected server id when submitEnabled then it is false`() {
        val state = ManageTilesState(
            tileLabel = "Label",
            serversDropdownItems = serverItems(1),
            selectedServerId = 2,
            entityDisplayState = EntityDisplayState.Loaded(listOf(fakeEntity("light.test"))),
            selectedEntityId = "light.test",
        )
        assertFalse(state.submitEnabled)
    }

    @Test
    fun `Given no entity matching the selected entity id when submitEnabled then it is false`() {
        val state = ManageTilesState(
            tileLabel = "Label",
            serversDropdownItems = serverItems(1),
            selectedServerId = 1,
            entityDisplayState = EntityDisplayState.Loaded(listOf(fakeEntity("light.other"))),
            selectedEntityId = "light.test",
        )
        assertFalse(state.submitEnabled)
    }

    @Test
    fun `Given a valid label, server and entity when submitEnabled then it is true`() {
        val state = ManageTilesState(
            tileLabel = "Label",
            serversDropdownItems = serverItems(1),
            selectedServerId = 1,
            entityDisplayState = EntityDisplayState.Loaded(listOf(fakeEntity("light.test"))),
            selectedEntityId = "light.test",
        )
        assertTrue(state.submitEnabled)
    }

    @Test
    fun `Given no icon selected when showResetIcon then it is false`() {
        val state = ManageTilesState(customIcon = null, selectedEntityId = "light.test")
        assertFalse(state.showResetIcon)
    }

    @Test
    fun `Given an icon selected but a blank entity id when showResetIcon then it is false`() {
        val state = ManageTilesState(customIcon = mockk(), selectedEntityId = "")
        assertFalse(state.showResetIcon)
    }

    @Test
    fun `Given an icon selected and a non-blank entity id when showResetIcon then it is true`() {
        val state = ManageTilesState(customIcon = mockk(), selectedEntityId = "light.test")
        assertTrue(state.showResetIcon)
    }

    @Test
    fun `Given a single server matching the selected server id when showServerSelector then it is false`() {
        val state = ManageTilesState(serversDropdownItems = serverItems(1), selectedServerId = 1)
        assertFalse(state.showServerSelector)
    }

    @Test
    fun `Given multiple servers when showServerSelector then it is true`() {
        val state = ManageTilesState(serversDropdownItems = serverItems(1, 2), selectedServerId = 1)
        assertTrue(state.showServerSelector)
    }

    @Test
    fun `Given a single server not matching the selected server id when showServerSelector then it is true`() {
        val state = ManageTilesState(serversDropdownItems = serverItems(1), selectedServerId = 99)
        assertTrue(state.showServerSelector)
    }

    @Test
    fun `Given a selected entity and loaded entities when changeServer then only the server-dependent fields are reset`() {
        val state = ManageTilesState(
            selectedServerId = 1,
            selectedEntityId = "light.test",
            entityDisplayState = EntityDisplayState.Loaded(listOf(fakeEntity("light.test"))),
            tileLabel = "Label",
            tileSubtitle = "Subtitle",
            customIcon = mockk(),
            selectedShouldVibrate = true,
            tileAuthRequired = true,
        )

        val newState = state.changeServer(2)

        assertEquals(2, newState.selectedServerId)
        assertNull(newState.selectedEntityId)
        assertEquals(EntityDisplayState.Loading, newState.entityDisplayState)
        // Everything not tied to the previous server must survive the switch.
        assertEquals(
            state.copy(selectedServerId = 2, selectedEntityId = null, entityDisplayState = EntityDisplayState.Loading),
            newState,
        )
    }
}
