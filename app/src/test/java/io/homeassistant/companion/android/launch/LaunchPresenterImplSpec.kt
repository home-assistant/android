package io.homeassistant.companion.android.launch

import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.authentication.SessionState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe


object LaunchPresenterImplSpec : Spek({

    beforeEachTest {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    afterEachTest {
        Dispatchers.resetMain()
    }

    describe("launch presenter") {
        val authenticationUseCase by memoized { mockk<AuthenticationUseCase>() }
        val view by memoized { mockk<LaunchView>(relaxUnitFun = true) }
        val presenter by memoized { LaunchPresenterImpl(view, authenticationUseCase) }

        describe("anonymous state") {
            beforeEachTest {
                coEvery { authenticationUseCase.getSessionState() } returns SessionState.ANONYMOUS
            }

            describe("on view ready") {
                beforeEachTest {
                    presenter.onViewReady()
                }

                it("should display the onboarding") {
                    verify { view.displayOnBoarding() }
                }
            }
        }

        describe("connected state") {
            beforeEachTest {
                coEvery { authenticationUseCase.getSessionState() } returns SessionState.CONNECTED
            }

            describe("on view ready") {
                beforeEachTest {
                    presenter.onViewReady()
                }

                it("should display the webview") {
                    verify { view.displayWebview() }
                }
            }
        }
    }
})