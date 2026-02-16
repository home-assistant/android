package io.homeassistant.companion.android.widgets.template

import androidx.compose.runtime.snapshots.Snapshot
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
class TemplateWidgetConfigureViewModelTest {

    private val templateWidgetDao: TemplateWidgetDao = mockk(relaxed = true)
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val integrationRepository: IntegrationRepository = mockk(relaxed = true)

    private lateinit var viewModel: TemplateWidgetConfigureViewModel

    private val supportedTextColors = listOf("#000000", "#FFFFFF")

    @BeforeEach
    fun setup() {
        coEvery { serverManager.isRegistered() } returns true
        val server: Server = mockk(relaxed = true)
        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        viewModel = TemplateWidgetConfigureViewModel(
            templateWidgetDao = templateWidgetDao,
            serverManager = serverManager,
        )
        viewModel.ioDispatcher = UnconfinedTestDispatcher()
    }

    @Nested
    inner class SetupTest {
        @Test
        fun `Given no existing widget when onSetup then isUpdateWidget is false`() = runTest {
            coEvery { templateWidgetDao.get(any()) } returns null

            viewModel.onSetup(widgetId = 42, supportedTextColors = supportedTextColors)
            advanceUntilIdle()

            assertFalse(viewModel.isUpdateWidget)
        }

        @Test
        fun `Given existing widget when onSetup then loads widget state`() = runTest {
            val existingWidget = TemplateWidgetEntity(
                id = 42,
                serverId = 1,
                template = "{{ states('sensor.temp') }}",
                textSize = 16f,
                lastUpdate = "2024-01-01",
                backgroundType = WidgetBackgroundType.TRANSPARENT,
                textColor = "#FFFFFF",
            )
            coEvery { templateWidgetDao.get(42) } returns existingWidget

            viewModel.onSetup(widgetId = 42, supportedTextColors = supportedTextColors)
            advanceUntilIdle()

            assertTrue(viewModel.isUpdateWidget)
            assertEquals(1, viewModel.selectedServerId)
            assertEquals("{{ states('sensor.temp') }}", viewModel.templateText)
            assertEquals("16", viewModel.textSize)
            assertEquals(WidgetBackgroundType.TRANSPARENT, viewModel.selectedBackgroundType)
            assertEquals(1, viewModel.textColorIndex)
        }

        @Test
        fun `Given existing widget with unknown text color when onSetup then defaults to index 0`() = runTest {
            val existingWidget = TemplateWidgetEntity(
                id = 42,
                serverId = 1,
                template = "test",
                textSize = 12f,
                lastUpdate = "2024-01-01",
                backgroundType = WidgetBackgroundType.TRANSPARENT,
                textColor = "#FF0000",
            )
            coEvery { templateWidgetDao.get(42) } returns existingWidget

            viewModel.onSetup(widgetId = 42, supportedTextColors = supportedTextColors)
            advanceUntilIdle()

            assertEquals(0, viewModel.textColorIndex)
        }

        @Test
        fun `Given onSetup called twice then second call is ignored`() = runTest {
            coEvery { templateWidgetDao.get(any()) } returns null

            viewModel.onSetup(widgetId = 42, supportedTextColors = supportedTextColors)
            advanceUntilIdle()

            viewModel.templateText = "modified"
            viewModel.onSetup(widgetId = 99, supportedTextColors = supportedTextColors)
            advanceUntilIdle()

            assertEquals("modified", viewModel.templateText)
        }
    }

    @Nested
    inner class ServerSelectionTest {
        @Test
        fun `Given different server when setServer then selectedServerId is updated`() {
            viewModel.setServer(serverId = 5)

            assertEquals(5, viewModel.selectedServerId)
        }

        @Test
        fun `Given same server when setServer then no change`() {
            viewModel.setServer(serverId = 5)
            viewModel.setServer(serverId = 5)

            assertEquals(5, viewModel.selectedServerId)
        }
    }

