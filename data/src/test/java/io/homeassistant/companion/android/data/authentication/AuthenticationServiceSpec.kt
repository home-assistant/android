package io.homeassistant.companion.android.data.authentication

import io.homeassistant.companion.android.data.HomeAssistantMockService
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object AuthenticationServiceSpec : Spek({

    describe("an authentication service") {
        val mockService by memoized { HomeAssistantMockService(AuthenticationService::class.java) }

        describe("authorization code on success") {
            lateinit var token: Token
            lateinit var request: RecordedRequest
            beforeEachTest {
                mockService.enqueueResponse(200, "authentication/authorization_code.json")
                token = runBlocking {
                    mockService.get().getToken(
                        AuthenticationService.GRANT_TYPE_CODE,
                        "12345",
                        AuthenticationService.CLIENT_ID
                    )
                }
                request = mockService.takeRequest()
            }

            it("should create a query") {
                assertThat(request.method).isEqualTo("POST")
                assertThat(request.path).isEqualTo("/auth/token")
                assertThat(request.body.readUtf8())
                    .contains("grant_type=authorization_code")
                    .contains("code=12345")
                    .contains("client_id=https%3A%2F%2Fhome-assistant.io%2Fandroid")
            }

            it("should deserialize the payload") {
                assertThat(token.accessToken).isEqualTo("ABCDEFGH")
                assertThat(token.expiresIn).isEqualTo(1800)
                assertThat(token.refreshToken).isEqualTo("IJKLMNOPQRST")
                assertThat(token.tokenType).isEqualTo("Bearer")
            }
        }

        describe("refresh token on success") {
            lateinit var token: Token
            lateinit var request: RecordedRequest
            beforeEachTest {
                mockService.enqueueResponse(200, "authentication/refresh_token.json")
                token = runBlocking {
                    mockService.get().refreshToken(
                        AuthenticationService.GRANT_TYPE_REFRESH,
                        "IJKLMNOPQRST",
                        AuthenticationService.CLIENT_ID
                    ).body()!!
                }
                request = mockService.takeRequest()
            }

            it("should create a query") {
                assertThat(request.method).isEqualTo("POST")
                assertThat(request.path).isEqualTo("/auth/token")
                assertThat(request.body.readUtf8())
                    .contains("grant_type=refresh_token")
                    .contains("refresh_token=IJKLMNOPQRST")
                    .contains("client_id=https%3A%2F%2Fhome-assistant.io%2Fandroid")
            }

            it("should deserialize the payload") {
                assertThat(token.accessToken).isEqualTo("ABCDEFGH")
                assertThat(token.expiresIn).isEqualTo(1800)
                assertThat(token.refreshToken).isNull()
                assertThat(token.tokenType).isEqualTo("Bearer")
            }
        }

        describe("refresh token on failure") {
            lateinit var errorBody: String
            lateinit var request: RecordedRequest
            beforeEachTest {
                mockService.enqueueResponse(400, "authentication/refresh_token_error.json")
                errorBody = runBlocking {
                    mockService.get().refreshToken(
                        AuthenticationService.GRANT_TYPE_REFRESH,
                        "IJKLMNOPQRST",
                        AuthenticationService.CLIENT_ID
                    ).errorBody()?.string()!!
                }
                request = mockService.takeRequest()
            }

            it("should create a query") {
                assertThat(request.method).isEqualTo("POST")
                assertThat(request.path).isEqualTo("/auth/token")
                assertThat(request.body.readUtf8())
                    .contains("grant_type=refresh_token")
                    .contains("refresh_token=IJKLMNOPQRST")
                    .contains("client_id=https%3A%2F%2Fhome-assistant.io%2Fandroid")
            }

            it("should contain error") {
                assertThat(errorBody).contains("\"error\": \"invalid_grant\"")
            }
        }

        describe("revoke token on success") {
            lateinit var request: RecordedRequest
            beforeEachTest {
                mockService.enqueueResponse(200)
                runBlocking {
                    mockService.get()
                        .revokeToken("IJKLMNOPQRST", AuthenticationService.REVOKE_ACTION)
                }
                request = mockService.takeRequest()
            }

            it("should create a query") {
                assertThat(request.method).isEqualTo("POST")
                assertThat(request.path).isEqualTo("/auth/token")
                assertThat(request.body.readUtf8())
                    .contains("token=IJKLMNOPQRST")
                    .contains("action=revoke")
            }
        }
    }
})
