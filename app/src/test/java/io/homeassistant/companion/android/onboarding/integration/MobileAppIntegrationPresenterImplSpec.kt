package io.homeassistant.companion.android.onboarding.integration

import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.lang.Exception


object MobileAppIntegrationPresenterImplSpec : Spek({

    beforeEachTest {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    afterEachTest {
        Dispatchers.resetMain()
    }

    describe("presenter") {
        val integrationUseCase by memoized { mockk<IntegrationUseCase>(relaxUnitFun = true) }
        val view by memoized { mockk<MobileAppIntegrationView>(relaxUnitFun = true) }
        val presenter by memoized { MobileAppIntegrationPresenterImpl(view, integrationUseCase) }

        describe("on click skip") {
            beforeEachTest {
                presenter.onSkip()
            }

            it("should continue") {
                coVerifyAll { view.registrationSkipped() }
            }
        }

        describe("on click retry") {

            it("should try to register and succeed") {
                coEvery {integrationUseCase.registerDevice(any())} just runs

                presenter.onRegistrationAttempt()

                coVerifyAll {
                    view.showLoading()
                    integrationUseCase.registerDevice(any())
                    view.deviceRegistered()
                }
            }

            it("should succeed and fail"){
                coEvery {integrationUseCase.registerDevice(any())} throws Exception()
                presenter.onRegistrationAttempt()
                coVerifyAll {
                    view.showLoading()
                    integrationUseCase.registerDevice(any())
                    view.showError()
                }
                coVerify(inverse = true) { view.deviceRegistered() }
            }
        }
    }

})