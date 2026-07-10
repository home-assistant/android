package io.homeassistant.companion.android.settings.qs

import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.Entity
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManageTilesStateTest {

    private fun serverItems(vararg ids: Int) = ids.map { HADropdownItem(key = it, label = "Server $it") }

    private fun fakeEntity(entityId: String) = Entity(
        entityId = entityId,
        state = "on",
        attributes = emptyMap(),
        lastChanged = LocalDateTime.now(),
        lastUpdated = LocalDateTime.now(),
    )

    @Test
    fun `Given a blank tile label when submitEnabled then it is false`() {
        val state = ManageTilesState(
            tileLabel = "",
            serversDropdownItems = serverItems(1),
            selectedServerId = 1,
            entities = listOf(fakeEntity("light.test")),
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
            entities = listOf(fakeEntity("light.test")),
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
            entities = listOf(fakeEntity("light.other")),
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
            entities = listOf(fakeEntity("light.test")),
            selectedEntityId = "light.test",
        )
        assertTrue(state.submitEnabled)
    }

    @Test
    fun `Given no icon selected when showResetIcon then it is false`() {
        val state = ManageTilesState(selectedIconId = null, selectedEntityId = "light.test")
        assertFalse(state.showResetIcon)
    }

    @Test
    fun `Given an icon selected but a blank entity id when showResetIcon then it is false`() {
        val state = ManageTilesState(selectedIconId = "mdi:account", selectedEntityId = "")
        assertFalse(state.showResetIcon)
    }

    @Test
    fun `Given an icon selected and a non-blank entity id when showResetIcon then it is true`() {
        val state = ManageTilesState(selectedIconId = "mdi:account", selectedEntityId = "light.test")
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
}
