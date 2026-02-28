package io.homeassistant.companion.android.settings.shortcuts.v2

import app.cash.turbine.testIn
import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.AppEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.HomeEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServerData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutError
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutTargetValue
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.settings.shortcuts.v2.views.screens.ShortcutEditAction
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
class EditShortcutViewModelTest {

    private val shortcutsRepository: ShortcutsRepository = mockk()

    private val server = Server(
        id = 1,
        _name = "Home",
        connection = ServerConnectionInfo(externalUrl = "https://example.com"),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )

    private val homeShortcutDraft = ShortcutDraft(
        id = "home_1",
        serverId = server.id,
        selectedIconName = null,
        label = "Home",
        description = "Home shortcut",
        target = ShortcutTargetValue.Lovelace("/lovelace/home"),
    )

    @BeforeEach
    fun setup() {
        coEvery { shortcutsRepository.loadEditorData() } returns ShortcutResult.Success(
            ShortcutEditorData(
                servers = listOf(server),
                serverDataById = mapOf(server.id to ServerData()),
            ),
        )
        coEvery { shortcutsRepository.loadAppEditor(0) } returns ShortcutResult.Success(
            AppEditorData.Edit(
                index = 0,
                draftSeed = buildDraft(id = appShortcutId(0), serverId = server.id),
            ),
        )
        coEvery { shortcutsRepository.loadHomeEditor(homeShortcutDraft.id) } returns ShortcutResult.Success(
            HomeEditorData.Edit(draftSeed = homeShortcutDraft),
        )
        coEvery { shortcutsRepository.upsertHomeShortcut(any()) } returns ShortcutResult.Success(
            PinResult.Requested,
        )
        coEvery {
            shortcutsRepository.upsertAppShortcut(any(), any(), any())
        } returns ShortcutResult.Success(
            AppEditorData.Edit(
                index = 0,
                draftSeed = buildDraft(id = appShortcutId(0), serverId = server.id),
            ),
        )
        coEvery { shortcutsRepository.deleteAppShortcut(any()) } returns ShortcutResult.Success(Unit)
        coEvery { shortcutsRepository.deleteHomeShortcut(any()) } returns ShortcutResult.Success(Unit)
    }

    @Test
    fun `Given no servers when init then screen error is set`() = runTest {
        coEvery { shortcutsRepository.loadEditorData() } returns ShortcutResult.Error(
            ShortcutError.NoServers,
        )

        val viewModel = createVm()

        assertFalse(viewModel.uiState.value.screen.isLoading)
        assertEquals(ShortcutError.NoServers, viewModel.uiState.value.screen.error)
    }

