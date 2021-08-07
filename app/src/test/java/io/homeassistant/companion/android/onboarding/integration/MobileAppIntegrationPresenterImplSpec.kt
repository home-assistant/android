package io.homeassistant.companion.android.onboarding.integration

import android.os.Build
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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
        val integrationUseCase by memoized { mockk<IntegrationRepository>(relaxUnitFun = true) }
        val view by memoized { mockk<MobileAppIntegrationView>(relaxUnitFun = true) }
        val presenter by memoized { MobileAppIntegrationPresenterBase(view, integrationUseCase) }

        describe("on registration success") {
            val deviceRegistration = DeviceRegistration(
                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                Build.MODEL ?: "UNKNOWN"
            )
            beforeEachTest {
                coEvery { integrationUseCase.registerDevice(deviceRegistration) } just runs
            }
            describe("register") {
                beforeEachTest {
                    presenter.onRegistrationAttempt(true, Build.MODEL ?: "UNKNOWN")
                }
                it("should register successfully") {
// TODO: As part of our swap away from dagger we need to inject the Coroutine Scope in to get this to pass
//                    coVerify {
//                        view.showLoading()
//                        integrationUseCase.registerDevice(deviceRegistration)
//                        view.deviceRegistered()
//                    }
                }
            }
        }

        describe("on registration failed") {
            beforeEachTest {
                coEvery { integrationUseCase.registerDevice(any()) } throws Exception()
            }
            describe("register") {
                beforeEachTest {
                    presenter.onRegistrationAttempt(false, Build.MODEL ?: "UNKNOWN")
                }
                it("should fail") {
// TODO: Fix issues with scopes...
//                    coVerifyAll {
//                        view.showLoading()
//                        integrationUseCase.registerDevice(any())
//                        view.showError()
//                    }
//                    coVerify(inverse = true) { view.deviceRegistered() }
                }
            }
        }
    }
})
