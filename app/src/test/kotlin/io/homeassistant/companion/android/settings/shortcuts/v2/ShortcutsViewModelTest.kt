package io.homeassistant.companion.android.settings.shortcuts.v2

import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.DynamicShortcutsData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServersData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutError
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutTargetValue
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutsListData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.toSummary
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
class ShortcutsViewModelTest {

    private val shortcutsRepository: ShortcutsRepository = mockk()

    private val server = Server(
        id = 1,
        _name = "Home",
        connection = ServerConnectionInfo(externalUrl = "https://example.com"),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )

    @BeforeEach
    fun setup() {
        coEvery { shortcutsRepository.getServers() } returns ShortcutResult.Success(
            ServersData(listOf(server), server.id),
        )
        coEvery { shortcutsRepository.loadShortcutsList() } returns ShortcutResult.Success(
            ShortcutsListData(
                dynamic = DynamicShortcutsData(
                    maxDynamicShortcuts = 5,
                    shortcuts = mapOf(
                        0 to buildDraft(id = "shortcut_1", serverId = server.id),
                        2 to buildDraft(id = "shortcut_3", serverId = server.id),
                    ),
                ),
                pinned = listOf(buildDraft(id = "pinned_1", serverId = server.id).toSummary()),
            ),
        )
    }

    @Test
    fun `Given shortcuts when viewModel initializes then content state is populated`() = runTest {
        val viewModel = ShortcutsViewModel(shortcutsRepository)
        turbineScope {
            val uiState = viewModel.uiState.testIn(backgroundScope)
            advanceUntilIdle()

            val state = uiState.expectMostRecentItem()
            Assertions.assertTrue(state is ShortcutsListUiState.Content)
            val content = state as ShortcutsListUiState.Content
            Assertions.assertTrue(content.canPinShortcuts)
            Assertions.assertTrue(content.canCreateDynamic)
            Assertions.assertEquals(listOf(0, 2), content.dynamicItems.map { it.index })
            Assertions.assertEquals(1, content.pinnedShortcuts.size)
        }
    }

    @Test
    fun `Given no shortcuts and no servers when viewModel initializes then empty state has no servers`() = runTest {
        coEvery { shortcutsRepository.getServers() } returns ShortcutResult.Error(
            ShortcutError.NoServers,
        )
        coEvery { shortcutsRepository.loadShortcutsList() } returns ShortcutResult.Success(
            ShortcutsListData(
                dynamic = DynamicShortcutsData(
                    maxDynamicShortcuts = 5,
                    shortcuts = emptyMap(),
                ),
                pinned = emptyList(),
                pinnedError = ShortcutError.PinnedNotSupported,
            ),
        )

        val viewModel = ShortcutsViewModel(shortcutsRepository)
        turbineScope {
            val uiState = viewModel.uiState.testIn(backgroundScope)
            advanceUntilIdle()

            val state = uiState.expectMostRecentItem()
            Assertions.assertTrue(state is ShortcutsListUiState.Empty)
            val empty = state as ShortcutsListUiState.Empty
            Assertions.assertFalse(empty.hasServers)
        }
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
