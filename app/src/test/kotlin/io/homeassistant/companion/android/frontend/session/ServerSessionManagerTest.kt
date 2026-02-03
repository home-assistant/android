package io.homeassistant.companion.android.frontend.session

import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.frontend.error.FrontendError
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ServerSessionManagerTest {

    private val serverManager: ServerManager = mockk()
    private val authRepository: AuthenticationRepository = mockk()
    private lateinit var manager: ServerSessionManager

    @BeforeEach
    fun setup() {
        coEvery { serverManager.authenticationRepository(any()) } returns authRepository
        manager = ServerSessionManager(serverManager)
    }

    @Test
    fun `Given connected session when isSessionConnected then returns Connected`() = runTest {
        coEvery { authRepository.getSessionState() } returns SessionState.CONNECTED

        val result = manager.isSessionConnected(serverId = 1)

        assertTrue(result is SessionCheckResult.Connected)
    }

    @Test
    fun `Given anonymous session when isSessionConnected then returns NotConnected`() = runTest {
        coEvery { authRepository.getSessionState() } returns SessionState.ANONYMOUS

        val result = manager.isSessionConnected(serverId = 1)

        assertTrue(result is SessionCheckResult.NotConnected)
    }

    @Test
    fun `Given exception when isSessionConnected then returns NotConnected`() = runTest {
        coEvery { authRepository.getSessionState() } throws RuntimeException("Connection failed")

        val result = manager.isSessionConnected(serverId = 1)

        assertTrue(result is SessionCheckResult.NotConnected)
    }

    @Test
    fun `Given valid auth when getExternalAuth then returns Success with callback script`() = runTest {
        val payload = AuthPayload(callback = "externalAuthCallback", force = false)
        val authJson = """{"access_token":"test","expires_in":3600}"""
        coEvery { authRepository.retrieveExternalAuthentication(false) } returns authJson

        val result = manager.getExternalAuth(serverId = 1, payload = payload)

        assertTrue(result is ExternalAuthResult.Success)
        assertEquals("externalAuthCallback(true, $authJson)", (result as ExternalAuthResult.Success).callbackScript)
    }

    @Test
    fun `Given forced auth refresh when getExternalAuth then passes force flag`() = runTest {
        val payload = AuthPayload(callback = "externalAuthCallback", force = true)
        val authJson = """{"access_token":"refreshed","expires_in":3600}"""
        coEvery { authRepository.retrieveExternalAuthentication(true) } returns authJson

        val result = manager.getExternalAuth(serverId = 1, payload = payload)

        assertTrue(result is ExternalAuthResult.Success)
        assertEquals("externalAuthCallback(true, $authJson)", (result as ExternalAuthResult.Success).callbackScript)
    }

    @Test
    fun `Given auth failure with anonymous session when getExternalAuth then returns Failed with AuthenticationError`() = runTest {
        val payload = AuthPayload(callback = "externalAuthCallback", force = false)
        coEvery { authRepository.retrieveExternalAuthentication(false) } throws Exception("Auth failed")
        coEvery { authRepository.getSessionState() } returns SessionState.ANONYMOUS

        val result = manager.getExternalAuth(serverId = 1, payload = payload)

        assertTrue(result is ExternalAuthResult.Failed)
        val failed = result as ExternalAuthResult.Failed
        assertEquals("externalAuthCallback(false)", failed.callbackScript)
        val error = failed.error as FrontendError.AuthenticationError
        assertEquals("Auth failed", error.errorDetails)
        assertEquals("ExternalAuthFailed", error.rawErrorType)
    }

    @Test
    fun `Given auth failure with connected session when getExternalAuth then returns Failed without error`() = runTest {
        val payload = AuthPayload(callback = "externalAuthCallback", force = false)
        coEvery { authRepository.retrieveExternalAuthentication(false) } throws Exception("Auth failed")
        coEvery { authRepository.getSessionState() } returns SessionState.CONNECTED

        val result = manager.getExternalAuth(serverId = 1, payload = payload)

        assertTrue(result is ExternalAuthResult.Failed)
        val failed = result as ExternalAuthResult.Failed
        assertEquals("externalAuthCallback(false)", failed.callbackScript)
        assertNull(failed.error)
    }

    @Test
    fun `Given auth failure and session check failure when getExternalAuth then returns Failed with AuthenticationError`() = runTest {
        val payload = AuthPayload(callback = "externalAuthCallback", force = false)
        coEvery { authRepository.retrieveExternalAuthentication(false) } throws Exception("Auth failed")
        coEvery { authRepository.getSessionState() } throws Exception("Session check failed")

        val result = manager.getExternalAuth(serverId = 1, payload = payload)

        // When session check fails, treated as anonymous and returns AuthenticationError
        assertTrue(result is ExternalAuthResult.Failed)
        val failed = result as ExternalAuthResult.Failed
        assertEquals("externalAuthCallback(false)", failed.callbackScript)
        val error = failed.error as FrontendError.AuthenticationError
        assertEquals("Auth failed", error.errorDetails)
        assertEquals("ExternalAuthFailed", error.rawErrorType)
    }

    @Test
    fun `Given successful revoke when revokeExternalAuth then returns Success`() = runTest {
        val payload = AuthPayload(callback = "revokeCallback", force = false)
        coEvery { authRepository.revokeSession() } returns Unit

        val result = manager.revokeExternalAuth(serverId = 1, payload = payload)

        assertTrue(result is RevokeAuthResult.Success)
        assertEquals("revokeCallback(true)", (result as RevokeAuthResult.Success).callbackScript)
    }

    @Test
    fun `Given revoke failure when revokeExternalAuth then returns Failed`() = runTest {
        val payload = AuthPayload(callback = "revokeCallback", force = false)
        coEvery { authRepository.revokeSession() } throws Exception("Revoke failed")

        val result = manager.revokeExternalAuth(serverId = 1, payload = payload)

        assertTrue(result is RevokeAuthResult.Failed)
        assertEquals("revokeCallback(false)", (result as RevokeAuthResult.Failed).callbackScript)
    }
}
