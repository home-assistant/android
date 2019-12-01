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

        describe("isRegistered") {
            beforeEachTest {
                runBlocking { useCase.isRegistered() }
            }

            it("should call the repository") {
                coVerify { integrationRepository.isRegistered() }
            }
        }
    }
})
