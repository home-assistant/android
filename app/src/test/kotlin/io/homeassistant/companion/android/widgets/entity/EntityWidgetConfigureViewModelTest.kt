package io.homeassistant.companion.android.widgets.entity

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.StaticWidgetDao
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class EntityWidgetConfigureViewModelTest {

    private val dao = mockk<StaticWidgetDao>(relaxUnitFun = true)
    private val integrationRepository = mockk<IntegrationRepository>()
    private val serverManager = mockk<ServerManager>()

    private val widgetId = 42
    private val serverId = 1
    private val server = mockk<Server> {
        every { id } returns serverId
    }
    private val entity = createEntity(
        entityId = "light.office",
        attributes = mapOf("friendly_name" to "Office light", "brightness" to 128),
    )

    @BeforeEach
    fun setUp() {
        every { serverManager.serversFlow } returns flowOf(emptyList())
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        coEvery { integrationRepository.getEntities() } returns listOf(entity)
        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { dao.get(any()) } returns null
    }

    @Test
    fun `Given an existing widget when setup completes then persisted configuration is restored`() = runTest {
        coEvery { dao.get(widgetId) } returns createWidgetEntity()
        val viewModel = createViewModel()

        viewModel.onSetup(widgetId, WidgetBackgroundType.DAYNIGHT, TEXT_COLORS)
        advanceUntilIdle()

        assertTrue(viewModel.isUpdateWidget)
        assertEquals(serverId, viewModel.selectedServerId)
        assertEquals(entity.entityId, viewModel.selectedEntityId)
        assertTrue(viewModel.appendAttributes)
        assertEquals(listOf("brightness", "friendly_name"), viewModel.selectedAttributeIds)
        assertEquals("Office light", viewModel.label)
        assertEquals("28", viewModel.textSize)
        assertEquals(" - ", viewModel.stateSeparator)
        assertEquals(", ", viewModel.attributeSeparator)
        assertEquals(WidgetTapAction.TOGGLE, viewModel.selectedTapAction)
        assertEquals(WidgetBackgroundType.TRANSPARENT, viewModel.selectedBackgroundType)
        assertEquals(EntityWidgetTextColor.BLACK, viewModel.selectedTextColor)
    }

    @Test
    fun `Given setup is called again when state changed then current state is preserved`() = runTest {
        val viewModel = createViewModel(entity.entityId)
        viewModel.onSetup(widgetId, WidgetBackgroundType.DAYNIGHT, TEXT_COLORS)
        advanceUntilIdle()
        viewModel.onBackgroundTypeSelected(WidgetBackgroundType.TRANSPARENT)

        viewModel.onSetup(widgetId, WidgetBackgroundType.DYNAMICCOLOR, TEXT_COLORS)
        advanceUntilIdle()

        assertEquals(WidgetBackgroundType.TRANSPARENT, viewModel.selectedBackgroundType)
        coVerify(exactly = 0) { dao.get(widgetId) }
    }

    @Test
    fun `Given valid selections when configuration is saved then widget data is persisted`() = runTest {
        val viewModel = createViewModel()
        viewModel.onSetup(widgetId, WidgetBackgroundType.DAYNIGHT, TEXT_COLORS)

        viewModel.entities.test {
            advanceUntilIdle()
            assertEquals(listOf(entity), expectMostRecentItem())

            viewModel.onEntitySelected(entity.entityId)
            viewModel.onSelectedEntityLoaded(entity)
            viewModel.onAppendAttributesChanged(true)
            viewModel.onAttributeAdded("brightness")
            viewModel.onAttributeSeparatorChanged(", ")
            viewModel.onStateSeparatorChanged(" - ")
            viewModel.onTextSizeChanged("36sp")
            viewModel.onTapActionSelected(WidgetTapAction.TOGGLE)
            viewModel.onBackgroundTypeSelected(WidgetBackgroundType.TRANSPARENT)
            viewModel.onTextColorSelected(EntityWidgetTextColor.BLACK)

            assertTrue(viewModel.isValidSelection())
            viewModel.updateWidgetConfiguration()

            coVerify {
                dao.add(
                    StaticWidgetEntity(
                        id = widgetId,
                        serverId = serverId,
                        entityId = entity.entityId,
                        attributeIds = "brightness",
                        label = "Office light",
                        textSize = 36F,
                        stateSeparator = " - ",
                        attributeSeparator = ", ",
                        tapAction = WidgetTapAction.TOGGLE,
                        lastUpdate = "",
                        backgroundType = WidgetBackgroundType.TRANSPARENT,
                        textColor = TEXT_COLORS.black,
                    ),
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given a generated label when entity changes then label follows the selected entity`() = runTest {
        val secondEntity = createEntity(
            entityId = "switch.fan",
            attributes = mapOf("friendly_name" to "Fan"),
        )
        coEvery { integrationRepository.getEntities() } returns listOf(entity, secondEntity)
        val viewModel = createViewModel()

        viewModel.entities.test {
            advanceUntilIdle()
            assertEquals(listOf(entity, secondEntity), expectMostRecentItem())

            viewModel.onEntitySelected(entity.entityId)
            viewModel.onSelectedEntityLoaded(entity)
            assertEquals("Office light", viewModel.label)

            viewModel.onEntitySelected(secondEntity.entityId)
            viewModel.onSelectedEntityLoaded(secondEntity)
            assertEquals("Fan", viewModel.label)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given a server change when an entity was selected then dependent state is cleared`() = runTest {
        val newServerId = serverId + 1
        val viewModel = createViewModel(entity.entityId)
        viewModel.onSetup(widgetId, WidgetBackgroundType.DAYNIGHT, TEXT_COLORS)
        advanceUntilIdle()
        viewModel.onAttributeAdded("brightness")
        viewModel.onTapActionSelected(WidgetTapAction.TOGGLE)

        viewModel.onServerSelected(newServerId)

        assertEquals(newServerId, viewModel.selectedServerId)
        assertEquals(null, viewModel.selectedEntityId)
        assertTrue(viewModel.selectedAttributeIds.isEmpty())
        assertEquals(WidgetTapAction.REFRESH, viewModel.selectedTapAction)
        assertFalse(viewModel.isValidSelection())
    }

    @Test
    fun `Given custom attributes when attributes are added then input is parsed and cleared`() {
        val viewModel = createViewModel(entity.entityId)
        viewModel.onAttributeAdded("brightness")
        viewModel.onCustomAttributeChanged("friendly_name, unit_of_measurement, brightness")

        viewModel.onCustomAttributesAdded()

        assertEquals(listOf("brightness", "friendly_name", "unit_of_measurement"), viewModel.selectedAttributeIds)
        assertEquals("", viewModel.viewState.customAttribute)
    }

    @Test
    fun `Given an invalid text size when view state is read then action is disabled`() {
        val viewModel = createViewModel(entity.entityId)

        viewModel.onTextSizeChanged("")

        assertFalse(viewModel.viewState.hasValidTextSize)
        assertFalse(viewModel.viewState.isActionEnabled)
    }

    private fun createViewModel(preselectedEntityId: String? = null) = EntityWidgetConfigureViewModel(dao, serverManager, preselectedEntityId)

    private fun createWidgetEntity() = StaticWidgetEntity(
        id = widgetId,
        serverId = serverId,
        entityId = entity.entityId,
        attributeIds = "brightness,friendly_name",
        label = "Office light",
        textSize = 28F,
        stateSeparator = " - ",
        attributeSeparator = ", ",
        tapAction = WidgetTapAction.TOGGLE,
        lastUpdate = "on",
        backgroundType = WidgetBackgroundType.TRANSPARENT,
        textColor = TEXT_COLORS.black,
    )

    companion object {
        private val TEXT_COLORS = EntityWidgetTextColors(white = "#FFFFFF", black = "#000000")

        private fun createEntity(entityId: String, attributes: Map<String, Any?>) = Entity(
            entityId = entityId,
            state = "on",
            attributes = attributes,
            lastChanged = LocalDateTime.MIN,
            lastUpdated = LocalDateTime.MIN,
        )
    }
}
