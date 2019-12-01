package io.homeassistant.companion.android.onboarding.authentication

import android.net.Uri
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object AuthenticationPresenterImplSpec : Spek({

    beforeEachTest {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    afterEachTest {
        Dispatchers.resetMain()
    }

    describe("authentication presenter") {
        val authenticationUseCase by memoized { mockk<AuthenticationUseCase>(relaxUnitFun = true) }
        val view by memoized { mockk<AuthenticationView>(relaxUnitFun = true) }
        val presenter by memoized { AuthenticationPresenterImpl(view, authenticationUseCase) }

        describe("on view ready") {
            beforeEachTest {
                coEvery {
                    authenticationUseCase.buildAuthenticationUrl("homeassistant://auth-callback")
                } returns URL("https://demo.home-assistant.io/auth/authorize?response_type=code&client_id=https://home-assistant.io/android&redirect_uri=homeassistant://auth-callback")
                presenter.onViewReady()
            }

            it("should load auth url") {
                verify { view.loadUrl("https://demo.home-assistant.io/auth/authorize?response_type=code&client_id=https://home-assistant.io/android&redirect_uri=homeassistant://auth-callback") }
            }
        }

        describe("on redirect url with callback url") {
            var allowRedirect: Boolean? = null
            beforeEachTest {
                mockkStatic(Uri::class)
                every { Uri.parse("homeassistant://auth-callback?code=123456") } returns mockk {
                    every { getQueryParameter("code") } returns "123456"
                }
                allowRedirect = presenter.onRedirectUrl("homeassistant://auth-callback?code=123456")
            }

            it("should open the webview") {
                coVerify { authenticationUseCase.registerAuthorizationCode("123456") }
                verify { view.openWebview() }
                assertThat(allowRedirect).isTrue()
            }
        }

        describe("on redirect wrong url") {
            var allowRedirect: Boolean? = null
            beforeEachTest {
                mockkStatic(Uri::class)
                every { Uri.parse("homeassistant://auth-callback") } returns mockk {
                    every { getQueryParameter("code") } returns null
                }
                allowRedirect = presenter.onRedirectUrl("homeassistant://auth-callback")
            }

            it("should not open the webview") {
                coVerify { authenticationUseCase wasNot Called }
                verify { view wasNot Called }
                assertThat(allowRedirect).isFalse()
            }
        }
    }
})
