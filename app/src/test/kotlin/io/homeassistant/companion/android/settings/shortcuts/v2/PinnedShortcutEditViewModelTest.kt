package io.homeassistant.companion.android.settings.shortcuts.v2

import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServerData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutTargetValue
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens.ShortcutEditAction
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
class PinnedShortcutEditViewModelTest {

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
        every { shortcutsRepository.canPinShortcuts } returns true
        coEvery { shortcutsRepository.currentServerId() } returns server.id
        coEvery { shortcutsRepository.getServers() } returns listOf(server)
        coEvery { shortcutsRepository.loadServerData(server.id) } returns ServerData()
        coEvery { shortcutsRepository.loadPinnedShortcuts() } returns listOf(pinnedDraft)
        coEvery { shortcutsRepository.upsertPinnedShortcut(any()) } returns PinResult.Requested
        coJustRun { shortcutsRepository.deletePinnedShortcut(any()) }
    }

    @Test
    fun `Given pinned shortcut when editPinned called then draft updates`() = runTest {
        val viewModel = PinnedShortcutEditViewModel(shortcutsRepository)
        turbineScope {
            val uiState = viewModel.uiState.testIn(backgroundScope)
            advanceUntilIdle()

            viewModel.editPinned(pinnedDraft.id)
            advanceUntilIdle()

            val state = uiState.expectMostRecentItem()
            assertEquals(pinnedDraft.id, state.draftSeed.id)
            assertEquals(listOf(pinnedDraft.id), state.pinnedIds)
        }
    }

    @Test
    fun `Given draft when createCurrent called then pin result is emitted`() = runTest {
        val viewModel = PinnedShortcutEditViewModel(shortcutsRepository)
        turbineScope {
            val pinEvents = viewModel.pinResultEvents.testIn(backgroundScope)
            advanceUntilIdle()

            viewModel.dispatch(ShortcutEditAction.Submit(pinnedDraft))
            advanceUntilIdle()

            assertEquals(PinResult.Requested, pinEvents.awaitItem())
        }

        coVerify { shortcutsRepository.upsertPinnedShortcut(match { it.id == pinnedDraft.id }) }
    }
}
