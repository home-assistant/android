package io.homeassistant.companion.android.common.data.authentication.impl

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.authentication.impl.entities.Token
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import retrofit2.Response

private const val SERVER_ID = 1
private const val INSTALL_ID = "install-id"
private const val LOCK_ENABLED_KEY = "${SERVER_ID}_biometric_enabled"

class AuthenticationRepositoryImplTest {

    private val keyChangesFlow = MutableSharedFlow<String>()
    private val mapperSlot = slot<suspend () -> Any>()
    private val localStorage = mockk<LocalStorage> {
        every { observeChanges(*anyVararg<String>()) } returns keyChangesFlow
        coEvery {
            observeChanges(*anyVararg<String>(), mapper = capture(mapperSlot))
        } answers {
            merge(keyChangesFlow, flowOf("")).map { mapperSlot.captured.invoke() }
        }
    }

    private lateinit var repository: AuthenticationRepositoryImpl

    @BeforeEach
    fun setup() {
        repository = AuthenticationRepositoryImpl(
            authenticationService = mockk(relaxed = true),
            serverManager = mockk<ServerManager>(relaxed = true),
            serverId = SERVER_ID,
            localStorage = localStorage,
            installId = INSTALL_ID,
        )
    }

    @Nested
    inner class LockEnabledRawFlow {

        @Test
        fun `Given collecting flow then the current setting is emitted immediately`() = runTest {
            coEvery { localStorage.getBoolean(LOCK_ENABLED_KEY) } returns true

            repository.lockEnabledRawFlow().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given collecting flow when the lock setting changes then the updated value is emitted`() = runTest {
            coEvery { localStorage.getBoolean(LOCK_ENABLED_KEY) } returns true

            repository.lockEnabledRawFlow().test {
                assertTrue(awaitItem())

                coEvery { localStorage.getBoolean(LOCK_ENABLED_KEY) } returns false
                keyChangesFlow.emit(LOCK_ENABLED_KEY)

                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class RefreshSession {

        private val authenticationService = mockk<AuthenticationService>()
        private val serverManager = mockk<ServerManager>(relaxed = true)
        private val server = Server(
            id = SERVER_ID,
            _name = "Home",
            connection = ServerConnectionInfo(externalUrl = "http://homeassistant:8123"),
            session = ServerSessionInfo(
                accessToken = "old-access",
                refreshToken = "old-refresh",
                tokenExpiration = 0,
                tokenType = "Bearer",
                installId = INSTALL_ID,
            ),
            user = ServerUserInfo(),
        )

        @BeforeEach
        fun setup() {
            val connectionStateProvider = mockk<ServerConnectionStateProvider> {
                every { urlFlow(any()) } returns
                    flowOf(UrlState.HasUrl("http://homeassistant:8123".toHttpUrl().toUrl()))
            }
            coEvery { serverManager.getServer(SERVER_ID) } returns server
            coEvery { serverManager.connectionStateProvider(SERVER_ID) } returns connectionStateProvider
            repository = AuthenticationRepositoryImpl(
                authenticationService = authenticationService,
                serverManager = serverManager,
                serverId = SERVER_ID,
                localStorage = localStorage,
                installId = INSTALL_ID,
            )
        }

        private suspend fun refreshWith(token: Token): ServerSessionInfo {
            coEvery { authenticationService.refreshToken(any(), any(), "old-refresh", any()) } returns
                Response.success(token)
            val updated = slot<Server>()

            repository.retrieveExternalAuthentication(forceRefresh = true)

            coVerify { serverManager.updateServer(capture(updated)) }
            return updated.captured.session
        }

        @Test
        fun `Given a response without refresh token when refreshing then the current refresh token is kept`() = runTest {
            val session = refreshWith(Token(accessToken = "new-access", expiresIn = 1800, tokenType = "Bearer"))

            assertEquals("new-access", session.accessToken)
            assertEquals("old-refresh", session.refreshToken)
        }

        @Test
        fun `Given a response with refresh token when refreshing then the new refresh token is stored`() = runTest {
            val session = refreshWith(
                Token(
                    accessToken = "new-access",
                    expiresIn = 1800,
                    refreshToken = "new-refresh",
                    tokenType = "Bearer",
                ),
            )

            assertEquals("new-refresh", session.refreshToken)
        }
    }
}
