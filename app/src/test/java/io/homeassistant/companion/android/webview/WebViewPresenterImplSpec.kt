package io.homeassistant.companion.android.webview

import android.net.Uri
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.URL


object WebViewPresenterImplSpec : Spek({

    beforeEachTest {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    afterEachTest {
        Dispatchers.resetMain()
    }

    describe("presenter") {
        val authenticationUseCase by memoized { mockk<AuthenticationUseCase>(relaxUnitFun = true) }
        val view by memoized { mockk<WebView>(relaxUnitFun = true) }
        val presenter by memoized { WebViewPresenterImpl(view, authenticationUseCase) }

        describe("on view ready empty query ") {
            beforeEachTest {
                coEvery { authenticationUseCase.getUrl() } returns URL("https://demo.home-assistant.io/")
                mockkStatic(Uri::class)
                every { Uri.parse("https://demo.home-assistant.io/") } returns mockk {
                    every { buildUpon().appendQueryParameter("external_auth", "1").build().toString() } returns "https://demo.home-assistant.io?external_auth=1"
                }

                presenter.onViewReady()
            }

            it("should load the url") {
                verify { view.loadUrl("https://demo.home-assistant.io?external_auth=1") }
            }
        }

        describe("on get external auth on success") {
            beforeEachTest {
                coEvery { authenticationUseCase.retrieveExternalAuthentication() } returns "{\"access_token\":\"ABCDEFGH\",\"expires_in\":1800}"
                presenter.onGetExternalAuth("externalAuthSetToken")
            }

            it("should set external auth") {
                verify { view.setExternalAuth("externalAuthSetToken", "{\"access_token\":\"ABCDEFGH\",\"expires_in\":1800}") }
            }
        }

        describe("on get external auth on error") {
            beforeEachTest {
                coEvery { authenticationUseCase.retrieveExternalAuthentication() } throws Exception()
                presenter.onGetExternalAuth("externalAuthSetToken")
            }

            it("should not crash") {
                coVerify {
                    view wasNot Called
                }
            }
        }
    }
})