package io.homeassistant.companion.android.data.authentication

import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.domain.authentication.SessionState
import io.homeassistant.companion.android.domain.url.UrlRepository
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.threeten.bp.Instant

object AuthenticationRepositoryImplSpec : Spek({

    beforeEachTest {
        mockkStatic(Instant::class)
        every { Instant.now() } returns Instant.parse("2019-01-16T01:52:00.000Z")
    }

    describe("a repository") {
        val localStorage by memoized { mockk<LocalStorage>(relaxUnitFun = true) }
        val authenticationService by memoized { mockk<AuthenticationService>(relaxUnitFun = true) }
        val urlRepository by memoized { mockk<UrlRepository>(relaxed = true) }
        val repository by memoized {
            AuthenticationRepositoryImpl(
                authenticationService,
                localStorage,
                urlRepository
            )
        }

        describe("get token on success") {
            beforeEachTest {
                coEvery {
                    authenticationService.getToken(
                        "authorization_code",
                        "123456",
                        "https://home-assistant.io/android"
                    )
                } returns mockk {
                    every { accessToken } returns "ABCDEFGH"
                    every { expiresIn } returns 1800
                    every { refreshToken } returns "IJKLMNOPQRST"
                    every { tokenType } returns "Bearer"
                }
            }

            describe("register authorization code") {
                beforeEachTest {
                    runBlocking {
                        repository.registerAuthorizationCode("123456")
                    }
                }

                it("should save the token and url") {
                    coVerifyAll {
                        localStorage.putString("access_token", "ABCDEFGH")
                        localStorage.putLong("expires_date", 1547605320)
                        localStorage.putString("refresh_token", "IJKLMNOPQRST")
                        localStorage.putString("token_type", "Bearer")
                    }
                }
            }
        }

        describe("get token on error") {
            beforeEachTest {
                coEvery {
                    authenticationService.getToken(any(), any(), any())
                } throws Exception()
            }

            describe("register authorization code") {
                beforeEachTest {
                    catchThrowable {
                        runBlocking {
                            repository.registerAuthorizationCode("123456")
                        }
                    }
                }

                it("shouldn't save data") {
                    verify { localStorage wasNot Called }
                }
            }
        }

        describe("build auth url") {
            lateinit var authenticationUrl: URL
            beforeEachTest {
                coEvery { urlRepository.getUrl() } returns URL("https://demo.home-assistant.io/")
                authenticationUrl =
                    runBlocking { repository.buildAuthenticationUrl("homeassistant://auth-callback") }
            }

            it("should return the authentication url") {
                assertThat(authenticationUrl).isEqualTo(URL("https://demo.home-assistant.io/auth/authorize?response_type=code&client_id=https://home-assistant.io/android&redirect_uri=homeassistant://auth-callback"))
            }
        }

        describe("connected user with valid access token") {
            beforeEachTest {
                coEvery { urlRepository.getUrl() } returns URL("https://demo.home-assistant.io/")
                coEvery { localStorage.getString("access_token") } returns "ABCDEFGH"
                coEvery { localStorage.getLong("expires_date") } returns 1547605320
                coEvery { localStorage.getString("refresh_token") } returns "IJKLMNOPQRST"
                coEvery { localStorage.getString("token_type") } returns "Bearer"
            }

            describe("get session state") {
                lateinit var sessionState: SessionState
                beforeEachTest {
                    sessionState = runBlocking {
                        repository.getSessionState()
                    }
                }

                it("should be connected") {
                    assertThat(sessionState).isEqualTo(SessionState.CONNECTED)
                }
            }

            describe("retrieve external authentication") {
                lateinit var externalAuth: String
                beforeEachTest {
                    externalAuth = runBlocking {
                        repository.retrieveExternalAuthentication(false)
                    }
                }

                it("should serialize the external authentication saved") {
                    assertThat(externalAuth).isEqualTo("{\"access_token\":\"ABCDEFGH\",\"expires_in\":1800}")
                }
            }

            describe("revoke session") {
                beforeEachTest {
                    runBlocking {
                        repository.revokeSession()
                    }
                }

                it("should call the service") {
                    coVerify {
                        authenticationService.revokeToken("IJKLMNOPQRST", "revoke")
                        urlRepository.saveUrl("", true)
                        urlRepository.saveUrl("", false)
                        urlRepository.saveHomeWifiSsids(emptySet())
                    }
                }

                it("should delete the session") {
                    coVerify {
                        localStorage.putString("access_token", null)
                        localStorage.putLong("expires_date", null)
                        localStorage.putString("refresh_token", null)
                        localStorage.putString("token_type", null)
                    }
                }
            }

            describe("build bearer token") {
                lateinit var token: String
                beforeEachTest {
                    token = runBlocking { repository.buildBearerToken() }
                }
                it("should return a valid bearer token") {
                    assertThat(token).isEqualTo("Bearer ABCDEFGH")
                }
            }
        }

        describe("connected user with expired access token") {
            beforeEachTest {
                coEvery { localStorage.getString("url") } returns "https://demo.home-assistant.io/"
                coEvery { localStorage.getString("access_token") } returns "ABCDEFGH"
                coEvery { localStorage.getLong("expires_date") } returns Instant.now().epochSecond - 1800
                coEvery { localStorage.getString("refresh_token") } returns "IJKLMNOPQRST"
                coEvery { localStorage.getString("token_type") } returns "Bearer"
                coEvery {
                    authenticationService.refreshToken(
                        "refresh_token",
                        "IJKLMNOPQRST",
                        "https://home-assistant.io/android"
                    )
                } returns mockk {
                    every { isSuccessful } returns true
                    every { body() } returns mockk {
                        every { accessToken } returns "HGFEDCBA"
                        every { expiresIn } returns 1800
                        every { refreshToken } returns null
                        every { tokenType } returns "Bearer"
                    }
                }
            }

            describe("retrieve external authentication") {
                lateinit var externalAuth: String
                beforeEachTest {
                    externalAuth = runBlocking {
                        repository.retrieveExternalAuthentication(false)
                    }
                }

                it("should save the token") {
                    coVerify {
                        localStorage.putString("access_token", "HGFEDCBA")
                        localStorage.putLong("expires_date", 1547605320)
                        localStorage.putString("refresh_token", "IJKLMNOPQRST")
                        localStorage.putString("token_type", "Bearer")
                    }
                }

                it("should serialize the refresh external authentication") {
                    assertThat(externalAuth).isEqualTo("{\"access_token\":\"HGFEDCBA\",\"expires_in\":1800}")
                }
            }

            describe("build bearer token") {
                lateinit var token: String
                beforeEachTest {
                    token = runBlocking { repository.buildBearerToken() }
                }
                it("should refresh the token") {
                    coVerify {
                        authenticationService.refreshToken(
                            "refresh_token",
                            "IJKLMNOPQRST",
                            "https://home-assistant.io/android"
                        )
                    }
                }

                it("should return a valid bearer token") {
                    assertThat(token).isEqualTo("Bearer HGFEDCBA")
                }
            }
        }

        describe("anonymous user") {
            beforeEachTest {
                coEvery { localStorage.getString("url") } returns null
                coEvery { localStorage.getString("access_token") } returns null
                coEvery { localStorage.getLong("expires_date") } returns null
                coEvery { localStorage.getString("refresh_token") } returns null
                coEvery { localStorage.getString("token_type") } returns null
            }

            describe("get session state") {
                lateinit var sessionState: SessionState
                beforeEachTest {
                    sessionState = runBlocking {
                        repository.getSessionState()
                    }
                }

                it("should be anonymous") {
                    assertThat(sessionState).isEqualTo(SessionState.ANONYMOUS)
                }
            }

            describe("retrieve external authentication") {
                lateinit var thrown: Throwable
                beforeEachTest {
                    thrown = catchThrowable {
                        runBlocking {
                            repository.retrieveExternalAuthentication(false)
                        }
                    }
                }

                it("throw an exception") {
                    assertThat(thrown).isInstanceOf(AuthorizationException::class.java)
                }
            }

            describe("revoke session") {
                lateinit var thrown: Throwable
                beforeEachTest {
                    thrown = catchThrowable {
                        runBlocking {
                            repository.revokeSession()
                        }
                    }
                }

                it("should throw an exception") {
                    assertThat(thrown).isInstanceOf(AuthorizationException::class.java)
                }
            }

            describe("build bearer token") {
                lateinit var thrown: Throwable
                beforeEachTest {
                    thrown = catchThrowable {
                        runBlocking {
                            repository.buildBearerToken()
                        }
                    }
                }
                it("should throw an exception") {
                    assertThat(thrown).isInstanceOf(AuthorizationException::class.java)
                }
            }
        }
    }
})
