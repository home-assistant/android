package io.homeassistant.companion.android.widgets.grid

import androidx.glance.GlanceId
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.GridWidgetDao
import io.homeassistant.companion.android.database.widget.GridWidgetEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GridWidgetPressActionTest {

    private val entryPoints = object : PressEntityAction.PressEntityActionEntryPoint {
        val dao = mockk<GridWidgetDao>()
        val serverManager = mockk<ServerManager>()

        override fun serverManager(): ServerManager = serverManager
        override fun gridWidgetDao(): GridWidgetDao = dao
    }

    private data class FakeGlanceId(val id: Int) : GlanceId

    @Test
    fun `Given a widgetID when not present in DAO and invoking onAction then do nothing`() = runTest {
        val action = spyk<PressEntityAction>()
        val glanceManager = mockk<GlanceAppWidgetManager>()
        val parameters = actionParametersOf(ENTITY_ID_KEY to "switch.test")
        val widgetId = 1

        every { action.getEntryPoints(any()) } returns entryPoints
        every { action.getGlanceManager(any()) } returns glanceManager
        every { glanceManager.getAppWidgetId(any()) } returns widgetId

        coEvery { entryPoints.gridWidgetDao().get(widgetId) } returns null

        action.onAction(mockk(), FakeGlanceId(widgetId), parameters)

        coVerify(exactly = 0) { entryPoints.serverManager().integrationRepository(any()) }
    }

    @Test
    fun `Given a widgetID when present in DAO and invoking onAction then call integration repository`() {
        val action = spyk<PressEntityAction>()
        val glanceManager = mockk<GlanceAppWidgetManager>()
        val entityId = "switch.test"
        val parameters = actionParametersOf(ENTITY_ID_KEY to entityId)
        val widgetId = 1
        val serverId = 123
        val entity = GridWidgetEntity(widgetId, serverId, "Label", emptyList())
        val integrationRepository = mockk<IntegrationRepository>(relaxed = true)

        every { action.getEntryPoints(any()) } returns entryPoints
        every { action.getGlanceManager(any()) } returns glanceManager
        every { glanceManager.getAppWidgetId(any()) } returns widgetId

        coEvery { entryPoints.gridWidgetDao().get(widgetId) } returns entity
        coEvery { entryPoints.serverManager().integrationRepository(serverId) } returns integrationRepository

        // GridGlanceAppWidget.update() will throw IllegalArgumentException when called, so use it to verify onAction
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runTest {
                action.onAction(mockk(), FakeGlanceId(widgetId), parameters)
            }
        }
        assertEquals("Invalid Glance ID", exception.message)

        coVerify { integrationRepository.callAction("switch", "toggle", mapOf("entity_id" to entityId)) }
    }
}
