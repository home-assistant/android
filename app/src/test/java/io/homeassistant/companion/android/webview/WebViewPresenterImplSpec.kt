package io.homeassistant.companion.android.webview

import android.net.Uri
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.url.UrlUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.verify
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@ExperimentalCoroutinesApi
object WebViewPresenterImplSpec : Spek({
    beforeEachTest {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    afterEachTest {
        Dispatchers.resetMain()
    }

    describe("presenter") {
        val urlUseCase by memoized { mockk<UrlUseCase>(relaxUnitFun = true) }
        val authenticationUseCase by memoized { mockk<AuthenticationUseCase>(relaxUnitFun = true) }
        val integrationUseCase by memoized { mockk<IntegrationUseCase>(relaxUnitFun = true) }
        val view by memoized { mockk<WebView>(relaxUnitFun = true) }
        val presenter by memoized { WebViewPresenterImpl(view, urlUseCase, authenticationUseCase, integrationUseCase) }

        describe("on view ready empty query ") {
            beforeEachTest {
                coEvery { urlUseCase.getUrl() } returns URL("https://demo.home-assistant.io/")
                mockkStatic(Uri::class)
                every { Uri.parse("https://demo.home-assistant.io/") } returns mockk {
                    every {
                        buildUpon().appendQueryParameter("external_auth", "1").build().toString()
                    } returns "https://demo.home-assistant.io?external_auth=1"
                }

                presenter.onViewReady(null)
            }

            it("should load the url") {
                verify { view.loadUrl("https://demo.home-assistant.io?external_auth=1") }
            }
        }

        describe("on get external auth on success") {
            beforeEachTest {
                coEvery { authenticationUseCase.retrieveExternalAuthentication() } returns "{\"access_token\":\"ABCDEFGH\",\"expires_in\":1800}"
                presenter.onGetExternalAuth("externalAuthSetToken", false)
            }

            it("should set external auth") {
                verify { view.setExternalAuth("externalAuthSetToken(true, {\"access_token\":\"ABCDEFGH\",\"expires_in\":1800})") }
            }
        }

        describe("on get external auth on error") {
            beforeEachTest {
                coEvery { authenticationUseCase.retrieveExternalAuthentication() } throws Exception()
                presenter.onGetExternalAuth("externalAuthSetToken", false)
            }

            it("should not crash") {
                verify { view.setExternalAuth("externalAuthSetToken(false)") }
            }
        }

        describe("on revoke external auth on success") {
            beforeEachTest {
                coEvery { authenticationUseCase.revokeSession() } just runs
                presenter.onRevokeExternalAuth("externalAuthRevokeToken")
            }

            it("should set external auth") {
                verify { view.setExternalAuth("externalAuthRevokeToken(true)") }
                verify { view.openOnBoarding() }
            }
        }

        describe("on revoke external auth on error") {
            beforeEachTest {
                coEvery { authenticationUseCase.revokeSession() } throws Exception()
                presenter.onRevokeExternalAuth("externalAuthRevokeToken")
            }

            it("should set external auth") {
                verify { view.setExternalAuth("externalAuthRevokeToken(false)") }
            }
        }
    }
})
