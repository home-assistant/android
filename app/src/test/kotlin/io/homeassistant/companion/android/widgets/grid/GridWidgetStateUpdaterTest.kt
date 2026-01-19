package io.homeassistant.companion.android.widgets.grid

import android.content.Context
import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.GridWidgetDao
import io.homeassistant.companion.android.database.widget.GridWidgetEntity
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GridWidgetStateUpdaterTest {

    private val dao = mockk<GridWidgetDao>()
    private val serverManager = mockk<ServerManager>()
    private val integrationRepository = mockk<IntegrationRepository>()
    private val context = mockk<Context>(relaxed = true)
    private val updater = GridWidgetStateUpdater(dao, serverManager, context)

    @Test
    fun `Given widget in DAO when subscribing to stateFlow then emits initial state`() = runTest {
        val widgetId = 42
        val serverId = 1
        val item1 = GridWidgetEntity.Item(1, "switch.test", "Switch", "mdi:switch")
        val entity = GridWidgetEntity(widgetId, serverId, "Label", listOf(item1))

        coEvery { dao.getFlow(widgetId) } returns flowOf(entity)
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
        coEvery { integrationRepository.getEntity("switch.test") } returns null
        coEvery { integrationRepository.getEntityUpdates(listOf("switch.test")) } returns emptyFlow()

        updater.stateFlow(widgetId).test {
            val state = awaitItem() as GridStateWithData
            assertEquals("Label", state.label)
            assertEquals(1, state.items.size)
            assertEquals("switch.test", state.items[0].id)
            assertEquals("Switch", state.items[0].label)
            awaitComplete()
        }
    }

    @Test
    fun `Given widget in DAO with entity updates when subscribing to stateFlow then emits updated state`() = runTest {
        val widgetId = 42
        val serverId = 1
        val item1 = GridWidgetEntity.Item(1, "switch.test", "Switch", "mdi:switch")
        val entity = GridWidgetEntity(widgetId, serverId, "Label", listOf(item1))

        coEvery { dao.getFlow(widgetId) } returns flowOf(entity)
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
        coEvery { integrationRepository.getEntity("switch.test") } returns null

        val updatedEntity = Entity(
            entityId = "switch.test",
            state = "on",
            attributes = mapOf<String, Any>(),
            lastChanged = LocalDateTime.now(),
            lastUpdated = LocalDateTime.now(),
        )

        coEvery { integrationRepository.getEntityUpdates(listOf("switch.test")) } returns flowOf(updatedEntity)

        updater.stateFlow(widgetId).test {
            awaitItem() // Ignore initial

            val updatedState = awaitItem() as GridStateWithData
            assertEquals("switch.test", updatedState.items[0].id)
            assertEquals("Switch", updatedState.items[0].label)
            assertTrue(updatedState.items[0].isActive)
            awaitComplete()
        }
    }
}
