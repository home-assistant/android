package io.homeassistant.companion.android.onboarding.integration

import android.os.Build
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.domain.integration.DeviceRegistration
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe


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

        describe("on skip") {
            beforeEachTest {
                presenter.onSkip()
            }

            it("should continue") {
                coVerifyAll { view.registrationSkipped() }
            }
        }

        describe("on registration success") {
            val deviceRegistration = DeviceRegistration(
                BuildConfig.APPLICATION_ID,
                "Home Assistant",
                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                Build.MODEL ?: "UNKNOWN",
                Build.MANUFACTURER ?: "UNKNOWN",
                Build.MODEL ?: "UNKNOWN",
                "Android",
                Build.VERSION.SDK_INT.toString(),
                false,
                null
            )
            beforeEachTest {
                coEvery { integrationUseCase.registerDevice(deviceRegistration) } just runs
            }
            describe("register") {
                beforeEachTest {
                    presenter.onRegistrationAttempt()
                }
                it("should register successfully") {
                    coVerifyAll {
                        view.showLoading()
                        integrationUseCase.registerDevice(deviceRegistration)
                        view.deviceRegistered()
                    }
                }
            }
        }

        describe("on registration failed") {
            beforeEachTest {
                coEvery { integrationUseCase.registerDevice(any()) } throws Exception()
            }
            describe("register"){
                beforeEachTest {
                    presenter.onRegistrationAttempt()
                }
                it("should fail") {
                    coVerifyAll {
                        view.showLoading()
                        integrationUseCase.registerDevice(any())
                        view.showError()
                    }
                    coVerify(inverse = true) { view.deviceRegistered() }
                }
            }
        }
    }
})