    @Nested
    inner class TemplateRenderingTest {
        @Test
        fun `Given valid template when text changes then template is rendered`() = runTest {
            coEvery {
                integrationRepository.renderTemplate(any(), any())
            } returns "25.0"

            viewModel.onSetup(widgetId = 42, supportedTextColors = supportedTextColors)
            advanceUntilIdle()
            viewModel.templateText = "{{ states('sensor.temp') }}"
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertEquals("25.0", viewModel.renderedTemplate)
            assertTrue(viewModel.isTemplateValid)
        }

        @Test
        fun `Given empty template when text changes then template is not valid`() = runTest {
            viewModel.onSetup(widgetId = 42, supportedTextColors = supportedTextColors)
            advanceUntilIdle()
            viewModel.templateText = ""
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertNull(viewModel.renderedTemplate)
            assertFalse(viewModel.isTemplateValid)
        }

        @Test
        fun `Given render error when rendering template then template is not valid`() = runTest {
            coEvery {
                integrationRepository.renderTemplate(any(), any())
            } throws RuntimeException("Render failed")

            viewModel.onSetup(widgetId = 42, supportedTextColors = supportedTextColors)
            advanceUntilIdle()
            viewModel.templateText = "{{ invalid }}"
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertNull(viewModel.renderedTemplate)
            assertFalse(viewModel.isTemplateValid)
        }

        @Test
        fun `Given server not registered when rendering template then template state is unchanged`() = runTest {
            coEvery { serverManager.isRegistered() } returns false

            viewModel.onSetup(widgetId = 42, supportedTextColors = supportedTextColors)
            advanceUntilIdle()
            viewModel.templateText = "{{ states('sensor.temp') }}"
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertNull(viewModel.renderedTemplate)
            assertFalse(viewModel.isTemplateValid)
        }
    }

    @Nested
    inner class UpdateWidgetTest {
        @Test
        fun `Given valid widget ID when updateWidgetConfiguration then entity is saved to DAO`() = runTest {
            coEvery { templateWidgetDao.get(any()) } returns null

            viewModel.onSetup(widgetId = 42, supportedTextColors = supportedTextColors)
            advanceUntilIdle()
            viewModel.templateText = "test template"
            viewModel.textSize = "16"
            viewModel.selectedBackgroundType = WidgetBackgroundType.DAYNIGHT

            viewModel.updateWidgetConfiguration()

            coVerify {
                templateWidgetDao.add(
                    match {
                        it.id == 42 &&
                            it.template == "test template" &&
                            it.textSize == 16f &&
                            it.backgroundType == WidgetBackgroundType.DAYNIGHT &&
                            it.textColor == null
                    },
                )
            }
        }

        @Test
        fun `Given transparent background when updateWidgetConfiguration then text color is set`() = runTest {
            coEvery { templateWidgetDao.get(any()) } returns null

            viewModel.onSetup(widgetId = 42, supportedTextColors = supportedTextColors)
            advanceUntilIdle()
            viewModel.templateText = "test"
            viewModel.selectedBackgroundType = WidgetBackgroundType.TRANSPARENT
            viewModel.textColorIndex = 1

            viewModel.updateWidgetConfiguration()

            coVerify {
                templateWidgetDao.add(
                    match {
                        it.textColor == "#FFFFFF" &&
                            it.backgroundType == WidgetBackgroundType.TRANSPARENT
                    },
                )
            }
        }

        @Test
        fun `Given invalid widget ID when updateWidgetConfiguration then throws IllegalStateException`() = runTest {
            var thrown = false
            try {
                viewModel.updateWidgetConfiguration()
            } catch (_: IllegalStateException) {
                thrown = true
            }
            assertTrue(thrown)
        }

        @Test
        fun `Given invalid text size when updateWidgetConfiguration then uses default text size`() = runTest {
            coEvery { templateWidgetDao.get(any()) } returns null

            viewModel.onSetup(widgetId = 42, supportedTextColors = supportedTextColors)
            advanceUntilIdle()
            viewModel.templateText = "test"
            viewModel.textSize = "invalid"

            viewModel.updateWidgetConfiguration()

            coVerify {
                templateWidgetDao.add(match { it.textSize == 12.0f })
            }
        }
    }
}
