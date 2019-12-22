package io.homeassistant.companion.android.onboarding.integration

import android.os.Build
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.InstanceIdResult
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.domain.integration.DeviceRegistration
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object MobileAppIntegrationPresenterImplSpec : Spek({

    beforeEachTest {
        Dispatchers.setMain(Dispatchers.Unconfined)

        val onSuccessListener = slot<OnSuccessListener<InstanceIdResult>>()
        val mockResults = mockk<InstanceIdResult> {
            every { token } returns "ABC123"
        }

        mockkStatic(FirebaseInstanceId::class)
        every { FirebaseInstanceId.getInstance() } returns mockk {
            every { instanceId } returns mockk {
                every { addOnSuccessListener(capture(onSuccessListener)) } answers {
                    onSuccessListener.captured.onSuccess(mockResults)

                    mockk {
                        every { result } returns mockResults
                    }
                }
                every { addOnFailureListener(any()) } returns mockk {
                    every { exception } returns Exception()
                }
            }
        }
    }

    afterEachTest {
        Dispatchers.resetMain()
    }

    describe("presenter") {
        val integrationUseCase by memoized { mockk<IntegrationUseCase>(relaxUnitFun = true) }
        val view by memoized { mockk<MobileAppIntegrationView>(relaxUnitFun = true) }
        val presenter by memoized { MobileAppIntegrationPresenterImpl(view, integrationUseCase) }

        describe("on registration success") {
            val deviceRegistration = DeviceRegistration(
                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                Build.MODEL ?: "UNKNOWN",
                "ABC123"
            )
            beforeEachTest {
                coEvery { integrationUseCase.registerDevice(deviceRegistration) } just runs
            }
            describe("register") {
                beforeEachTest {
                    presenter.onRegistrationAttempt()
                }
                it("should register successfully") {
                    coVerify {
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
            describe("register") {
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
