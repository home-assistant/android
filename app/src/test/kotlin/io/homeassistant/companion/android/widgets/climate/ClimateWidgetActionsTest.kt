package io.homeassistant.companion.android.widgets.climate

import androidx.glance.GlanceId
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.homeassistant.companion.android.common.data.integration.HvacMode
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.database.widget.ClimateWidgetDao
import io.homeassistant.companion.android.database.widget.ClimateWidgetEntity
import io.mockk.called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class ClimateWidgetActionsTest {

    private val entryPoints = object : ControlClimateAction.ClimateActionEntryPoint {
        val dao = mockk<ClimateWidgetDao>()
        val webSocketRepository = mockk<WebSocketRepository>()
        val serverManager = mockk<ServerManager>().apply {
            coEvery { webSocketRepository(any()) } returns webSocketRepository
        }

        override fun serverManager(): ServerManager = serverManager

        override fun climateDao(): ClimateWidgetDao = dao
    }

    private data class FakeGlanceId(val id: Int) : GlanceId

    @Test
    fun `Given a widgetID when not present in DAO and invoking onAction then do nothing`() = runTest {
        val action = spyk<ControlClimateAction>()
        val glanceManager = mockk<GlanceAppWidgetManager>()
        val parameters = actionParametersOf(IS_INCREASE_KEY to true)
        val widgetId = 1

        every { action.getEntryPoints(any()) } returns entryPoints
        every { action.getGlanceManager(any()) } returns glanceManager
        every { glanceManager.getAppWidgetId(any()) } returns widgetId

        coEvery { entryPoints.climateDao().get(widgetId) } returns null

        action.onAction(mockk(), FakeGlanceId(widgetId), parameters)

        verify(exactly = 0) {
            entryPoints.serverManager() wasNot called
        }
        coVerify(exactly = 1) {
            entryPoints.climateDao().get(widgetId)
        }
    }

    @Test
    fun `Given widgetID when increasing temperature then set climate temperature and invoke update`() = runTest {
        val action = spyk<ControlClimateAction>()
        val glanceManager = mockk<GlanceAppWidgetManager>()
        val widgetId = 1
        val widgetEntity = ClimateWidgetEntity(
            id = widgetId,
            serverId = 42,
            entityId = "climate.test",
            latestUpdateData = ClimateWidgetEntity.LastUpdateData(
                climateTemp = 24f,
                currentTemp = 22f,
                minTemp = 15f,
                maxTemp = 30f,
                stepTemp = 1f,
            ),
        )
        val parameters = actionParametersOf(IS_INCREASE_KEY to true)

        every { action.getEntryPoints(any()) } returns entryPoints
        every { action.getGlanceManager(any()) } returns glanceManager
        every { glanceManager.getAppWidgetId(any()) } returns widgetId

        coEvery { entryPoints.climateDao().get(widgetId) } returns widgetEntity
        coEvery { entryPoints.serverManager().getServer(42) } returns mockk()
        coEvery {
            entryPoints.serverManager().webSocketRepository(42).setClimateTemperature(
                "climate.test",
                "25.0",
            )
        } returns true

        try {
            action.onAction(mockk(), FakeGlanceId(widgetId), parameters)
            fail { "onAction should fail with invalid glance ID" }
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid Glance ID", e.message)
        }

        coVerify(exactly = 1) {
            entryPoints.climateDao().get(widgetId)
            entryPoints.serverManager().webSocketRepository(42)
            entryPoints.serverManager().webSocketRepository(42)
                .setClimateTemperature("climate.test", "25.0")
        }
    }

    @Test
    fun `Given temperature at max when increasing temperature then sets max temperature`() = runTest {
        val action = spyk<ControlClimateAction>()
        val glanceManager = mockk<GlanceAppWidgetManager>()
        val widgetId = 1
        val widgetEntity = ClimateWidgetEntity(
            id = widgetId,
            serverId = 42,
            entityId = "climate.test",
            latestUpdateData = ClimateWidgetEntity.LastUpdateData(
                climateTemp = 30f,
                currentTemp = 22f,
                minTemp = 15f,
                maxTemp = 30f,
                stepTemp = 1f,
            ),
        )

        every { action.getEntryPoints(any()) } returns entryPoints
        every { action.getGlanceManager(any()) } returns glanceManager
        every { glanceManager.getAppWidgetId(any()) } returns widgetId

        coEvery { entryPoints.climateDao().get(widgetId) } returns widgetEntity
        coEvery { entryPoints.serverManager().getServer(42) } returns mockk()
        coEvery {
            entryPoints.serverManager().webSocketRepository(42)
                .setClimateTemperature("climate.test", "30.0")
        } returns true

        try {
            action.onAction(
                mockk(),
                FakeGlanceId(widgetId),
                actionParametersOf(IS_INCREASE_KEY to true),
            )
            fail { "onAction should fail with invalid glance ID" }
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid Glance ID", e.message)
        }

        coVerify(exactly = 1) {
            entryPoints.serverManager().webSocketRepository(42)
                .setClimateTemperature("climate.test", "30.0")
        }
    }

    @Test
    fun `Given temperature at min when decreasing temperature then sets min temperature`() = runTest {
        val action = spyk<ControlClimateAction>()
        val glanceManager = mockk<GlanceAppWidgetManager>()
        val widgetId = 1
        val widgetEntity = ClimateWidgetEntity(
            id = widgetId,
            serverId = 42,
            entityId = "climate.test",
            latestUpdateData = ClimateWidgetEntity.LastUpdateData(
                climateTemp = 15f,
                currentTemp = 22f,
                minTemp = 15f,
                maxTemp = 30f,
                stepTemp = 1f,
            ),
        )

        every { action.getEntryPoints(any()) } returns entryPoints
        every { action.getGlanceManager(any()) } returns glanceManager
        every { glanceManager.getAppWidgetId(any()) } returns widgetId

        coEvery { entryPoints.climateDao().get(widgetId) } returns widgetEntity
        coEvery { entryPoints.serverManager().getServer(42) } returns mockk()
        coEvery {
            entryPoints.serverManager().webSocketRepository(42)
                .setClimateTemperature("climate.test", "15.0")
        } returns true

        try {
            action.onAction(
                mockk(),
                FakeGlanceId(widgetId),
                actionParametersOf(IS_INCREASE_KEY to false),
            )
            fail { "onAction should fail with invalid glance ID" }
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid Glance ID", e.message)
        }

        coVerify(exactly = 1) {
            entryPoints.serverManager().webSocketRepository(42)
                .setClimateTemperature("climate.test", "15.0")
        }
    }

    @Test
    fun `Given widget without temperature data when changing temperature then does nothing`() = runTest {
        val action = spyk<ControlClimateAction>()
        val glanceManager = mockk<GlanceAppWidgetManager>()
        val widgetId = 1
        val widgetEntity = ClimateWidgetEntity(
            id = widgetId,
            serverId = 42,
            entityId = "climate.test",
            latestUpdateData = ClimateWidgetEntity.LastUpdateData(
                climateTemp = null,
                currentTemp = 22f,
                minTemp = 15f,
                maxTemp = 30f,
                stepTemp = 1f,
            ),
        )

        every { action.getEntryPoints(any()) } returns entryPoints
        every { action.getGlanceManager(any()) } returns glanceManager
        every { glanceManager.getAppWidgetId(any()) } returns widgetId

        coEvery { entryPoints.climateDao().get(widgetId) } returns widgetEntity
        coEvery { entryPoints.serverManager().getServer(42) } returns mockk()

        try {
            action.onAction(
                mockk(),
                FakeGlanceId(widgetId),
                actionParametersOf(IS_INCREASE_KEY to true),
            )
            fail { "onAction should fail with invalid glance ID" }
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid Glance ID", e.message)
        }

        coVerify(exactly = 0) {
            entryPoints.serverManager().webSocketRepository(any())
        }
    }

    @Test
    fun `Given widgetID when setting HVAC mode then set climate HVAC mode and invoke update`() = runTest {
        val action = spyk<ControlClimateAction>()
        val glanceManager = mockk<GlanceAppWidgetManager>()
        val widgetId = 1
        val widgetEntity = ClimateWidgetEntity(
            id = widgetId,
            serverId = 42,
            entityId = "climate.test",
        )
        val parameters = actionParametersOf(HVAC_MODE_KEY to HvacMode.HEAT)

        every { action.getEntryPoints(any()) } returns entryPoints
        every { action.getGlanceManager(any()) } returns glanceManager
        every { glanceManager.getAppWidgetId(any()) } returns widgetId

        coEvery { entryPoints.climateDao().get(widgetId) } returns widgetEntity
        coEvery { entryPoints.serverManager().getServer(42) } returns mockk()
        coEvery {
            entryPoints.serverManager().webSocketRepository(42)
                .setClimateHvacMode("climate.test", "heat")
        } returns true

        try {
            action.onAction(mockk(), FakeGlanceId(widgetId), parameters)
            fail { "onAction should fail with invalid glance ID" }
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid Glance ID", e.message)
        }

        coVerify(exactly = 1) {
            entryPoints.climateDao().get(widgetId)
            entryPoints.serverManager().webSocketRepository(42)
                .setClimateHvacMode("climate.test", "heat")
        }
    }

    @Test
    fun `Given a widgetID with server removed when present in DAO and invoking onAction then do nothing`() = runTest {
        val action = spyk<ControlClimateAction>()
        val glanceManager = mockk<GlanceAppWidgetManager>()
        val widgetId = 1
        val climateWidget = ClimateWidgetEntity(1, 42, "climate.test")
        val parameters = actionParametersOf(IS_INCREASE_KEY to true)

        every { action.getEntryPoints(any()) } returns entryPoints
        every { action.getGlanceManager(any()) } returns glanceManager
        every { glanceManager.getAppWidgetId(any()) } returns widgetId
        coEvery { entryPoints.serverManager.getServer(42) } returns null

        coEvery { entryPoints.climateDao().get(widgetId) } returns climateWidget

        action.onAction(mockk(), FakeGlanceId(widgetId), parameters)

        coVerify(exactly = 1) {
            entryPoints.climateDao().get(widgetId)
            entryPoints.serverManager().getServer(42)
        }
        coVerify(exactly = 0) {
            entryPoints.serverManager().webSocketRepository(any())
        }
    }
}
