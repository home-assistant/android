package io.homeassistant.companion.android.widgets.configure

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.RemoteException
import app.cash.turbine.test
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetDao
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
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
 * Tests the behaviour shared by every widget configuration ViewModel through a minimal fake
 * subclass, so the concrete ViewModel tests only have to cover what is specific to their widget.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class BaseWidgetConfigureViewModelTest {

    private val dao = mockk<WidgetDao<CameraWidgetEntity>>(relaxUnitFun = true)
    private val serverManager = mockk<ServerManager>()
    private val context = mockk<Context>(relaxed = true)

    private val widgetId = 42
    private val serverId = 1
    private val server = mockk<Server> {
        every { id } returns serverId
        every { friendlyName } returns "Home"
    }
    private val pendingEntity = CameraWidgetEntity(
        id = widgetId,
        serverId = serverId,
        entityId = "camera.front_door",
        tapAction = WidgetTapAction.REFRESH,
    )

    @BeforeEach
    fun setUp() {
        every { serverManager.serversFlow } returns flowOf(listOf(server))
        coEvery { serverManager.getServer() } returns server
        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { dao.get(any()) } returns null
        every { dao.getWidgetCountFlow() } returns flowOf(0)
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
    fun `Given a new widget when created then it falls back to the active server and reloads`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isUpdateWidget)
        assertEquals(serverId, state.selectedServerId)
        assertEquals(1, viewModel.reloadCount)
    }

    @Test
    fun `Given no active server when created then it falls back to the default server id`() = runTest {
        coEvery { serverManager.getServer() } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ServerManager.SERVER_ID_ACTIVE, viewModel.state.value.selectedServerId)
    }

    @Test
    fun `Given an existing widget when created then the stored configuration is restored`() = runTest {
        coEvery { dao.get(widgetId) } returns pendingEntity.copy(serverId = serverId)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isUpdateWidget)
        assertEquals(serverId, state.selectedServerId)
        assertEquals(commonR.string.update_widget, state.actionButtonLabel)
        assertEquals(1, viewModel.reloadCount)
    }

    @Test
    fun `Given an invalid widget id when created then no saved configuration is restored`() = runTest {
        val viewModel = createViewModel(widgetId = AppWidgetManager.INVALID_APPWIDGET_ID)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isUpdateWidget)
        assertEquals(serverId, state.selectedServerId)
        coVerify(exactly = 0) { dao.get(any()) }
    }

    @Test
    fun `Given a different server selected then the state changes and the configuration reloads`() = runTest {
        val newServerId = serverId + 1
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onServerSelected(newServerId)

        assertEquals(newServerId, viewModel.state.value.selectedServerId)
        assertFalse(viewModel.state.value.isActionEnabled)
        assertEquals(2, viewModel.reloadCount)
    }

    @Test
    fun `Given the already selected server when selected again then nothing changes`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val reloadsAfterInit = viewModel.reloadCount

        viewModel.onServerSelected(viewModel.state.value.selectedServerId)

        assertEquals(reloadsAfterInit, viewModel.reloadCount)
    }

    @Test
    fun `Given a background type selected then it is applied to the state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onBackgroundTypeSelected(WidgetBackgroundType.TRANSPARENT)

        assertEquals(WidgetBackgroundType.TRANSPARENT, viewModel.state.value.backgroundType)
    }

    @Test
    fun `Given an invalid widget id when the configuration is saved then an error is reported`() = runTest {
        val viewModel = createViewModel(widgetId = AppWidgetManager.INVALID_APPWIDGET_ID)
        advanceUntilIdle()

        viewModel.errors.test {
            assertFalse(viewModel.updateWidgetConfiguration(context))
            assertEquals(commonR.string.widget_update_error, awaitItem())
        }
        coVerify(exactly = 0) { dao.add(any()) }
    }

    @Test
    fun `Given the entity cannot be built when the configuration is saved then an error is reported`() = runTest {
        val viewModel = createViewModel(pendingEntity = null)
        advanceUntilIdle()

        viewModel.errors.test {
            assertFalse(viewModel.updateWidgetConfiguration(context))
            assertEquals(commonR.string.widget_update_error, awaitItem())
        }
        coVerify(exactly = 0) { dao.add(any()) }
    }

    @Test
    fun `Given a valid configuration when it is saved then the widget is persisted`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.updateWidgetConfiguration(context))

        coVerify { dao.add(pendingEntity) }
    }

    @Test
    fun `Given an SDK below Oreo when widget creation is requested then an error is reported`() = runTest {
        SdkVersion.sdkInt = Build.VERSION_CODES.N
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.errors.test {
            assertFalse(viewModel.requestWidgetCreation(context))
            assertEquals(commonR.string.widget_creation_error, awaitItem())
        }
    }

    @Test
    fun `Given a launcher that does not support pinning when widget creation is requested then an error is reported`() = runTest {
        withAppWidgetManager({ every { isRequestPinAppWidgetSupported } returns false }) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.errors.test {
                assertFalse(viewModel.requestWidgetCreation(context))
                assertEquals(commonR.string.widget_creation_error, awaitItem())
            }
        }
    }

    @Test
    fun `Given pinning support cannot be read when widget creation is requested then an error is reported`() = runTest {
        withAppWidgetManager({ every { isRequestPinAppWidgetSupported } throws RemoteException("binder") }) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.errors.test {
                assertFalse(viewModel.requestWidgetCreation(context))
                assertEquals(commonR.string.widget_creation_error, awaitItem())
            }
        }
    }

    @Test
    fun `Given the entity cannot be built when widget creation is requested then an error is reported`() = runTest {
        withAppWidgetManager({ every { isRequestPinAppWidgetSupported } returns true }) {
            val viewModel = createViewModel(pendingEntity = null)
            advanceUntilIdle()

            viewModel.errors.test {
                assertFalse(viewModel.requestWidgetCreation(context))
                assertEquals(commonR.string.widget_creation_error, awaitItem())
            }
        }
    }

    @Test
    fun `Given the launcher rejects the pin request then an error is reported`() = runTest {
        withAppWidgetManager({ every { isRequestPinAppWidgetSupported } returns true }) {
            val viewModel = createViewModel(pinResult = false)
            advanceUntilIdle()

            viewModel.errors.test {
                assertFalse(viewModel.requestWidgetCreation(context))
                assertEquals(commonR.string.widget_creation_error, awaitItem())
            }
        }
    }

    @Test
    fun `Given the launcher accepts the pin request then the widget is created`() = runTest {
        // The count increases once the launcher adds the widget, which is what the request waits for.
        every { dao.getWidgetCountFlow() } returns flowOf(0, 1)
        withAppWidgetManager({ every { isRequestPinAppWidgetSupported } returns true }) {
            val viewModel = createViewModel(pinResult = true)
            advanceUntilIdle()

            assertTrue(viewModel.requestWidgetCreation(context))
        }
    }

    @Test
    fun `Given an error already shown when the screen collects again then the error is not shown again`() = runTest {
        val viewModel = createViewModel(widgetId = AppWidgetManager.INVALID_APPWIDGET_ID)
        advanceUntilIdle()
        assertFalse(viewModel.updateWidgetConfiguration(context))

        viewModel.errors.test {
            assertEquals(commonR.string.widget_update_error, awaitItem())
        }
        // A new collector, e.g. the screen after a rotation, must not show the error again
        viewModel.errors.test {
            expectNoEvents()
        }
    }

    /** Sets the SDK to Oreo and installs a mocked [AppWidgetManager] so pinning checks are deterministic. */
    private inline fun withAppWidgetManager(configure: AppWidgetManager.() -> Unit, block: () -> Unit) {
        SdkVersion.sdkInt = Build.VERSION_CODES.O
        mockkStatic(AppWidgetManager::class) {
            val appWidgetManager = mockk<AppWidgetManager>()
            every { AppWidgetManager.getInstance(any()) } returns appWidgetManager
            appWidgetManager.configure()
            block()
        }
    }

    private fun createViewModel(
        widgetId: Int = this.widgetId,
        pendingEntity: CameraWidgetEntity? = this.pendingEntity,
        pinResult: Boolean = true,
    ) = FakeWidgetConfigureViewModel(
        serverManager = serverManager,
        widgetId = widgetId,
        dao = dao,
        pendingEntity = pendingEntity,
        pinResult = pinResult,
    )
}

/** Minimal state exposing only what [WidgetConfigureState] requires, plus a field the fake can mutate. */
private data class FakeConfigureState(
    override val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
    override val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    override val isUpdateWidget: Boolean = false,
    override val isActionEnabled: Boolean = true,
    val backgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
) : WidgetConfigureState<FakeConfigureState> {
    override fun withServersDropdownItems(items: List<HADropdownItem<Int>>) = copy(serversDropdownItems = items)

    override fun withSelectedServerId(serverId: Int) = copy(selectedServerId = serverId)

    override fun changeServer(serverId: Int) = copy(selectedServerId = serverId, isActionEnabled = false)

    override fun withBackgroundType(backgroundType: WidgetBackgroundType) = copy(backgroundType = backgroundType)
}

/**
 * Smallest concrete [BaseWidgetConfigureViewModel]: it records [reloadCount] and lets a test choose
 * the entity to build and whether the pin request is accepted, so the shared behaviour can be driven.
 */
private class FakeWidgetConfigureViewModel(
    serverManager: ServerManager,
    widgetId: Int,
    dao: WidgetDao<CameraWidgetEntity>,
    private val pendingEntity: CameraWidgetEntity?,
    private val pinResult: Boolean,
) : BaseWidgetConfigureViewModel<FakeConfigureState, CameraWidgetEntity>(
    serverManager = serverManager,
    widgetId = widgetId,
    dao = dao,
    initialState = FakeConfigureState(),
) {

    var reloadCount = 0
        private set

    override val widgetProviderClass: Class<*> = FakeWidgetConfigureViewModel::class.java

    override fun restoreState(state: FakeConfigureState, widget: CameraWidgetEntity) = state.copy(selectedServerId = widget.serverId, isUpdateWidget = true)

    override suspend fun buildPendingEntity(context: Context): CameraWidgetEntity? = pendingEntity

    override fun reload() {
        reloadCount++
    }

    override suspend fun requestPin(context: Context, widget: CameraWidgetEntity): Boolean = pinResult
}
