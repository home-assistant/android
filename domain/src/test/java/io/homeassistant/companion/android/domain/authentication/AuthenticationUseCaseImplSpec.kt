package io.homeassistant.companion.android.domain.authentication

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object AuthenticationUseCaseImplSpec : Spek({

    describe("authentication use case") {
        val authenticationRepository by memoized { mockk<AuthenticationRepository>(relaxUnitFun = true) }
        val useCase by memoized { AuthenticationUseCaseImpl(authenticationRepository) }

        describe("register authorization code") {
            beforeEachTest {
                runBlocking { useCase.registerAuthorizationCode("123456") }
            }

            it("should call the repository") {
                coVerify { authenticationRepository.registerAuthorizationCode("123456") }
            }
        }

        describe("retrieve external authentication") {
            lateinit var externalAuth: String
            beforeEachTest {
                coEvery { authenticationRepository.retrieveExternalAuthentication(false) } returns "{\"access_token\":\"ABCDEFGH\",\"expires_in\":1800}"
                externalAuth = runBlocking { useCase.retrieveExternalAuthentication() }
            }

            it("should return the repository value") {
                assertThat(externalAuth).isEqualTo("{\"access_token\":\"ABCDEFGH\",\"expires_in\":1800}")
            }
        }

        describe("revoke session") {
            beforeEachTest {
                runBlocking { useCase.revokeSession() }
            }

            it("should call the repository") {
                coVerify { authenticationRepository.revokeSession() }
            }
        }

        describe("session state") {
            lateinit var sessionState: SessionState
            beforeEachTest {
                coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
                sessionState = runBlocking { useCase.getSessionState() }
            }

            it("should return the given SessionState") {
                assertThat(sessionState).isEqualTo(SessionState.CONNECTED)
            }
        }

        describe("build authentication url") {
            lateinit var url: URL
            beforeEachTest {
                coEvery {
                    authenticationRepository.buildAuthenticationUrl("homeassistant://auth-callback")
                } returns URL("https://demo.home-assistant.io/auth/authorize?response_type=code&client_id=https://home-assistant.io/android&redirect_uri=homeassistant://auth-callback")
                url = runBlocking { useCase.buildAuthenticationUrl("homeassistant://auth-callback") }
            }

            it("should return the given url") {
                assertThat(url).isEqualTo(URL("https://demo.home-assistant.io/auth/authorize?response_type=code&client_id=https://home-assistant.io/android&redirect_uri=homeassistant://auth-callback"))
            }
        }
    }
})
