package io.homeassistant.companion.android.settings.shortcuts.v2

import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.DynamicEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinnedEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServerData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutTargetValue
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens.ShortcutEditAction
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
class ShortcutEditViewModelTest {

    private val shortcutsRepository: ShortcutsRepository = mockk()

    private val server = Server(
        id = 1,
        _name = "Home",
        connection = ServerConnectionInfo(externalUrl = "https://example.com"),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )

    private val pinnedDraft = ShortcutDraft(
        id = "pinned_1",
        serverId = server.id,
        selectedIcon = null,
        label = "Pinned",
        description = "Pinned shortcut",
        target = ShortcutTargetValue.Lovelace("/lovelace/pinned"),
    )

    @BeforeEach
    fun setup() {
        coEvery { shortcutsRepository.loadEditorData() } returns ShortcutResult.Success(
            ShortcutEditorData(
                servers = listOf(server),
                serverDataById = mapOf(server.id to ServerData()),
            ),
        )
        coEvery { shortcutsRepository.loadDynamicEditor(0) } returns ShortcutResult.Success(
            DynamicEditorData.Edit(
                index = 0,
                draftSeed = buildDraft(id = dynamicShortcutId(0), serverId = server.id),
            ),
        )
        coEvery { shortcutsRepository.loadPinnedEditor(pinnedDraft.id) } returns ShortcutResult.Success(
            PinnedEditorData.Edit(draftSeed = pinnedDraft),
        )
        coEvery { shortcutsRepository.loadPinnedEditorForCreate() } returns ShortcutResult.Success(
            PinnedEditorData.Create(draftSeed = pinnedDraft.copy(id = "")),
        )
        coEvery { shortcutsRepository.upsertPinnedShortcut(any()) } returns ShortcutResult.Success(
            PinResult.Requested,
        )
        coEvery {
            shortcutsRepository.upsertDynamicShortcut(any(), any(), any())
        } returns ShortcutResult.Success(
            DynamicEditorData.Edit(
                index = 0,
                draftSeed = buildDraft(id = dynamicShortcutId(0), serverId = server.id),
            ),
        )
        coEvery { shortcutsRepository.deleteDynamicShortcut(any()) } returns ShortcutResult.Success(Unit)
        coEvery { shortcutsRepository.deletePinnedShortcut(any()) } returns ShortcutResult.Success(Unit)
    }

    @Test
    fun `Given dynamic shortcuts when viewModel initializes then selected shortcut is marked created`() = runTest {
        val viewModel = ShortcutEditViewModel(shortcutsRepository)
        turbineScope {
            val uiState = viewModel.uiState.testIn(backgroundScope)
            advanceUntilIdle()

            viewModel.openDynamic(0)
            advanceUntilIdle()

            val state = uiState.expectMostRecentItem()
            val editor = state.editor as ShortcutEditorUiState.EditorState.DynamicEdit
            assertFalse(state.screen.isLoading)
            assertEquals(0, editor.index)
        }
    }

    @Test
    fun `Given custom draft when submit dispatched then repository upsert uses draft`() = runTest {
        val viewModel = ShortcutEditViewModel(shortcutsRepository)
        advanceUntilIdle()
        viewModel.openDynamic(0)
        advanceUntilIdle()

        val draft = buildDraft(
            id = dynamicShortcutId(0),
            serverId = server.id,
        ).copy(
            label = "Updated",
            description = "Updated description",
            target = ShortcutTargetValue.Entity("light.kitchen"),
        )

        viewModel.dispatch(ShortcutEditAction.Submit(draft))
        advanceUntilIdle()

        coVerify {
            shortcutsRepository.upsertDynamicShortcut(
                0,
                match {
                    it.label == "Updated" &&
                        it.description == "Updated description" &&
                        it.target == ShortcutTargetValue.Entity("light.kitchen")
                },
                true,
            )
        }
    }

    @Test
    fun `Given draft when createCurrent called then repository upsert is invoked`() = runTest {
        val viewModel = ShortcutEditViewModel(shortcutsRepository)
        advanceUntilIdle()
        viewModel.openDynamic(0)
        advanceUntilIdle()

        viewModel.dispatch(
            ShortcutEditAction.Submit(
                buildDraft(
                    id = dynamicShortcutId(0),
                    serverId = server.id,
                ),
            ),
        )
        advanceUntilIdle()

        coVerify {
            shortcutsRepository.upsertDynamicShortcut(
                0,
                match { it.id == dynamicShortcutId(0) },
                true,
            )
        }
    }

    @Test
    fun `Given pinned shortcut when editPinned called then draft updates`() = runTest {
        val viewModel = ShortcutEditViewModel(shortcutsRepository)
        turbineScope {
            val uiState = viewModel.uiState.testIn(backgroundScope)
            advanceUntilIdle()

            viewModel.editPinned(pinnedDraft.id)
            advanceUntilIdle()

            val state = uiState.expectMostRecentItem()
            val editor = state.editor as ShortcutEditorUiState.EditorState.PinnedEdit
            assertEquals(pinnedDraft.id, editor.draftSeed.id)
        }
    }

    @Test
    fun `Given draft when createCurrent called then pinned action and close events are emitted`() = runTest {
        val viewModel = ShortcutEditViewModel(shortcutsRepository)
        turbineScope {
            val pinEvents = viewModel.pinResultEvents.testIn(backgroundScope)
            val closeEvents = viewModel.closeEvents.testIn(backgroundScope)
            advanceUntilIdle()

            viewModel.openCreatePinned()
            advanceUntilIdle()

            viewModel.dispatch(ShortcutEditAction.Submit(pinnedDraft.copy(id = "")))
            advanceUntilIdle()

            assertEquals(PinResult.Requested, pinEvents.awaitItem())
            closeEvents.awaitItem()
        }

        coVerify { shortcutsRepository.upsertPinnedShortcut(match { it.id.isBlank() }) }
    }

    private fun dynamicShortcutId(index: Int): String {
        return "shortcut_${index + 1}"
    }

    private fun buildDraft(id: String, serverId: Int): ShortcutDraft {
        return ShortcutDraft(
            id = id,
            serverId = serverId,
            selectedIcon = null,
            label = id,
            description = "Description for $id",
            target = ShortcutTargetValue.Lovelace("/lovelace/$id"),
        )
    }
}
