package io.homeassistant.companion.android.widgets.configure

import android.content.Context
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetDao
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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

/**
 * Tests the entity-loading behaviour shared by entity-based configuration ViewModels through a
 * minimal fake subclass, so the concrete ViewModel tests only cover what is specific to their widget.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class BaseEntityWidgetConfigureViewModelTest {

    private val dao = mockk<WidgetDao<CameraWidgetEntity>>(relaxUnitFun = true)
    private val serverManager = mockk<ServerManager>()
    private val entitiesForDisplayManager = mockk<EntitiesForDisplayManager>()

    private val widgetId = 42
    private val serverId = 1
    private val server = mockk<Server> {
        every { id } returns serverId
        every { friendlyName } returns "Home"
    }
    private val light = createEntity("light.office")
    private val switch = createEntity("switch.fan")

    @BeforeEach
    fun setUp() {
        every { serverManager.serversFlow } returns flowOf(listOf(server))
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer() } returns server
        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { dao.get(any()) } returns null
        every { entitiesForDisplayManager.snapshotInContext(any(), any<(Entity) -> Boolean>()) } returns
            flowOf(displayStateOf(light.toDisplayItem()))
    }

    @Test
    fun `Given a registered server when created then its entities are loaded into the state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(displayStateOf(light.toDisplayItem()), viewModel.state.value.entityDisplayState)
        assertEquals(listOf(displayStateOf(light.toDisplayItem())), viewModel.loadedStates)
    }

    @Test
    fun `Given no registered server when created then the entity list is empty`() = runTest {
        coEvery { serverManager.isRegistered() } returns false

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            EntityDisplayState.Loaded(emptyList<EntityDisplayWithContext>()),
            viewModel.state.value.entityDisplayState,
        )
    }

    @Test
    fun `Given an entity filter when entities are loaded then it is forwarded to the manager`() = runTest {
        val filterSlot = slot<(Entity) -> Boolean>()
        every { entitiesForDisplayManager.snapshotInContext(any(), capture(filterSlot)) } returns
            flowOf(displayStateOf(light.toDisplayItem()))

        createViewModel(entityFilter = { it.domain == "light" })
        advanceUntilIdle()

        assertTrue(filterSlot.captured(light))
        assertFalse(filterSlot.captured(switch))
    }

    @Test
    fun `Given a different server selected then entities reload for that server`() = runTest {
        val newServerId = serverId + 1
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onServerSelected(newServerId)
        advanceUntilIdle()

        verify { entitiesForDisplayManager.snapshotInContext(newServerId, any<(Entity) -> Boolean>()) }
    }

    private fun createViewModel(entityFilter: (Entity) -> Boolean = { true }) = FakeEntityConfigureViewModel(
        serverManager = serverManager,
        widgetId = widgetId,
        dao = dao,
        entitiesForDisplayManager = entitiesForDisplayManager,
        entityFilter = entityFilter,
    )

    private fun displayStateOf(vararg items: EntityDisplayWithContext) = EntityDisplayState.Loaded(items.toList())

    private fun Entity.toDisplayItem() = EntityDisplayWithContext(EntityDisplayWithoutContext(this))

    private fun createEntity(entityId: String) = Entity(
        entityId = entityId,
        state = "on",
        attributes = emptyMap(),
        lastChanged = LocalDateTime.MIN,
        lastUpdated = LocalDateTime.MIN,
    )
}

/** Minimal entity state exposing the display state the base reads and writes. */
private data class FakeEntityConfigureState(
    override val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
    override val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    override val isUpdateWidget: Boolean = false,
    override val isActionEnabled: Boolean = false,
    val entityDisplayState: EntityDisplayState<EntityDisplayWithContext> = EntityDisplayState.Loading,
) : WidgetConfigureState<FakeEntityConfigureState> {
    override fun withServersDropdownItems(items: List<HADropdownItem<Int>>) = copy(serversDropdownItems = items)

    override fun withSelectedServerId(serverId: Int) = copy(selectedServerId = serverId)

    override fun changeServer(serverId: Int) = copy(selectedServerId = serverId)

    override fun withBackgroundType(backgroundType: WidgetBackgroundType) = this
}

/** Smallest concrete [BaseEntityWidgetConfigureViewModel]: it records each loaded display state. */
private class FakeEntityConfigureViewModel(
    serverManager: ServerManager,
    widgetId: Int,
    dao: WidgetDao<CameraWidgetEntity>,
    entitiesForDisplayManager: EntitiesForDisplayManager,
    entityFilter: (Entity) -> Boolean,
) : BaseEntityWidgetConfigureViewModel<FakeEntityConfigureState, CameraWidgetEntity>(
    serverManager = serverManager,
    widgetId = widgetId,
    dao = dao,
    entitiesForDisplayManager = entitiesForDisplayManager,
    initialState = FakeEntityConfigureState(),
    entityFilter = entityFilter,
) {

    val loadedStates = mutableListOf<EntityDisplayState<EntityDisplayWithContext>>()

    override val widgetProviderClass: Class<*> = FakeEntityConfigureViewModel::class.java

    override fun restoreState(state: FakeEntityConfigureState, widget: CameraWidgetEntity) = state.copy(selectedServerId = widget.serverId, isUpdateWidget = true)

    override suspend fun buildPendingEntity(context: Context): CameraWidgetEntity? = null

    override fun withEntityDisplayState(
        state: FakeEntityConfigureState,
        displayState: EntityDisplayState<EntityDisplayWithContext>,
    ) = state.copy(entityDisplayState = displayState)

    override fun onEntitiesLoaded(displayState: EntityDisplayState<EntityDisplayWithContext>) {
        loadedStates += displayState
    }
}
