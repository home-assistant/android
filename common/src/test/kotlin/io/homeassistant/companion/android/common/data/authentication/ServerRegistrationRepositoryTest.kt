package io.homeassistant.companion.android.common.data.authentication

import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService.Companion.SEGMENT_AUTH_TOKEN
import io.homeassistant.companion.android.common.data.authentication.impl.entities.Token
import io.homeassistant.companion.android.common.util.di.SuspendProvider
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ConsoleLogExtension::class)
class ServerRegistrationRepositoryTest {

    private val authenticationService: AuthenticationService = mockk()
    private val installIdProvider: SuspendProvider<String> = mockk()

    private var repository: ServerRegistrationRepository = ServerRegistrationRepository(authenticationService, installIdProvider)

    @Test
    fun `Given valid URL and token with refresh token, when registering auth code, then return TemporaryServer and call service correctly`() = runTest {
        val url = "https://homeassistant.local:8123"
        val authCode = "test_auth_code"
        val installId = "test_install_id"
        val token = Token(
            accessToken = "access_token_123",
            expiresIn = 3600,
            refreshToken = "refresh_token_456",
            tokenType = "Bearer",
        )
        val urlSlot = slot<HttpUrl>()

        coEvery { installIdProvider.invoke() } returns installId
        coEvery {
            authenticationService.getToken(capture(urlSlot), any(), any(), any())
        } returns token

        val result = repository.registerAuthorizationCode(
            url = url,
            authorizationCode = authCode,
            allowInsecureConnection = false,
        )

        assertNotNull(result)
        assertEquals(url, result!!.externalUrl)
        assertEquals(false, result.allowInsecureConnection)
        assertEquals("access_token_123", result.session.accessToken)
        assertEquals("refresh_token_456", result.session.refreshToken)
        assertEquals("Bearer", result.session.tokenType)
        assertEquals(installId, result.session.installId)
        assertNotNull(result.session.tokenExpiration)

        val capturedUrl = urlSlot.captured
        assertEquals("homeassistant.local", capturedUrl.host)
        assertEquals(8123, capturedUrl.port)
        assertTrue(capturedUrl.encodedPath.endsWith("/$SEGMENT_AUTH_TOKEN"))
        coVerify {
            authenticationService.getToken(
                any(),
                AuthenticationService.GRANT_TYPE_CODE,
                authCode,
                AuthenticationService.CLIENT_ID,
            )
        }
    }

    @Test
    fun `Given valid URL and token without refresh token, when registering auth code, then return null`() = runTest {
        val token = Token(
            accessToken = "access_token",
            expiresIn = 3600,
            refreshToken = null,
            tokenType = "Bearer",
        )

        coEvery {
            authenticationService.getToken(any(), any(), any(), any())
        } returns token

        val result = repository.registerAuthorizationCode(
            url = "https://homeassistant.local:8123",
            authorizationCode = "auth_code",
            allowInsecureConnection = null,
        )

        assertNull(result)
    }

    @Test
    fun `Given invalid URL, when registering auth code, then return null without calling auth service`() = runTest {
        val result = repository.registerAuthorizationCode(
            url = "not_a_valid_url",
            authorizationCode = "auth_code",
            allowInsecureConnection = null,
        )

        assertNull(result)
        coVerify(exactly = 0) { authenticationService.getToken(any(), any(), any(), any()) }
    }

    @Test
    fun `Given empty URL, when registering auth code, then return null`() = runTest {
        val result = repository.registerAuthorizationCode(
            url = "",
            authorizationCode = "auth_code",
            allowInsecureConnection = null,
        )

        assertNull(result)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    @NullSource
    fun `Given allowInsecureConnection value, when registering, then pass through to TemporaryServer`(
        allowInsecure: Boolean?,
    ) = runTest {
        coEvery { installIdProvider.invoke() } returns "install_id"
        coEvery {
            authenticationService.getToken(any(), any(), any(), any())
        } returns Token("access", 3600, "refresh", "Bearer")

        val result = repository.registerAuthorizationCode(
            url = "https://ha.local:8123",
            authorizationCode = "code",
            allowInsecureConnection = allowInsecure,
        )

        assertNotNull(result)
        assertEquals(allowInsecure, result!!.allowInsecureConnection)
    }

    @Test
    fun `Given valid registration, when token expires in future, then expiration is calculated correctly`() = runTest {
        val expiresInSeconds = 3600
        val beforeCallTime = System.currentTimeMillis() / 1000

        coEvery { installIdProvider.invoke() } returns "install_id"
        coEvery {
            authenticationService.getToken(any(), any(), any(), any())
        } returns Token("access", expiresInSeconds, "refresh", "Bearer")

        val result = repository.registerAuthorizationCode(
            url = "https://ha.local:8123",
            authorizationCode = "code",
            allowInsecureConnection = null,
        )

        val afterCallTime = System.currentTimeMillis() / 1000

        assertNotNull(result)
        val tokenExpiration = result!!.session.tokenExpiration!!
        assertTrue(tokenExpiration >= beforeCallTime + expiresInSeconds)
        assertTrue(tokenExpiration <= afterCallTime + expiresInSeconds)
    }
}
