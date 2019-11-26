package io.homeassistant.companion.android.data.integration

import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.domain.authentication.AuthenticationRepository
import io.homeassistant.companion.android.domain.integration.DeviceRegistration
import io.homeassistant.companion.android.domain.integration.UpdateLocation
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import retrofit2.Response
import java.net.URL
import kotlin.properties.Delegates

object IntegrationRepositoryImplSpec : Spek({

    describe("a repository") {

        val localStorage by memoized { mockk<LocalStorage>(relaxUnitFun = true) }
        val integrationService by memoized { mockk<IntegrationService>(relaxUnitFun = true) }
        val authenticationRepository by memoized { mockk<AuthenticationRepository>(relaxUnitFun = true) }

        val repository by memoized {
            IntegrationRepositoryImpl(
                integrationService,
                authenticationRepository,
                localStorage
            )
        }

        describe("registerDevice") {
            beforeEachTest {
                val deviceRegistration = DeviceRegistration(
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
                val registerDeviceRequest = RegisterDeviceRequest(
                    deviceRegistration.appId,
                    deviceRegistration.appName,
                    deviceRegistration.appVersion,
                    deviceRegistration.deviceName,
                    deviceRegistration.manufacturer,
                    deviceRegistration.model,
                    deviceRegistration.osName,
                    deviceRegistration.osVersion,
                    deviceRegistration.supportsEncryption,
                    deviceRegistration.appData
                )
                coEvery {
                    integrationService.registerDevice("ABC123", registerDeviceRequest)
                } returns mockk {
                    every { cloudhookUrl } returns "https://home-assistant.io/1/"
                    every { remoteUiUrl } returns "https://home-assistant.io/2/"
                    every { secret } returns "ABCDE"
                    every { webhookId } returns "FGHIJ"
                }

                coEvery { authenticationRepository.buildBearerToken() } returns "ABC123"

                runBlocking {
                    repository.registerDevice(deviceRegistration)
                }
            }

            it("should save response data") {
                coVerifyAll {
                    localStorage.putString("cloud_url", "https://home-assistant.io/1/")
                    localStorage.putString("remote_ui_url", "https://home-assistant.io/2/")
                    localStorage.putString("secret", "ABCDE")
                    localStorage.putString("webhook_id", "FGHIJ")
                }
            }
        }

        describe("is registered") {
            beforeEachTest {
                coEvery { localStorage.getString("webhook_id") } returns "FGHIJ"
            }
            describe("isRegistered") {
                var isRegistered by Delegates.notNull<Boolean>()
                beforeEachTest {
                    runBlocking { isRegistered = repository.isRegistered() }
                }
                it("should return true when webhook has a value") {
                    Assertions.assertThat(isRegistered).isTrue()
                }
            }
        }

        describe("is not registered") {
            beforeEachTest {
                coEvery { localStorage.getString("webhook_id") } returns null
            }
            describe("isRegistered") {
                var isRegistered by Delegates.notNull<Boolean>()
                beforeEachTest {
                    runBlocking { isRegistered = repository.isRegistered() }
                }
                it("should return false when webhook has no value") {
                    Assertions.assertThat(isRegistered).isFalse()
                }

            }
        }

        describe("location updated") {
            beforeEachTest {
                coEvery { authenticationRepository.getUrl() } returns URL("http://example.com")
                coEvery { localStorage.getString("webhook_id") } returns "FGHIJ"
            }
            describe("updateLocation") {
                val location = UpdateLocation(
                    "locationName",
                    arrayOf(45.0, -45.0),
                    0,
                    1,
                    2,
                    3,
                    4,
                    5
                )
                val updateLocationRequest = UpdateLocationRequest(
                    location.locationName,
                    location.gps,
                    location.gpsAccuracy,
                    location.battery,
                    location.speed,
                    location.altitude,
                    location.course,
                    location.verticalAccuracy
                )
                val integrationRequest = IntegrationRequest(
                    "update_location",
                    updateLocationRequest
                )
                beforeEachTest {
                    coEvery { localStorage.getString("cloud_url") } returns null
                    coEvery { localStorage.getString("remote_ui_url") } returns null
                    coEvery {
                        integrationService.updateLocation(
                            any(),//"http://example.com/api/webhook/FGHIJ",
                            any()//integrationRequest
                        )
                    } returns Response.success(null)
                    runBlocking { repository.updateLocation(location) }
                }

                it("should call the service.") {
                    coVerify {
                        integrationService.updateLocation(
                            "http://example.com/api/webhook/FGHIJ",
                            integrationRequest
                        )
                    }
                }
            }
        }
    }
})
