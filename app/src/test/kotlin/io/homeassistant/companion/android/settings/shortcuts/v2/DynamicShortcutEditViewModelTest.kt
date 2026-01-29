package io.homeassistant.companion.android.settings.shortcuts.v2

import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.common.data.shortcuts.ShortcutsRepository
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
class DynamicShortcutEditViewModelTest {

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
        every { shortcutsRepository.canPinShortcuts } returns true
        every { shortcutsRepository.maxDynamicShortcuts } returns 5
        coEvery { shortcutsRepository.currentServerId() } returns server.id
        coEvery { shortcutsRepository.getServers() } returns listOf(server)
        coEvery { shortcutsRepository.loadServerData(server.id) } returns ServerData()
        coEvery { shortcutsRepository.loadDynamicShortcuts() } returns mapOf(
            0 to buildDraft(id = dynamicShortcutId(0), serverId = server.id),
        )
        coJustRun { shortcutsRepository.upsertDynamicShortcut(any(), any()) }
        coJustRun { shortcutsRepository.deleteDynamicShortcut(any()) }
    }

    @Test
    fun `Given dynamic shortcuts when viewModel initializes then selected shortcut is marked created`() = runTest {
        val viewModel = DynamicShortcutEditViewModel(shortcutsRepository)
        turbineScope {
            val uiState = viewModel.uiState.testIn(backgroundScope)
            advanceUntilIdle()

            val state = uiState.expectMostRecentItem()
            assertFalse(state.screen.isLoading)
            assertTrue(state.isCreated)
        }
    }

    @Test
    fun `Given custom draft when submit dispatched then repository upsert uses draft`() = runTest {
        val viewModel = DynamicShortcutEditViewModel(shortcutsRepository)
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
            )
        }
    }

    @Test
    fun `Given draft when createCurrent called then repository upsert is invoked`() = runTest {
        val viewModel = DynamicShortcutEditViewModel(shortcutsRepository)
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
            )
        }
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
