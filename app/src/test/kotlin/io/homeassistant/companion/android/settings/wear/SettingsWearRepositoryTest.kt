package io.homeassistant.companion.android.settings.wear

import io.homeassistant.companion.android.common.data.authentication.AuthorizationException
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.authentication.impl.entities.Token
import io.homeassistant.companion.android.common.data.integration.IntegrationException
import io.homeassistant.companion.android.common.data.integration.impl.IntegrationService
import io.homeassistant.companion.android.common.data.integration.impl.entities.EntityResponse
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import retrofit2.Response

@ExtendWith(ConsoleLogExtension::class)
class SettingsWearRepositoryTest {

    private val authenticationService: AuthenticationService = mockk()
    private val integrationService: IntegrationService = mockk()
    private val repository = SettingsWearRepository(authenticationService, integrationService)

    @BeforeEach
    fun setUp() {
        // Always override the handler so individual test can decide to override it or not without impacting the other tests
        FailFast.setHandler { exception, _ ->
            fail("Unhandled exception caught", exception)
        }
    }

    private fun createWearServer(
        externalUrl: String = "https://ha.local:8123",
        cloudUrl: String? = null,
        webhookId: String = "test_webhook",
        cloudhookUrl: String? = null,
        accessToken: String? = null,
    ) = WearServer(
        serverId = 42,
        externalUrl = externalUrl,
        cloudUrl = cloudUrl,
        webhookId = webhookId,
        cloudhookUrl = cloudhookUrl,
        accessToken = accessToken,
    )

    @Nested
    inner class WearServerTest {

        @Test
        fun `Given cloud and external URLs, when getBaseUrls, then return both with cloud first`() {
            val server = createWearServer(
                externalUrl = "https://external.local:8123",
                cloudUrl = "https://cloud.nabu.casa",
            )

            val urls = server.getBaseUrls()

            assertEquals(2, urls.size)
            assertEquals("https://cloud.nabu.casa/".toHttpUrl(), urls[0])
            assertEquals("https://external.local:8123/".toHttpUrl(), urls[1])
        }

        @Test
        fun `Given only external URL, when getBaseUrls, then return only external URL`() {
            val server = createWearServer(
                externalUrl = "https://external.local:8123",
                cloudUrl = null,
            )

            val urls = server.getBaseUrls()

            assertEquals(1, urls.size)
            assertEquals("https://external.local:8123/".toHttpUrl(), urls[0])
        }

        @Test
        fun `Given invalid external URL, when getBaseUrls, then return empty list`() {
            val server = createWearServer(
                externalUrl = "not_a_valid_url",
                cloudUrl = null,
            )

            val urls = server.getBaseUrls()

            assertTrue(urls.isEmpty())
        }

        @Test
        fun `Given cloudhook and external URLs, when getWebhookUrls, then return both with cloudhook first`() {
            val server = createWearServer(
                externalUrl = "https://external.local:8123",
                cloudhookUrl = "https://hooks.nabu.casa/webhook123",
                webhookId = "webhook_abc",
            )

            val urls = server.getWebhookUrls()

            assertEquals(2, urls.size)
            assertEquals("https://hooks.nabu.casa/webhook123".toHttpUrl(), urls[0])
            assertEquals("https://external.local:8123/api/webhook/webhook_abc".toHttpUrl(), urls[1])
        }

        @Test
        fun `Given only external URL, when getWebhookUrls, then return external webhook URL`() {
            val server = createWearServer(
                externalUrl = "https://external.local:8123",
                cloudhookUrl = null,
                webhookId = "webhook_abc",
            )

            val urls = server.getWebhookUrls()

            assertEquals(1, urls.size)
            assertEquals("https://external.local:8123/api/webhook/webhook_abc".toHttpUrl(), urls[0])
        }

        @Test
        fun `Given invalid external URL, when getWebhookUrls, then return empty list`() {
            val server = createWearServer(
                externalUrl = "invalid",
                cloudhookUrl = null,
            )

            val urls = server.getWebhookUrls()

            assertTrue(urls.isEmpty())
        }
    }

