package io.homeassistant.companion.android.widgets.button

import android.content.Context
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Flash
import io.github.timoptr.mdiicons.generated.Lightbulb
import io.homeassistant.companion.android.common.data.integration.Action
import io.homeassistant.companion.android.common.data.integration.ActionData
import io.homeassistant.companion.android.common.data.integration.ActionFields
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.mdiName
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.homeassistant.companion.android.widgets.common.ActionFieldBinder
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
class ButtonWidgetViewModelTest {

    private val dao = mockk<ButtonWidgetDao>(relaxUnitFun = true)
    private val integrationRepository = mockk<IntegrationRepository>()
    private val serverManager = mockk<ServerManager>()
    private val widgetContext = mockk<Context>(relaxed = true)

    private val widgetId = 42
    private val serverId = 1
    private val server = mockk<Server> {
        every { id } returns serverId
        every { friendlyName } returns "Home"
    }

    @BeforeEach
    fun setUp() {
        every { serverManager.serversFlow } returns flowOf(listOf(server))
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        coEvery { serverManager.getServer() } returns server
        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { dao.get(any()) } returns null
        every { dao.getWidgetCountFlow() } returns flowOf(0)
        coEvery { integrationRepository.getServices() } returns emptyList()
        coEvery { integrationRepository.getEntities() } returns emptyList()
    }

    @Test
    fun `Given an existing widget when created then persisted configuration is restored`() = runTest {
        coEvery { dao.get(widgetId) } returns createWidgetEntity(
            domain = "light",
            service = "turn_on",
            label = "Toggle Light",
            iconName = "lightbulb",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isUpdateWidget)
        assertEquals(serverId, state.selectedServerId)
        assertEquals("light.turn_on", state.actionText)
        assertEquals("Toggle Light", state.label)
        assertEquals(Mdi.Lightbulb, state.selectedIcon)
        assertEquals(Mdi.Lightbulb.mdiName, state.selectedIconId)
        assertEquals(WidgetBackgroundType.TRANSPARENT, state.selectedBackgroundType)
        assertEquals("#3A3A3A", state.textColorHex)
        assertFalse(state.requiresAuthentication)
    }

