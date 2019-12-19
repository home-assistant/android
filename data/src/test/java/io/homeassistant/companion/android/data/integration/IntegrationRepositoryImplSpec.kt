package io.homeassistant.companion.android.data.integration

import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.domain.authentication.AuthenticationRepository
import io.homeassistant.companion.android.domain.integration.DeviceRegistration
import io.homeassistant.companion.android.domain.integration.Entity
import io.homeassistant.companion.android.domain.integration.UpdateLocation
import io.homeassistant.companion.android.domain.integration.ZoneAttributes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.every
import io.mockk.mockk
import java.net.URL
import java.util.Calendar
import java.util.HashMap
import kotlin.properties.Delegates
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import retrofit2.Response

object IntegrationRepositoryImplSpec : Spek({

    describe("a repository") {

        val localStorage by memoized { mockk<LocalStorage>(relaxUnitFun = true) }
        val integrationService by memoized { mockk<IntegrationService>(relaxUnitFun = true) }
        val authenticationRepository by memoized { mockk<AuthenticationRepository>(relaxUnitFun = true) }

        val repository by memoized {
            IntegrationRepositoryImpl(
                integrationService,
                authenticationRepository,
                localStorage,
                "manufacturer",
                "model",
                "osVersion"
            )
        }

        describe("registerDevice") {
            beforeEachTest {
                val deviceRegistration = DeviceRegistration(
                    "appVersion",
                    "deviceName",
                    "pushToken"
                )
                val registerDeviceRequest = RegisterDeviceRequest(
                    "io.homeassistant.companion.android",
                    "Home Assistant",
                    deviceRegistration.appVersion,
                    deviceRegistration.deviceName,
                    "manufacturer",
                    "model",
                    "Android",
                    "osVersion",
                    false,
                    hashMapOf(
                        "push_url" to "https://mobile-apps.home-assistant.io/api/sendPushNotification",
                        "push_token" to (deviceRegistration.pushToken ?: "push_token")
                    )
                )

                coEvery { localStorage.getString("app_version") } returns "app_version"
                coEvery { localStorage.getString("device_name") } returns "device_name"
                coEvery { localStorage.getString("push_token") } returns "push_token"

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
                coVerify {
                    localStorage.putString("cloud_url", "https://home-assistant.io/1/")
                    localStorage.putString("remote_ui_url", "https://home-assistant.io/2/")
                    localStorage.putString("secret", "ABCDE")
                    localStorage.putString("webhook_id", "FGHIJ")
                }
            }

            it("should save registration data") {
                coVerify {
                    localStorage.putString("app_version", "appVersion")
                    localStorage.putString("device_name", "deviceName")
                    localStorage.putString("push_token", "pushToken")
                }
            }
        }

        describe("updateRegistration") {
            val deviceRegistration = DeviceRegistration(
                "appVersion",
                "deviceName",
                "pushToken"
            )
            val registerDeviceRequest = RegisterDeviceRequest(
                "io.homeassistant.companion.android",
                "Home Assistant",
                deviceRegistration.appVersion,
                deviceRegistration.deviceName,
                "manufacturer",
                "model",
                "Android",
                "osVersion",
                false,
                hashMapOf(
                    "push_url" to "https://mobile-apps.home-assistant.io/api/sendPushNotification",
                    "push_token" to (deviceRegistration.pushToken ?: "push_token")
                )
            )
            beforeEachTest {
                coEvery {
                    integrationService.updateRegistration(any(), IntegrationRequest("update_registration", registerDeviceRequest))
                } returns Response.success(null)

                coEvery { authenticationRepository.getUrl() } returns URL("http://example.com")
                coEvery { localStorage.getString("webhook_id") } returns "FGHIJ"
                coEvery { localStorage.getString("cloud_url") } returns "http://best.com/hook/id"
                coEvery { localStorage.getString("remote_ui_url") } returns "http://better.com"

                coEvery { localStorage.getString("app_version") } returns "app_version"
                coEvery { localStorage.getString("device_name") } returns "device_name"
                coEvery { localStorage.getString("push_token") } returns "push_token"

                runBlocking {
                    repository.updateRegistration(deviceRegistration)
                }
            }

            it("should call the service") {
                coVerify {
                    integrationService.updateRegistration(
                        "http://best.com/hook/id".toHttpUrl(),
                        IntegrationRequest("update_registration", registerDeviceRequest))
                }
            }

            it("should persist the registration") {
                coVerify {
                    localStorage.putString("app_version", "appVersion")
                    localStorage.putString("device_name", "deviceName")
                    localStorage.putString("push_token", "pushToken")
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
                    assertThat(isRegistered).isTrue()
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
                    assertThat(isRegistered).isFalse()
                }
            }
        }

        describe("location updated") {
            beforeEachTest {
                coEvery { authenticationRepository.getUrl() } returns URL("http://example.com")
                coEvery { localStorage.getString("webhook_id") } returns "FGHIJ"
            }

            describe("updateLocation cloud url") {
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
                val integrationRequest = IntegrationRequest(
                    "update_location",
                    UpdateLocationRequest(
                        location.locationName,
                        location.gps,
                        location.gpsAccuracy,
                        location.battery,
                        location.speed,
                        location.altitude,
                        location.course,
                        location.verticalAccuracy
                    )
                )
                beforeEachTest {
                    coEvery { localStorage.getString("cloud_url") } returns "http://best.com/hook/id"
                    coEvery { localStorage.getString("remote_ui_url") } returns "http://better.com"
                    coEvery {
                        integrationService.updateLocation(
                            any(), // "http://example.com/api/webhook/FGHIJ",
                            any() // integrationRequest
                        )
                    } returns Response.success(null)
                    runBlocking { repository.updateLocation(location) }
                }

                it("should call the service.") {
                    coVerify {
                        integrationService.updateLocation(
                            "http://best.com/hook/id".toHttpUrl(),
                            integrationRequest
                        )
                    }
                }
            }

            describe("updateLocation remote ui url") {
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
                val integrationRequest = IntegrationRequest(
                    "update_location",
                    UpdateLocationRequest(
                        location.locationName,
                        location.gps,
                        location.gpsAccuracy,
                        location.battery,
                        location.speed,
                        location.altitude,
                        location.course,
                        location.verticalAccuracy
                    )
                )
                beforeEachTest {
                    coEvery { localStorage.getString("cloud_url") } returns null
                    coEvery { localStorage.getString("remote_ui_url") } returns "http://better.com"
                    coEvery {
                        integrationService.updateLocation(
                            any(), // "http://example.com/api/webhook/FGHIJ",
                            any() // integrationRequest
                        )
                    } returns Response.success(null)
                    runBlocking { repository.updateLocation(location) }
                }

                it("should call the service.") {
                    coVerify {
                        integrationService.updateLocation(
                            "http://better.com/api/webhook/FGHIJ".toHttpUrl(),
                            integrationRequest
                        )
                    }
                }
            }

            describe("updateLocation auth url") {
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
                val integrationRequest = IntegrationRequest(
                    "update_location",
                    UpdateLocationRequest(
                        location.locationName,
                        location.gps,
                        location.gpsAccuracy,
                        location.battery,
                        location.speed,
                        location.altitude,
                        location.course,
                        location.verticalAccuracy
                    )
                )
                beforeEachTest {
                    coEvery { localStorage.getString("cloud_url") } returns null
                    coEvery { localStorage.getString("remote_ui_url") } returns null
                    coEvery {
                        integrationService.updateLocation(
                            any(), // "http://example.com/api/webhook/FGHIJ",
                            any() // integrationRequest
                        )
                    } returns Response.success(null)
                    runBlocking { repository.updateLocation(location) }
                }

                it("should call the service.") {
                    coVerify {
                        integrationService.updateLocation(
                            "http://example.com/api/webhook/FGHIJ".toHttpUrl(),
                            integrationRequest
                        )
                    }
                }
            }

            describe("updateLocation fail then succeeds") {
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
                val integrationRequest = IntegrationRequest(
                    "update_location",
                    UpdateLocationRequest(
                        location.locationName,
                        location.gps,
                        location.gpsAccuracy,
                        location.battery,
                        location.speed,
                        location.altitude,
                        location.course,
                        location.verticalAccuracy
                    )
                )

                beforeEachTest {
                    coEvery { localStorage.getString("cloud_url") } returns "http://best.com/hook/id"
                    coEvery { localStorage.getString("remote_ui_url") } returns "http://better.com"
                    coEvery {
                        integrationService.updateLocation(
                            "http://best.com/hook/id".toHttpUrl(),
                            any() // integrationRequest
                        )
                    } returns mockk {
                        every { isSuccessful } returns false
                    }
                    coEvery {
                        integrationService.updateLocation(
                            "http://better.com/api/webhook/FGHIJ".toHttpUrl(),
                            any() // integrationRequest
                        )
                    } returns Response.success(null)

                    runBlocking { repository.updateLocation(location) }
                }

                it("should call service 2 times") {
                    coVerifyAll {
                        integrationService.updateLocation(
                            "http://best.com/hook/id".toHttpUrl(),
                            integrationRequest
                        )
                        integrationService.updateLocation(
                            "http://better.com/api/webhook/FGHIJ".toHttpUrl(),
                            integrationRequest
                        )
                    }
                }
            }

            describe("updateLocation failure") {
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

                lateinit var thrown: Throwable

                beforeEachTest {
                    coEvery { localStorage.getString("cloud_url") } returns "http://best.com/hook/id"
                    coEvery { localStorage.getString("remote_ui_url") } returns "http://better.com"
                    coEvery {
                        integrationService.updateLocation(
                            "http://best.com/hook/id".toHttpUrl(),
                            any() // integrationRequest
                        )
                    } returns mockk {
                        every { isSuccessful } returns false
                    }
                    coEvery {
                        integrationService.updateLocation(
                            "http://better.com/api/webhook/FGHIJ".toHttpUrl(),
                            any() // integrationRequest
                        )
                    } returns mockk {
                        every { isSuccessful } returns false
                    }
                    coEvery {
                        integrationService.updateLocation(
                            "http://example.com/api/webhook/FGHIJ".toHttpUrl(),
                            any() // integrationRequest
                        )
                    } returns mockk {
                        every { isSuccessful } returns false
                    }

                    thrown = catchThrowable { runBlocking { repository.updateLocation(location) } }
                }

                it("should throw an exception") {
                    assertThat(thrown).isInstanceOf(IntegrationException::class.java)
                }
            }

            describe("updateLocation with trailing slash") {
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
                val integrationRequest = IntegrationRequest(
                    "update_location",
                    UpdateLocationRequest(
                        location.locationName,
                        location.gps,
                        location.gpsAccuracy,
                        location.battery,
                        location.speed,
                        location.altitude,
                        location.course,
                        location.verticalAccuracy
                    )
                )
                beforeEachTest {
                    coEvery { localStorage.getString("cloud_url") } returns null
                    coEvery { localStorage.getString("remote_ui_url") } returns "http://better.com/"
                    coEvery {
                        integrationService.updateLocation(
                            any(), // "http://example.com/api/webhook/FGHIJ",
                            any() // integrationRequest
                        )
                    } returns Response.success(null)
                    runBlocking { repository.updateLocation(location) }
                }

                it("should call the service.") {
                    coVerify {
                        integrationService.updateLocation(
                            "http://better.com/api/webhook/FGHIJ".toHttpUrl(),
                            integrationRequest
                        )
                    }
                }
            }
        }

        describe("get zones") {
            beforeEachTest {
                coEvery { localStorage.getString("cloud_url") } returns "https://best.com"
                coEvery { localStorage.getString("remote_ui_url") } returns "http://better.com/"
                coEvery { authenticationRepository.getUrl() } returns URL("http://example.com")
                coEvery { localStorage.getString("webhook_id") } returns "FGHIJ"
            }
            describe("getZones") {
                val entities = EntityResponse(
                    "entityId",
                    "state",
                    ZoneAttributes(
                        false,
                        0.0,
                        1.1,
                        2.2F,
                        "fName",
                        "icon"
                    ),
                    Calendar.getInstance(),
                    Calendar.getInstance(),
                    HashMap()
                )
                var zones: Array<Entity<ZoneAttributes>>? = null
                beforeEachTest {
                    coEvery { integrationService.getZones(any(), any()) } returns arrayOf(entities)
                    runBlocking { zones = repository.getZones() }
                }
                it("should return true when webhook has a value") {
                    assertThat(zones).isNotNull
                    assertThat(zones!!.size).isEqualTo(1)
                    assertThat(zones!![0]).isNotNull
                    assertThat(zones!![0].entityId).isEqualTo(entities.entityId)
                }
            }
        }

        describe("location settings") {
            describe("isZoneTrackingEnabled") {
                var isZoneTrackingEnabled by Delegates.notNull<Boolean>()
                beforeEachTest {
                    coEvery { localStorage.getBoolean("zone_enabled") } returns true
                    runBlocking { isZoneTrackingEnabled = repository.isZoneTrackingEnabled() }
                }
                it("should return what is stored") {
                    assertThat(isZoneTrackingEnabled).isTrue()
                }
            }

            describe("setZoneTrackingEnabled") {
                beforeEachTest {
                    runBlocking { repository.setZoneTrackingEnabled(true) }
                }
                it("should return what is stored") {
                    coVerify {
                        localStorage.putBoolean("zone_enabled", true)
                    }
                }
            }

            describe("isBackgroundTrackingEnabled") {
                var isBackgroundTrackingEnabled by Delegates.notNull<Boolean>()
                beforeEachTest {
                    coEvery { localStorage.getBoolean("background_enabled") } returns true
                    runBlocking { isBackgroundTrackingEnabled = repository.isBackgroundTrackingEnabled() }
                }
                it("should return what is stored") {
                    assertThat(isBackgroundTrackingEnabled).isTrue()
                }
            }

            describe("setBackgroundTrackingEnabled") {
                beforeEachTest {
                    runBlocking { repository.setBackgroundTrackingEnabled(true) }
                }
                it("should return what is stored") {
                    coVerify {
                        localStorage.putBoolean("background_enabled", true)
                    }
                }
            }
        }
    }
})