    @Nested
    inner class RegisterRefreshTokenTest {

        @Test
        fun `Given successful token refresh, when registerRefreshToken, then return server with new access token`() = runTest {
            val server = createWearServer()
            val refreshToken = "refresh_token_123"
            val newAccessToken = "new_access_token_456"
            val urlSlot = slot<HttpUrl>()

            coEvery {
                authenticationService.refreshToken(capture(urlSlot), any(), any(), any())
            } returns Response.success(Token(newAccessToken, 3600, refreshToken, "Bearer"))

            val result = repository.registerRefreshToken(server, refreshToken)

            assertEquals(newAccessToken, result.accessToken)
            assertEquals(server.externalUrl, result.externalUrl)
            assertTrue(urlSlot.captured.encodedPath.endsWith("/auth/token"))
            coVerify {
                authenticationService.refreshToken(
                    any(),
                    AuthenticationService.GRANT_TYPE_REFRESH,
                    refreshToken,
                    AuthenticationService.CLIENT_ID,
                )
            }
        }

        @Test
        fun `Given null response body, when registerRefreshToken, then throw IntegrationException with AuthorizationException cause`() = runTest {
            val server = createWearServer()

            coEvery {
                authenticationService.refreshToken(any(), any(), any(), any())
            } returns Response.success(null)

            try {
                repository.registerRefreshToken(server, "refresh_token")
                fail("Expected IntegrationException to be thrown")
            } catch (e: IntegrationException) {
                assertInstanceOf(AuthorizationException::class.java, e.cause)
            }
        }

        @Test
        fun `Given unsuccessful response, when registerRefreshToken, then throw IntegrationException`() = runTest {
            val server = createWearServer()

            coEvery {
                authenticationService.refreshToken(any(), any(), any(), any())
            } returns Response.error(401, mockk(relaxed = true))

            try {
                repository.registerRefreshToken(server, "refresh_token")
                fail("Expected IntegrationException to be thrown")
            } catch (_: IntegrationException) {
                // Continue
            }
        }
    }

    @Nested
    inner class RenderTemplateTest {

        @Test
        fun `Given JsonPrimitive result, when renderTemplate, then return content without quotes`() = runTest {
            val server = createWearServer()
            val template = "{{ states('sensor.temperature') }}"
            val expectedResult = "23.5"

            coEvery {
                integrationService.getTemplate(any(), any())
            } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

            val result = repository.renderTemplate(server, template)

            assertEquals(expectedResult, result)
        }

        @Test
        fun `Given null JsonPrimitive content, when renderTemplate, then return null`() = runTest {
            val server = createWearServer()

            coEvery {
                integrationService.getTemplate(any(), any())
            } returns JsonObject(mapOf("template" to JsonPrimitive(null as String?)))

            val result = repository.renderTemplate(server, "{{ none }}")

            assertNull(result)
        }

        @Test
        fun `Given non-primitive result, when renderTemplate, then return toString`() = runTest {
            val server = createWearServer()
            val jsonObject = JsonObject(mapOf("key" to JsonPrimitive("value")))

            coEvery {
                integrationService.getTemplate(any(), any())
            } returns JsonObject(mapOf("template" to jsonObject))

            val result = repository.renderTemplate(server, "{{ states | tojson }}")

            assertEquals(jsonObject.toString(), result)
        }
    }

    @Nested
    inner class GetEntitiesTest {

        @Test
        fun `Given valid access token and entities, when getEntities, then return mapped entities`() = runTest {
            val server = createWearServer(accessToken = "valid_token")
            val now = LocalDateTime.now()
            val entityResponses = listOf(
                EntityResponse("light.living_room", "on", mapOf("brightness" to 255), now, now),
                EntityResponse("sensor.temp", "23.5", mapOf("unit" to "°C"), now, now),
            )
            val urlSlot = slot<HttpUrl>()

            coEvery {
                integrationService.getStates(capture(urlSlot), any())
            } returns entityResponses

            val result = repository.getEntities(server)

            assertEquals(2, result.size)
            assertEquals("light.living_room", result[0].entityId)
            assertEquals("on", result[0].state)
            assertEquals("sensor.temp", result[1].entityId)
            assertTrue(urlSlot.captured.encodedPath.endsWith("/api/states"))
            coVerify {
                integrationService.getStates(any(), "Bearer valid_token")
            }
        }

        @Test
        fun `Given null access token, when getEntities, then return empty list and trigger fail fast`() = runTest {
            val server = createWearServer(accessToken = null)
            var failFastTriggered = false

            FailFast.setHandler { exception, additionalMessage ->
                failFastTriggered = true
            }

            val result = repository.getEntities(server)

            assertTrue(failFastTriggered)
            assertTrue(result.isEmpty())
            coVerify(exactly = 0) { integrationService.getStates(any(), any()) }
        }

        @Test
        fun `Given IntegrationException, when getEntities, then return empty list`() = runTest {
            val server = createWearServer(accessToken = "valid_token")

            coEvery {
                integrationService.getStates(any(), any())
            } throws IntegrationException("Network error")

            val result = repository.getEntities(server)

            assertTrue(result.isEmpty())
        }
    }
}
