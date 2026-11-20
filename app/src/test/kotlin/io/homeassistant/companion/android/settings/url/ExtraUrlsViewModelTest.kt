package io.homeassistant.companion.android.settings.url

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class ExtraUrlsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherJUnit5Extension(testDispatcher)

    private val serverId = 1
    private val serverManager: ServerManager = mockk()

    /** The extra URLs as stored in the "database" of the fake server manager. */
    private var storedUrls: List<String> = emptyList()

    private fun server() = Server(
        id = serverId,
        _name = "Test Server",
        nameOverride = null,
        _version = "2024.1.0",
        deviceRegistryId = null,
        listOrder = 0,
        deviceName = null,
        connection = ServerConnectionInfo(
            externalUrl = "https://external.example.com",
            internalUrl = "https://internal.example.com:8123",
            extraUrls = storedUrls,
        ),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )

    private fun createViewModel(initialUrls: List<String> = emptyList()): ExtraUrlsViewModel {
        storedUrls = initialUrls
        // yield() makes reading and writing asynchronous like the real database does.
        coEvery { serverManager.getServer(serverId) } coAnswers {
            yield()
            server()
        }
        coEvery { serverManager.updateExtraUrls(serverId, any()) } coAnswers {
            yield()
            storedUrls = secondArg()
        }
        return ExtraUrlsViewModel(
            state = SavedStateHandle(mapOf(ExtraUrlsFragment.EXTRA_SERVER to serverId)),
            serverManager = serverManager,
            application = mockk<Application>(relaxed = true),
        )
    }

    @Test
    fun `Given a server with extra URLs when the screen opens then they are shown`() = runTest(testDispatcher) {
        val viewModel = createViewModel(listOf("https://a.example.com/", "https://b.example.com/"))
        advanceUntilIdle()

        assertEquals(listOf("https://a.example.com/", "https://b.example.com/"), viewModel.uiState.value.urls)
    }

    @Test
    fun `Given a valid URL when adding then it is normalized, saved on its own and the field can be cleared`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val added = viewModel.addUrl("https://vpn.example.com:8123/lovelace")

        assertTrue(added)
        assertEquals(listOf("https://vpn.example.com:8123/"), viewModel.uiState.value.urls)
        coVerify { serverManager.updateExtraUrls(serverId, listOf("https://vpn.example.com:8123/")) }
        // Only the extra URLs are written, the rest of the server (for example the session) is not.
        coVerify(exactly = 0) { serverManager.updateServer(any()) }
    }

    @Test
    fun `Given the internal URL typed without a trailing slash when adding it then it is rejected as duplicate`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val added = viewModel.addUrl("https://internal.example.com:8123/")

        assertFalse(added)
        assertEquals(ExtraUrlError.Duplicate, viewModel.uiState.value.error)
        coVerify(exactly = 0) { serverManager.updateExtraUrls(any(), any()) }
    }

    @Test
    fun `Given an error when the input changes then the error is cleared`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addUrl("not a url")
        assertEquals(ExtraUrlError.Invalid, viewModel.uiState.value.error)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `Given quick successive edits when they are applied then none of them is lost`() = runTest(testDispatcher) {
        val viewModel = createViewModel(listOf("https://a.example.com/", "https://b.example.com/", "https://c.example.com/"))
        advanceUntilIdle()

        viewModel.removeUrl("https://a.example.com/")
        viewModel.removeUrl("https://b.example.com/")
        advanceUntilIdle()

        assertEquals(listOf("https://c.example.com/"), storedUrls)
        assertEquals(listOf("https://c.example.com/"), viewModel.uiState.value.urls)
    }

    @Test
    fun `Given a URL that is already gone when removing it then nothing is written`() = runTest(testDispatcher) {
        val viewModel = createViewModel(listOf("https://a.example.com/"))
        advanceUntilIdle()

        viewModel.removeUrl("https://gone.example.com/")
        advanceUntilIdle()

        coVerify(exactly = 0) { serverManager.updateExtraUrls(any(), any()) }
        assertEquals(listOf("https://a.example.com/"), storedUrls)
    }

    @Test
    fun `Given URLs when moving one down then the order changes`() = runTest(testDispatcher) {
        val viewModel = createViewModel(listOf("https://a.example.com/", "https://b.example.com/"))
        advanceUntilIdle()

        viewModel.moveUrl("https://a.example.com/", 1)
        advanceUntilIdle()

        assertEquals(listOf("https://b.example.com/", "https://a.example.com/"), storedUrls)
    }
}
