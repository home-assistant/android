package io.homeassistant.companion.android.common.util

import android.Manifest
import android.content.Context
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val API_PRE_LOCAL_NETWORK_PERMISSION = 36 // BAKLAVA
private const val API_WITH_LOCAL_NETWORK_PERMISSION = 37 // CINNAMON_BUN

class CheckLocalNetworkPermissionUseCaseTest {

    private lateinit var context: Context
    private lateinit var serverManager: ServerManager
    private lateinit var useCase: CheckLocalNetworkPermissionUseCase

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        serverManager = mockk(relaxed = true)
        mockkObject(LocalNetworkPermissionWarning)
        every { LocalNetworkPermissionWarning.show(any(), any()) } just runs
        every { LocalNetworkPermissionWarning.cancel(any()) } just runs
        useCase = newUseCase(permissionGranted = false)
        stubSystemSdkVersion(currentSdk = API_WITH_LOCAL_NETWORK_PERMISSION)
    }

    @AfterEach
    fun tearDown() {
        SdkVersion.sdkInt = 0
        unmockkObject(LocalNetworkPermissionWarning)
    }

    @Test
    fun `Given pre-API-37 device when invoked then returns true and notification handler untouched`() = runTest {
        stubSystemSdkVersion(currentSdk = API_PRE_LOCAL_NETWORK_PERMISSION)

        val result = useCase()

        assertTrue(result)
        verify(exactly = 0) { LocalNetworkPermissionWarning.show(any(), any()) }
        verify(exactly = 0) { LocalNetworkPermissionWarning.cancel(any()) }
    }

    @Test
    fun `Given permission granted when invoked then returns true and cancels notification`() = runTest {
        useCase = newUseCase(permissionGranted = true)

        val result = useCase()

        assertTrue(result)
        verify(exactly = 1) { LocalNetworkPermissionWarning.cancel(context) }
        verify(exactly = 0) { LocalNetworkPermissionWarning.show(any(), any()) }
    }

    @Test
    fun `Given permission missing and no servers when invoked then returns true and cancels notification`() = runTest {
        coEvery { serverManager.servers() } returns emptyList()

        val result = useCase()

        assertTrue(result)
        verify(exactly = 1) { LocalNetworkPermissionWarning.cancel(context) }
        verify(exactly = 0) { LocalNetworkPermissionWarning.show(any(), any()) }
    }

    @Test
    fun `Given permission missing and only remote servers when invoked then returns true and cancels notification`() = runTest {
        coEvery { serverManager.servers() } returns listOf(
            server(id = 1, externalUrl = "https://8.8.8.8:8123", internalUrl = null),
        )

        val result = useCase()

        assertTrue(result)
        verify(exactly = 1) { LocalNetworkPermissionWarning.cancel(context) }
        verify(exactly = 0) { LocalNetworkPermissionWarning.show(any(), any()) }
    }

    @Test
    fun `Given permission missing and a server with local internal URL when invoked then returns false and shows notification`() = runTest {
        val localServer = server(
            id = 1,
            externalUrl = "https://8.8.8.8:8123",
            internalUrl = "http://192.168.1.10:8123",
        )
        coEvery { serverManager.servers() } returns listOf(localServer)

        val result = useCase()

        assertFalse(result)
        verify(exactly = 1) { LocalNetworkPermissionWarning.show(context, listOf(localServer)) }
        verify(exactly = 0) { LocalNetworkPermissionWarning.cancel(any()) }
    }

    @Test
    fun `Given permission missing and a server with local external URL when invoked then returns false and shows notification`() = runTest {
        val localServer = server(id = 1, externalUrl = "http://10.0.0.5:8123", internalUrl = null)
        coEvery { serverManager.servers() } returns listOf(localServer)

        val result = useCase()

        assertFalse(result)
        verify(exactly = 1) { LocalNetworkPermissionWarning.show(context, listOf(localServer)) }
    }

    @Test
    fun `Given permission flips from missing to granted between calls when invoked twice then second call cancels notification`() = runTest {
        val localServer = server(
            id = 1,
            externalUrl = "https://8.8.8.8:8123",
            internalUrl = "http://192.168.1.10:8123",
        )
        coEvery { serverManager.servers() } returns listOf(localServer)

        var granted = false
        useCase = CheckLocalNetworkPermissionUseCase(
            context = context,
            permissionChecker = PermissionChecker { granted },
            serverManager = serverManager,
        )

        val first = useCase()
        granted = true
        val second = useCase()

        assertFalse(first)
        assertTrue(second)
        coVerify(exactly = 1) { LocalNetworkPermissionWarning.show(context, listOf(localServer)) }
        coVerify(exactly = 1) { LocalNetworkPermissionWarning.cancel(context) }
    }

    private fun newUseCase(permissionGranted: Boolean): CheckLocalNetworkPermissionUseCase = CheckLocalNetworkPermissionUseCase(
        context = context,
        permissionChecker = { permission ->
            permissionGranted && permission == Manifest.permission.ACCESS_LOCAL_NETWORK
        },
        serverManager = serverManager,
    )

    private fun stubSystemSdkVersion(currentSdk: Int) {
        SdkVersion.sdkInt = currentSdk
    }

    private fun server(id: Int, externalUrl: String, internalUrl: String?): Server = Server(
        id = id,
        _name = "Server $id",
        connection = ServerConnectionInfo(
            externalUrl = externalUrl,
            internalUrl = internalUrl,
        ),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )
}
