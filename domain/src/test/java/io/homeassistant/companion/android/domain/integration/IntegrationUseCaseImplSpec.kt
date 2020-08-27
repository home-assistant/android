package io.homeassistant.companion.android.domain.integration

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object IntegrationUseCaseImplSpec : Spek({
    describe("integration use case") {

        val integrationRepository by memoized {
            mockk<IntegrationRepository>(
                relaxed = true,
                relaxUnitFun = true
            )
        }
        val useCase by memoized { IntegrationUseCaseImpl(integrationRepository) }

        describe("registerDevice") {
            val deviceRegistration = mockk<DeviceRegistration>()
            beforeEachTest {
                coEvery {
                    integrationRepository.registerDevice(any())
                } just Runs

                runBlocking {
                    useCase.registerDevice(deviceRegistration)
                }
            }

            it("should call repository") {
                coVerify {
                    integrationRepository.registerDevice(deviceRegistration)
                }
            }
        }

        describe("updateRegistration") {
            beforeEachTest {
                coEvery {
                    integrationRepository.updateRegistration(any())
                } just Runs

                runBlocking {
                    useCase.updateRegistration("1", "2", "3", "4", "5", "6", "7")
                }
            }

            it("should call repository") {
                coVerify {
                    integrationRepository.updateRegistration(DeviceRegistration(
                        "1",
                        "2",
                        "7"
                        ))
                }
            }
        }

        describe("isRegistered") {
            beforeEachTest {
                runBlocking { useCase.isRegistered() }
            }

            it("should call the repository") {
                coVerify { integrationRepository.isRegistered() }
            }
        }

        describe("updateLocation") {
            val location = mockk<UpdateLocation>()
            beforeEachTest {
                runBlocking { useCase.updateLocation(location) }
            }

            it("should call the repository") {
                coVerify { integrationRepository.updateLocation(location) }
            }
        }

        describe("callService") {
            val serviceData = mockk<HashMap<String, Any>>()
            beforeEachTest {
                runBlocking { useCase.callService("domain", "service", serviceData) }
            }

            it("should call the repository") {
                coVerify { integrationRepository.callService("domain", "service", serviceData) }
            }
        }

        describe("getZones") {
            beforeEachTest {
                runBlocking { useCase.getZones() }
            }

            it("should call the repository") {
                coVerify { integrationRepository.getZones() }
            }
        }

        describe("getThemeColor") {
            beforeEachTest {
                runBlocking { useCase.getThemeColor() }
            }

            it("should call the repository") {
                coVerify { integrationRepository.getThemeColor() }
            }
        }
    }
})
