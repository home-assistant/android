package io.homeassistant.companion.android.widgets.template

import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
class TemplateWidgetConfigureViewModelTest {

    private val dao = mockk<TemplateWidgetDao>(relaxUnitFun = true)
    private val integrationRepository = mockk<IntegrationRepository>()
    private val serverManager = mockk<ServerManager>()

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
        coEvery { dao.get(any()) } returns null
        every { dao.getWidgetCountFlow() } returns flowOf(0)
    }

    @Test
    fun `Given an existing widget when created then persisted configuration is restored`() = runTest {
        coEvery { dao.get(widgetId) } returns createWidgetEntity()
        coEvery { integrationRepository.renderTemplate("{{ states('sensor.temp') }}", emptyMap()) } returns "21.5"

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isUpdateWidget)
        assertEquals(serverId, state.selectedServerId)
        assertEquals("{{ states('sensor.temp') }}", state.template)
        assertEquals("21", state.textSize)
        assertEquals(WidgetBackgroundType.TRANSPARENT, state.selectedBackgroundType)
        assertEquals(BLACK_HEX, state.textColorHex)
        assertEquals(TemplatePreview.Rendered("21.5"), state.preview)
        assertTrue(state.isActionEnabled)
    }

    @Test
    fun `Given an existing widget with a blank template when created then nothing is rendered`() = runTest {
        coEvery { dao.get(widgetId) } returns createWidgetEntity(template = "")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(TemplatePreview.Empty, viewModel.state.value.preview)
        assertFalse(viewModel.state.value.isActionEnabled)
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
    fun `Given a template when changed then it is rendered against the selected server`() = runTest {
        coEvery { integrationRepository.renderTemplate("{{ 1 + 1 }}", emptyMap()) } returns "2"
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTemplateChanged("{{ 1 + 1 }}")
        advanceUntilIdle()

        assertEquals(TemplatePreview.Rendered("2"), viewModel.state.value.preview)
        assertTrue(viewModel.state.value.isActionEnabled)
    }

    @Test
    fun `Given a template cleared when changed then the preview goes back to empty`() = runTest {
        coEvery { integrationRepository.renderTemplate("{{ 1 }}", emptyMap()) } returns "1"
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onTemplateChanged("{{ 1 }}")
        advanceUntilIdle()

        viewModel.onTemplateChanged("")

        assertEquals(TemplatePreview.Empty, viewModel.state.value.preview)
        assertFalse(viewModel.state.value.isActionEnabled)
    }

    @Test
    fun `Given a template that fails to render then the error is exposed`() = runTest {
        coEvery { integrationRepository.renderTemplate("{{ broken", emptyMap()) } throws RuntimeException("boom")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTemplateChanged("{{ broken")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.preview is TemplatePreview.Error)
        assertFalse(viewModel.state.value.isActionEnabled)
    }

    @Test
    fun `Given valid selections when configuration is saved then widget data is persisted`() = runTest {
        coEvery { integrationRepository.renderTemplate("{{ 1 }}", emptyMap()) } returns "1"
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTemplateChanged("{{ 1 }}")
        advanceUntilIdle()
        viewModel.onTextSizeChanged("18")
        viewModel.onBackgroundTypeSelected(WidgetBackgroundType.TRANSPARENT)
        viewModel.onTextColorSelected(BLACK_HEX)
        advanceUntilIdle()

        assertTrue(viewModel.updateWidgetConfiguration())

        coVerify {
            dao.add(
                TemplateWidgetEntity(
                    id = widgetId,
                    serverId = serverId,
                    template = "{{ 1 }}",
                    textSize = 18F,
                    lastUpdate = "Loading",
                    backgroundType = WidgetBackgroundType.TRANSPARENT,
                    textColor = BLACK_HEX,
                ),
            )
        }
    }

    @Test
    fun `Given an invalid configuration when configuration is saved then it is rejected`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.updateWidgetConfiguration())
    }

    private fun createViewModel() = TemplateWidgetConfigureViewModel(
        templateWidgetDao = dao,
        serverManager = serverManager,
        widgetId = widgetId,
    )

    private fun createWidgetEntity(template: String = "{{ states('sensor.temp') }}") = TemplateWidgetEntity(
        id = widgetId,
        serverId = serverId,
        template = template,
        textSize = 21F,
        lastUpdate = "on",
        backgroundType = WidgetBackgroundType.TRANSPARENT,
        textColor = BLACK_HEX,
    )

    companion object {
        /** Hex of `colorWidgetButtonLabelBlack`, which is what the widget persists. */
        private const val BLACK_HEX = "#3A3A3A"
    }
}
