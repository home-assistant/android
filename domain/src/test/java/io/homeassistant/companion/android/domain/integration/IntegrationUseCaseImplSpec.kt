package io.homeassistant.companion.android.domain.integration

import io.mockk.coEvery
import io.mockk.coVerify
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
            beforeEachTest {
                coEvery {
                    integrationRepository.registerDevice(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                    )
                } returns true

                runBlocking {
                    useCase.registerDevice(
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
                }
            }

            it("should call repository") {
                coVerify {
                    integrationRepository.registerDevice(
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