    @Nested
    inner class NavigationTest {

        @Test
        fun `Given app shortcut exists when openEditAppShortcut then editor is AppEdit with correct index`() = runTest {
            val viewModel = createVm()

            viewModel.openEditAppShortcut(0)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val editor = state.editor as ShortcutEditorUiState.EditorState.AppEdit
            assertFalse(state.screen.isLoading)
            assertEquals(0, editor.index)
        }

        @Test
        fun `Given home shortcut exists when openEditHomeShortcut then editor is HomeEdit with correct id`() = runTest {
            val viewModel = createVm()

            viewModel.openEditHomeShortcut(homeShortcutDraft.id)
            advanceUntilIdle()

            val editor = viewModel.uiState.value.editor as ShortcutEditorUiState.EditorState.HomeEdit
            assertEquals(homeShortcutDraft.id, editor.draftSeed.id)
        }

        @Test
        fun `Given app shortcut slots full when openEditAppShortcut then screen error set and editor unchanged`() = runTest {
            coEvery { shortcutsRepository.loadAppEditor(0) } returns ShortcutResult.Error(
                ShortcutError.SlotsFull,
            )

            val viewModel = createVm()
            viewModel.openEditHomeShortcut(homeShortcutDraft.id)
            advanceUntilIdle()
            val editorBeforeError = viewModel.uiState.value.editor

            viewModel.openEditAppShortcut(0)
            advanceUntilIdle()

            assertEquals(ShortcutError.SlotsFull, viewModel.uiState.value.screen.error)
            assertEquals(editorBeforeError, viewModel.uiState.value.editor)
        }

        @Test
        fun `Given error when openEditHomeShortcut then error cleared`() = runTest {
            coEvery { shortcutsRepository.loadAppEditor(0) } returns ShortcutResult.Error(
                ShortcutError.SlotsFull,
            )

            val viewModel = createVm()
            viewModel.openEditAppShortcut(0)
            advanceUntilIdle()
            assertEquals(ShortcutError.SlotsFull, viewModel.uiState.value.screen.error)

            viewModel.openEditHomeShortcut(homeShortcutDraft.id)
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.screen.error)
        }
    }

    @Nested
    inner class SubmitActionTest {

        @ParameterizedTest
        @CsvSource("true", "false")
        fun `Given app shortcut when submit then upsert uses correct draft and isEditing flag`(expectedIsEditing: Boolean) = runTest {
            val index = if (expectedIsEditing) 0 else 1
            val draft = buildDraft(
                id = appShortcutId(index),
                serverId = server.id,
            ).copy(
                label = "Updated",
                description = "Updated description",
                target = ShortcutTargetValue.Entity("light.kitchen"),
            )

            if (!expectedIsEditing) {
                coEvery { shortcutsRepository.loadAppEditorFirstAvailable() } returns ShortcutResult.Success(
                    AppEditorData.Create(
                        index = index,
                        draftSeed = draft,
                    ),
                )
            }

            val viewModel = createVm()

            if (expectedIsEditing) {
                viewModel.openEditAppShortcut(index)
                advanceUntilIdle()
            } else {
                viewModel.createAppShortcutFirstAvailable()
                advanceUntilIdle()
            }

            viewModel.dispatch(ShortcutEditAction.Submit(draft))
            advanceUntilIdle()

            coVerify {
                shortcutsRepository.upsertAppShortcut(index, draft, expectedIsEditing)
            }
        }

        @ParameterizedTest
        @EnumSource(PinResult::class, names = ["Requested", "Updated"])
        fun `Given home shortcut when submit then pin result emitted and close event emitted`(pinResult: PinResult) = runTest {
            coEvery { shortcutsRepository.upsertHomeShortcut(any()) } returns ShortcutResult.Success(pinResult)

            val viewModel = createVm()
            viewModel.openEditHomeShortcut(homeShortcutDraft.id)
            advanceUntilIdle()

            assertPinAndClose(viewModel, pinResult) {
                viewModel.dispatch(ShortcutEditAction.Submit(homeShortcutDraft))
            }

            coVerify { shortcutsRepository.upsertHomeShortcut(match { it.id == homeShortcutDraft.id }) }
        }

        @Test
        fun `Given app shortcut edit when submit then close event emitted`() = runTest {
            val viewModel = createVm()
            viewModel.openEditAppShortcut(0)
            advanceUntilIdle()

            assertCloseEmitted(viewModel) {
                viewModel.dispatch(ShortcutEditAction.Submit(buildDraft(id = appShortcutId(0), serverId = server.id)))
            }
        }

        @Test
        fun `Given app shortcut create when submit then close event emitted`() = runTest {
            val createIndex = 1
            val draft = buildDraft(id = appShortcutId(createIndex), serverId = server.id)
            coEvery { shortcutsRepository.loadAppEditorFirstAvailable() } returns ShortcutResult.Success(
                AppEditorData.Create(index = createIndex, draftSeed = draft),
            )

            val viewModel = createVm()
            viewModel.createAppShortcutFirstAvailable()
            advanceUntilIdle()

            assertCloseEmitted(viewModel) {
                viewModel.dispatch(ShortcutEditAction.Submit(draft))
            }
        }

        @Test
        fun `Given app shortcut submit error when submit then screen error set`() = runTest {
            coEvery {
                shortcutsRepository.upsertAppShortcut(any(), any(), any())
            } returns ShortcutResult.Error(
                ShortcutError.SlotsFull,
            )

            val viewModel = createVm()
            viewModel.openEditAppShortcut(0)
            advanceUntilIdle()

            viewModel.dispatch(
                ShortcutEditAction.Submit(
                    buildDraft(
                        id = appShortcutId(0),
                        serverId = server.id,
                    ),
                ),
            )
            advanceUntilIdle()

            assertEquals(ShortcutError.SlotsFull, viewModel.uiState.value.screen.error)
        }
    }

    @Nested
    inner class DeleteActionTest {

        @Test
        fun `Given app shortcut edit when delete then close event emitted`() = runTest {
            val viewModel = createVm()
            viewModel.openEditAppShortcut(0)
            advanceUntilIdle()

            assertCloseEmitted(viewModel) {
                viewModel.dispatch(ShortcutEditAction.Delete)
            }

            coVerify { shortcutsRepository.deleteAppShortcut(0) }
        }

        @Test
        fun `Given home shortcut edit when delete then close event emitted`() = runTest {
            val viewModel = createVm()
            viewModel.openEditHomeShortcut(homeShortcutDraft.id)
            advanceUntilIdle()

            assertCloseEmitted(viewModel) {
                viewModel.dispatch(ShortcutEditAction.Delete)
            }

            coVerify { shortcutsRepository.deleteHomeShortcut(homeShortcutDraft.id) }
        }

        @Test
        fun `Given home shortcut delete error when delete then screen error set and no close event`() = runTest {
            coEvery { shortcutsRepository.deleteHomeShortcut(any()) } returns ShortcutResult.Error(
                ShortcutError.Unknown,
            )

            val viewModel = createVm()
            viewModel.openEditHomeShortcut(homeShortcutDraft.id)
            advanceUntilIdle()

            turbineScope {
                val closeEvents = viewModel.closeEvents.testIn(backgroundScope)

                viewModel.dispatch(ShortcutEditAction.Delete)
                advanceUntilIdle()

                assertEquals(ShortcutError.Unknown, viewModel.uiState.value.screen.error)
                closeEvents.expectNoEvents()
            }
        }
    }

    private fun TestScope.createVm(): EditShortcutViewModel {
        val vm = EditShortcutViewModel(shortcutsRepository)
        advanceUntilIdle()
        return vm
    }

    private suspend fun TestScope.assertCloseEmitted(viewModel: EditShortcutViewModel, action: suspend () -> Unit) {
        turbineScope {
            val closeEvents = viewModel.closeEvents.testIn(backgroundScope)
            action()
            advanceUntilIdle()
            closeEvents.awaitItem()
        }
    }

    private suspend fun TestScope.assertPinAndClose(
        viewModel: EditShortcutViewModel,
        expectedPin: PinResult,
        action: suspend () -> Unit,
    ) {
        turbineScope {
            val pinEvents = viewModel.pinResultEvents.testIn(backgroundScope)
            val closeEvents = viewModel.closeEvents.testIn(backgroundScope)
            action()
            advanceUntilIdle()
            assertEquals(expectedPin, pinEvents.awaitItem())
            closeEvents.awaitItem()
        }
    }

    private fun appShortcutId(index: Int): String {
        return "shortcut_${index + 1}"
    }

    private fun buildDraft(id: String, serverId: Int): ShortcutDraft {
        return ShortcutDraft(
            id = id,
            serverId = serverId,
            selectedIconName = null,
            label = id,
            description = "Description for $id",
            target = ShortcutTargetValue.Lovelace("/lovelace/$id"),
        )
    }
}
