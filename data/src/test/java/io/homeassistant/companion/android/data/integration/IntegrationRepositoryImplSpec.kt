package io.homeassistant.companion.android.data.integration

import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.domain.authentication.AuthenticationRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

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
                coEvery {
                    integrationService.registerDevice(any(), any())
                } returns mockk {
                    every { cloudhookUrl } returns "https://home-assistant.io/1/"
                    every { remoteUiUrl } returns "https://home-assistant.io/2/"
                    every { secret } returns "ABCDE"
                    every { webhookId } returns "FGHIJ"
                }

                coEvery { authenticationRepository.buildBearerToken() } returns "ABC123"

                runBlocking {
                    repository.registerDevice(
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

            it("should save response data") {
                coVerifyAll {
                    localStorage.putString("cloud_url", "https://home-assistant.io/1/")
                    localStorage.putString("remote_ui_url", "https://home-assistant.io/2/")
                    localStorage.putString("secret", "ABCDE")
                    localStorage.putString("webhook_id", "FGHIJ")
                }
            }
        }

        describe("isRegistered") {
            beforeEachTest {
                coEvery { localStorage.getString("webhook_id") } returns "FGHIJ"
                runBlocking { repository.isRegistered() }
            }
            it("should pull the webhook id") {
                coVerify { localStorage.getString("webhook_id") }
            }
        }
    }

})