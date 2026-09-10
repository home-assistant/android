package io.homeassistant.companion.android.launch.link

import android.net.Uri
import app.cash.turbine.test
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.settings.server.ServerChooserItem
import io.homeassistant.companion.android.settings.server.ServerChooserItemsUseCase
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class LinkViewModelTest {

    private val linkHandler: LinkHandler = mockk()
    private val serverChooserItems: ServerChooserItemsUseCase = mockk()

    private fun createViewModel(): LinkViewModel = LinkViewModel(linkHandler = linkHandler, serverChooserItemsUseCase = serverChooserItems)

    private fun uri(value: String = "https://my.home-assistant.io/redirect/foo"): Uri = mockk(relaxed = true) {
        every { this@mockk.toString() } returns value
    }

    @Test
    fun `Given ServerPicker destination when onLinkReceived then uiState becomes ChoosingServer`() = runTest {
        val servers = listOf<Server>(mockk { every { id } returns 1 }, mockk { every { id } returns 2 })
        val items = listOf(
            ServerChooserItem(serverId = 1, userName = "Alice", serverName = "Home"),
            ServerChooserItem(serverId = 2, userName = "Bob", serverName = "Office"),
        )
        coEvery { linkHandler.handleLink(any()) } returns LinkDestination.ServerPicker(FrontendTarget.Path("/lovelace"), servers)
        every { serverChooserItems(servers) } returns flowOf(items)

        val viewModel = createViewModel()
        viewModel.uiState.test {
            assertEquals(LinkUiState.Loading, awaitItem())
            viewModel.onLinkReceived(uri())
            assertEquals(LinkUiState.ChoosingServer(items = items, target = FrontendTarget.Path("/lovelace")), awaitItem())
        }
    }

    @Test
    fun `Given Webview destination when onLinkReceived then NavigateToWebView event is emitted`() = runTest {
        coEvery { linkHandler.handleLink(any()) } returns LinkDestination.Webview(FrontendTarget.Path("/lovelace"), serverId = 3)

        val viewModel = createViewModel()
        viewModel.navigationEvents.test {
            viewModel.onLinkReceived(uri())
            assertEquals(LinkNavigationEvent.NavigateToWebView(target = FrontendTarget.Path("/lovelace"), serverId = 3), awaitItem())
        }
    }

    @Test
    fun `Given Onboarding destination when onLinkReceived then OpenInvitation event is emitted`() = runTest {
        coEvery { linkHandler.handleLink(any()) } returns LinkDestination.Onboarding("http://homeassistant.local:8123")

        val viewModel = createViewModel()
        viewModel.navigationEvents.test {
            viewModel.onLinkReceived(uri())
            assertEquals(LinkNavigationEvent.OpenInvitation("http://homeassistant.local:8123"), awaitItem())
        }
    }

    @Test
    fun `Given NoDestination when onLinkReceived then Finish event is emitted`() = runTest {
        coEvery { linkHandler.handleLink(any()) } returns LinkDestination.NoDestination

        val viewModel = createViewModel()
        viewModel.navigationEvents.test {
            viewModel.onLinkReceived(uri())
            assertEquals(LinkNavigationEvent.Finish, awaitItem())
        }
    }

    @Test
    fun `Given a null uri when onLinkReceived then FailFast triggers and a Finish event is emitted`() = runTest {
        var failFastTriggered = false
        FailFast.setHandler { _, _ -> failFastTriggered = true }

        val viewModel = createViewModel()
        viewModel.navigationEvents.test {
            viewModel.onLinkReceived(null)
            assertEquals(LinkNavigationEvent.Finish, awaitItem())
        }
        assertTrue(failFastTriggered, "FailFast should be triggered for a missing uri")
    }

    @Test
    fun `Given ChoosingServer state when onServerSelected then NavigateToWebView event carries the path and id`() = runTest {
        val servers = listOf<Server>(mockk { every { id } returns 1 }, mockk { every { id } returns 2 })
        coEvery { linkHandler.handleLink(any()) } returns LinkDestination.ServerPicker(FrontendTarget.Path("/lovelace"), servers)
        every { serverChooserItems(servers) } returns flowOf(
            listOf(
                ServerChooserItem(serverId = 1, userName = "Alice", serverName = "Home"),
                ServerChooserItem(serverId = 2, userName = "Bob", serverName = "Office"),
            ),
        )

        val viewModel = createViewModel()
        viewModel.onLinkReceived(uri())
        advanceUntilIdle()

        viewModel.navigationEvents.test {
            viewModel.onServerSelected(serverId = 2)
            assertEquals(LinkNavigationEvent.NavigateToWebView(target = FrontendTarget.Path("/lovelace"), serverId = 2), awaitItem())
        }
    }

    @Test
    fun `Given no ChoosingServer state when onServerSelected then no event is emitted`() = runTest {
        val viewModel = createViewModel()
        viewModel.navigationEvents.test {
            viewModel.onServerSelected(serverId = 2)
            expectNoEvents()
        }
    }

    @Test
    fun `When onServerChooserDismissed then Finish event is emitted`() = runTest {
        val viewModel = createViewModel()
        viewModel.navigationEvents.test {
            viewModel.onServerChooserDismissed()
            assertEquals(LinkNavigationEvent.Finish, awaitItem())
        }
    }
}
