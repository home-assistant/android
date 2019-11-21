package io.homeassistant.companion.android.data.integration

import io.homeassistant.companion.android.data.HomeAssistantMockService
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object IntegrationServiceSpec : Spek({
    describe("an integration service") {
        val mockService by memoized { HomeAssistantMockService(IntegrationService::class.java) }

        lateinit var request: RecordedRequest
        lateinit var registrationResponse: RegisterDeviceResponse

        beforeEachTest {
            val registrationRequest = RegisterDeviceRequest(
                "appId",
                "appName",
                "appVersion",
                "deviceName",
                "manufacturer",
                "model",
                "osName",
                "osVersion",
                false,
                null
            )
            mockService.enqueueResponse(200, "integration/register.json")
            registrationResponse = runBlocking {
                mockService.get().registerDevice("123", registrationRequest)
            }
            request = mockService.takeRequest()
        }
        it("should serialize request") {
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path).isEqualTo("/api/mobile_app/registrations")
            assertThat(request.body.readUtf8())
                .contains("\"app_id\":\"appId\"")
        }
        it("should deserialize the response") {
            assertThat(registrationResponse.webhookId).isEqualTo("ABC")
        }
    }
})
