package io.homeassistant.companion.android.data.integration

import io.homeassistant.companion.android.data.HomeAssistantMockService
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import retrofit2.Response

object IntegrationServiceSpec : Spek({
    describe("an integration service") {
        val mockService by memoized { HomeAssistantMockService(IntegrationService::class.java) }

        lateinit var request: RecordedRequest
        describe("registerDevice") {
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

        describe("updateLocation") {
            lateinit var response: Response<ResponseBody>

            beforeEachTest {
                val updateLocationRequest = UpdateLocationRequest(
                    "locationName",
                    arrayOf(45.0, -45.0),
                    0,
                    1,
                    2,
                    3,
                    4,
                    5
                )
                val integrationRequest = IntegrationRequest(
                    "update_location",
                    updateLocationRequest
                )
                mockService.enqueueResponse(200, "integration/empty.json")
                response = runBlocking {
                    mockService.get().updateLocation(mockService.getMockServer().url("/path/to/hook"), integrationRequest)
                }
                request = mockService.takeRequest()
            }
            it("should serialize request") {
                assertThat(request.method).isEqualTo("POST")
                assertThat(request.path).isEqualTo("/path/to/hook")
            }
            it("should return success") {
                assertThat(response.isSuccessful).isTrue()
            }
        }
    }
})