    @Test
    fun `Given actions and entities available on server when reloaded then serverActions are sorted and exposed`() = runTest {
        val action1 = createAction(domain = "switch", action = "turn_off")
        val action2 = createAction(domain = "light", action = "turn_on")
        coEvery { integrationRepository.getServices() } returns listOf(action1, action2)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf(action2, action1), state.serverActions)
    }

    @Test
    fun `Given server loading throws exception when reloaded then exception is handled gracefully`() = runTest {
        coEvery { integrationRepository.getServices() } throws RuntimeException("Connection failed")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.serverActions.isEmpty())
    }

    @Test
    fun `Given an action text updated when updated then actionText in state is updated and fields loaded`() = runTest {
        val action = createAction(
            domain = "light",
            action = "turn_on",
            target = true,
            fields = mapOf("brightness" to ActionFields()),
        )
        coEvery { integrationRepository.getServices() } returns listOf(action)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateActionText("light.turn_on")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("light.turn_on", state.actionText)
        assertEquals(2, state.dynamicFields.size)
        assertEquals("entity_id", state.dynamicFields[0].field)
        assertEquals("brightness", state.dynamicFields[1].field)
    }

    @Test
    fun `Given action with target enabled when action fields update then entity_id binder and domain entities are added`() = runTest {
        val action = createAction(domain = "light", action = "turn_on", target = true)
        val entity1 = createEntity("light.lamp")
        val entity2 = createEntity("switch.fan")
        val entity3 = createEntity("group.lights")
        coEvery { integrationRepository.getServices() } returns listOf(action)
        coEvery { integrationRepository.getEntities() } returns listOf(entity1, entity2, entity3)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateActionText("light.turn_on")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("all", "light.lamp", "group.lights"), state.domainEntities)
    }

    @Test
    fun `Given action with domain homeassistant when target enabled then all entities are included in domainEntities`() = runTest {
        val action = createAction(domain = "homeassistant", action = "restart", target = true)
        val entity1 = createEntity("light.lamp")
        val entity2 = createEntity("switch.fan")
        coEvery { integrationRepository.getServices() } returns listOf(action)
        coEvery { integrationRepository.getEntities() } returns listOf(entity1, entity2)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateActionText("homeassistant.restart")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("all", "light.lamp", "switch.fan"), state.domainEntities)
    }

    @Test
    fun `Given existing widget in database when updating action fields then previous service data values are restored into field binders`() = runTest {
        val existingWidget = createWidgetEntity(
            domain = "light",
            service = "turn_on",
            serviceData = "{\"entity_id\":\"light.lamp\",\"brightness\":\"255\"}",
        )
        coEvery { dao.get(widgetId) } returns existingWidget
        val action = createAction(
            domain = "light",
            action = "turn_on",
            target = true,
            fields = mapOf("brightness" to ActionFields()),
        )
        coEvery { integrationRepository.getServices() } returns listOf(action)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        val entityIdBinder = state.dynamicFields.firstOrNull { it.field == "entity_id" }
        val brightnessBinder = state.dynamicFields.firstOrNull { it.field == "brightness" }

        assertEquals("light.lamp", entityIdBinder?.value)
        assertEquals("255", brightnessBinder?.value)
    }

    @Test
    fun `Given extra fields in existing widget serviceData when updating action fields then extra field binders are added`() = runTest {
        val existingWidget = createWidgetEntity(
            domain = "light",
            service = "turn_on",
            serviceData = "{\"entity_id\":\"light.lamp\",\"custom_param\":\"value123\"}",
        )
        coEvery { dao.get(widgetId) } returns existingWidget
        val action = createAction(domain = "light", action = "turn_on", target = true)
        coEvery { integrationRepository.getServices() } returns listOf(action)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        val customBinder = state.dynamicFields.firstOrNull { it.field == "custom_param" }
        assertEquals("value123", customBinder?.value)
    }

    @Test
    fun `Given unknown action text when updated then dynamicFields are cleared`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateActionText("unknown.action")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.dynamicFields.isEmpty())
    }

    @Test
    fun `Given dynamic fields when a new field is added at position then it is inserted correctly`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.addDynamicField(0, ActionFieldBinder("light.turn_on", "brightness", "100"))

        val state = viewModel.state.value
        assertEquals(1, state.dynamicFields.size)
        assertEquals("brightness", state.dynamicFields[0].field)
        assertEquals("100", state.dynamicFields[0].value)
    }

    @Test
    fun `Given a dynamic field when value is updated then state reflects updated value`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.addDynamicField(0, ActionFieldBinder("light.turn_on", "brightness", "100"))
        viewModel.updateDynamicField(0, "200")

        val state = viewModel.state.value
        assertEquals("200", state.dynamicFields[0].value)
    }

    @Test
    fun `Given icon selection when changed then selectedIcon and selectedIconId reflect selection`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectIcon(Mdi.Lightbulb)
        assertEquals(Mdi.Lightbulb, viewModel.state.value.selectedIcon)
        assertEquals(Mdi.Lightbulb.mdiName, viewModel.state.value.selectedIconId)

        viewModel.selectIcon(null)
        assertEquals(Mdi.Flash, viewModel.state.value.selectedIcon)
        assertEquals(Mdi.Flash.mdiName, viewModel.state.value.selectedIconId)
    }

    @Test
    fun `Given options updated when updateOption called then state values are updated`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateOption(
            label = "My Light",
            selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
            textColorHex = "#FFFFFF",
            requiresAuthentication = true,
        )

        val state = viewModel.state.value
        assertEquals("My Light", state.label)
        assertEquals(WidgetBackgroundType.TRANSPARENT, state.selectedBackgroundType)
        assertEquals("#FFFFFF", state.textColorHex)
        assertTrue(state.requiresAuthentication)
    }

    @Test
    fun `Given valid selections when configuration is saved then widget entity is built and persisted to database`() = runTest {
        val action = createAction(domain = "light", action = "turn_on", target = true)
        coEvery { integrationRepository.getServices() } returns listOf(action)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateActionText("light.turn_on")
        advanceUntilIdle()

        viewModel.updateDynamicField(0, "light.living_room")
        viewModel.updateOption(
            label = "Living Room Light",
            selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
            textColorHex = "#FFFFFF",
            requiresAuthentication = true,
        )
        viewModel.selectIcon(Mdi.Lightbulb)

        assertTrue(viewModel.updateWidgetConfiguration(widgetContext))

        coVerify {
            dao.add(
                ButtonWidgetEntity(
                    id = widgetId,
                    serverId = serverId,
                    domain = "light",
                    service = "turn_on",
                    serviceData = "{\"entity_id\":\"light.living_room\"}",
                    label = "Living Room Light",
                    iconName = Mdi.Lightbulb.mdiName,
                    backgroundType = WidgetBackgroundType.TRANSPARENT,
                    textColor = "#FFFFFF",
                    requireAuthentication = true,
                ),
            )
        }
    }

    @Test
    fun `Given dynamic field with comma separated string when saving then value is cleaned into list`() = runTest {
        val action = createAction(domain = "light", action = "turn_on", target = true)
        coEvery { integrationRepository.getServices() } returns listOf(action)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateActionText("light.turn_on")
        advanceUntilIdle()

        viewModel.updateDynamicField(0, " light.lamp1 , light.lamp2 ")

        assertTrue(viewModel.updateWidgetConfiguration(widgetContext))

        coVerify {
            dao.add(
                match { entity ->
                    entity.serviceData.contains("[\"light.lamp1\",\"light.lamp2\"]")
                },
            )
        }
    }

    @Test
    fun `Given server selection changed when onServerSelected called then serverId updates and state resets actionText`() = runTest {
        val newServerId = 2
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateActionText("light.turn_on")
        viewModel.onServerSelected(newServerId)

        val state = viewModel.state.value
        assertEquals(newServerId, state.selectedServerId)
        assertEquals("", state.actionText)
        assertFalse(state.isActionEnabled)
    }

    private fun createViewModel(preselectedEntityId: String? = null) = ButtonWidgetViewModel(
        widgetId = widgetId,
        preselectedEntityId = preselectedEntityId,
        serverManager = serverManager,
        buttonWidgetDao = dao,
    )

    private fun createAction(
        domain: String = "light",
        action: String = "turn_on",
        target: Any? = true,
        fields: Map<String, ActionFields> = emptyMap(),
    ) = Action(
        domain = domain,
        action = action,
        actionData = ActionData(
            name = "Action $domain.$action",
            target = target,
            fields = fields,
        ),
    )

    private fun createEntity(entityId: String) = Entity(
        entityId = entityId,
        state = "on",
        attributes = emptyMap(),
        lastChanged = LocalDateTime.MIN,
        lastUpdated = LocalDateTime.MIN,
    )

    private fun createWidgetEntity(
        domain: String = "light",
        service: String = "turn_on",
        serviceData: String = "{}",
        label: String = "Turn on light",
        iconName: String = "flash",
    ) = ButtonWidgetEntity(
        id = widgetId,
        serverId = serverId,
        iconName = iconName,
        domain = domain,
        service = service,
        serviceData = serviceData,
        label = label,
        backgroundType = WidgetBackgroundType.TRANSPARENT,
        textColor = "#3A3A3A",
        requireAuthentication = false,
    )
}
