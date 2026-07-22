package io.homeassistant.companion.android.widgets.entity

import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.GetEntitiesForDisplayUseCase
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
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class EntityWidgetConfigureViewModelTest {

    private val dao = mockk<StaticWidgetDao>(relaxUnitFun = true)
    private val integrationRepository = mockk<IntegrationRepository>()
    private val serverManager = mockk<ServerManager>()
    private val getEntitiesForDisplay = mockk<GetEntitiesForDisplayUseCase>()

    private val widgetId = 42
    private val serverId = 1
    private val server = mockk<Server> {
        every { id } returns serverId
        every { friendlyName } returns "Home"
    }
    private val entity = createEntity(
        entityId = "light.office",
        attributes = mapOf("brightness" to 128),
    )

    @BeforeEach
    fun setUp() {
        every { serverManager.serversFlow } returns flowOf(listOf(server))
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        coEvery { integrationRepository.getEntity(entity.entityId) } returns entity
        coEvery { serverManager.getServer() } returns server
        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { dao.get(any()) } returns null
        every { getEntitiesForDisplay(any(), any<(Entity) -> Boolean>()) } returns flowOf(displayStateOf(entity.toDisplayItem("Office light")))
    }

    @Test
    fun `Given an existing widget when created then persisted configuration is restored`() = runTest {
        coEvery { dao.get(widgetId) } returns createWidgetEntity()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isUpdateWidget)
        assertEquals(serverId, state.selectedServerId)
        assertEquals(entity.entityId, state.selectedEntityId)
        assertEquals(listOf("brightness", "friendly_name"), state.selectedAttributeIds)
        assertEquals("Office light", state.label)
        assertEquals("28", state.textSize)
        assertEquals(" - ", state.stateSeparator)
        assertEquals(", ", state.attributeSeparator)
        assertEquals(WidgetTapAction.TOGGLE, state.selectedTapAction)
        assertEquals(WidgetBackgroundType.TRANSPARENT, state.selectedBackgroundType)
        assertEquals(BLACK_HEX, state.textColorHex)
        assertEquals(commonR.string.update_widget, state.actionButtonLabel)
    }

    @Test
    fun `Given servers when created then they are exposed as dropdown items`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            listOf(HADropdownItem(key = serverId, label = "Home")),
            viewModel.state.value.serversDropdownItems,
        )
    }

    @Test
    fun `Given a selected entity when attributes are loaded then they are sorted and exposed`() = runTest {
        val viewModel = createViewModel(entity.entityId)
        advanceUntilIdle()

        assertEquals(listOf("brightness"), viewModel.state.value.availableAttributes)
    }

    @Test
    fun `Given valid selections when configuration is saved then widget data is persisted`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEntitySelected(entity.entityId)
        viewModel.onAttributeAdded("brightness")
        viewModel.onAttributeSeparatorChanged(", ")
        viewModel.onStateSeparatorChanged(" - ")
        viewModel.onTextSizeChanged("36sp")
        viewModel.onTapActionSelected(WidgetTapAction.TOGGLE)
        viewModel.onBackgroundTypeSelected(WidgetBackgroundType.TRANSPARENT)
        viewModel.onTextColorSelected(BLACK_HEX)
        advanceUntilIdle()

        assertTrue(viewModel.updateWidgetConfiguration())

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
                    textColor = BLACK_HEX,
                ),
            )
        }
    }

    @Test
    fun `Given a generated label when entity changes then label follows the selected entity`() = runTest {
        val secondEntity = createEntity(entityId = "switch.fan", attributes = emptyMap())
        coEvery { integrationRepository.getEntity(secondEntity.entityId) } returns secondEntity
        every { getEntitiesForDisplay(any(), any<(Entity) -> Boolean>()) } returns
            flowOf(displayStateOf(entity.toDisplayItem("Office light"), secondEntity.toDisplayItem("Fan")))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEntitySelected(entity.entityId)
        assertEquals("Office light", viewModel.state.value.label)

        viewModel.onEntitySelected(secondEntity.entityId)
        assertEquals("Fan", viewModel.state.value.label)
    }

    @Test
    fun `Given a user typed label when entity changes then the label is kept`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onLabelChanged("My widget")
        viewModel.onEntitySelected(entity.entityId)

        assertEquals("My widget", viewModel.state.value.label)
    }

    @Test
    fun `Given a server change when an entity was selected then dependent state is cleared`() = runTest {
        val newServerId = serverId + 1
        val viewModel = createViewModel(entity.entityId)
        advanceUntilIdle()
        viewModel.onAttributeAdded("brightness")
        viewModel.onTapActionSelected(WidgetTapAction.TOGGLE)

        viewModel.onServerSelected(newServerId)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(newServerId, state.selectedServerId)
        assertNull(state.selectedEntityId)
        assertTrue(state.selectedAttributeIds.isEmpty())
        assertTrue(state.availableAttributes.isEmpty())
        assertEquals(WidgetTapAction.REFRESH, state.selectedTapAction)
        assertFalse(state.isActionEnabled)
    }

    @Test
    fun `Given custom attributes when attributes are added then input is parsed and cleared`() = runTest {
        val viewModel = createViewModel(entity.entityId)
        viewModel.onAttributeAdded("brightness")
        viewModel.onCustomAttributeChanged("friendly_name, unit_of_measurement, brightness")

        viewModel.onCustomAttributesAdded()

        val state = viewModel.state.value
        assertEquals(listOf("brightness", "friendly_name", "unit_of_measurement"), state.selectedAttributeIds)
        assertEquals("", state.customAttribute)
    }

    @Test
    fun `Given an invalid text size when state is read then the error is set and the action is disabled`() = runTest {
        val viewModel = createViewModel(entity.entityId)

        viewModel.onTextSizeChanged("")

        val state = viewModel.state.value
        assertEquals(commonR.string.widget_text_size_error, state.textSizeError)
        assertFalse(state.isActionEnabled)
    }

    @Test
    fun `Given a valid text size when state is read then no error is reported`() = runTest {
        val viewModel = createViewModel(entity.entityId)

        viewModel.onTextSizeChanged("24")

        assertNull(viewModel.state.value.textSizeError)
    }

    private fun createViewModel(preselectedEntityId: String? = null) = EntityWidgetConfigureViewModel(
        staticWidgetDao = dao,
        serverManager = serverManager,
        getEntitiesForDisplay = getEntitiesForDisplay,
        widgetId = widgetId,
        preselectedEntityId = preselectedEntityId,
    )

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
        textColor = BLACK_HEX,
    )

    companion object {
        /** Hex of `colorWidgetButtonLabelBlack`, which is what the widget persists. */
        private const val BLACK_HEX = "#3A3A3A"

        private fun displayStateOf(vararg items: EntityDisplayItem) = EntityDisplayState.Loaded(items.toList())

        /** Display name comes from the entity registry in production, so it is set explicitly here. */
        private fun Entity.toDisplayItem(name: String) = EntityDisplayItem.from(this).copy(name = name)

        private fun createEntity(entityId: String, attributes: Map<String, Any?>) = Entity(
            entityId = entityId,
            state = "on",
            attributes = attributes,
            lastChanged = LocalDateTime.MIN,
            lastUpdated = LocalDateTime.MIN,
        )
    }
}
