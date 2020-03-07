package io.homeassistant.companion.android.launch

import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.InstanceIdResult
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.authentication.SessionState
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object LaunchPresenterImplSpec : Spek({

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

    describe("launch presenter") {
        val authenticationUseCase by memoized { mockk<AuthenticationUseCase>() }
        val integrationUseCase by memoized { mockk<IntegrationUseCase>() }
        val view by memoized { mockk<LaunchView>(relaxUnitFun = true) }
        val presenter by memoized { LaunchPresenterImpl(view, authenticationUseCase, integrationUseCase) }

        describe("anonymous state") {
            beforeEachTest {
                coEvery { authenticationUseCase.getSessionState() } returns SessionState.ANONYMOUS
            }

            describe("on view ready") {
                beforeEachTest {
                    presenter.onViewReady()
                }

                it("should display the onboarding") {
                    verify { view.displayOnBoarding(false) }
                }
            }
        }

        describe("connected state") {
            beforeEachTest {
                coEvery { authenticationUseCase.getSessionState() } returns SessionState.CONNECTED
                coEvery { integrationUseCase.isRegistered() } returns true
                coEvery { authenticationUseCase.isLockEnabled() } returns false
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

        describe("connected state") {
            beforeEachTest {
                coEvery { authenticationUseCase.getSessionState() } returns SessionState.CONNECTED
                coEvery { integrationUseCase.isRegistered() } returns true
                coEvery { authenticationUseCase.isLockEnabled() } returns true
            }

            describe("on view ready") {
                beforeEachTest {
                    presenter.onViewReady()
                }

                it("should display the lockview") {
                    verify { view.displayLockView() }
                }
            }
        }

        describe("connected state but not integrated") {
            beforeEachTest {
                coEvery { authenticationUseCase.getSessionState() } returns SessionState.CONNECTED
                coEvery { integrationUseCase.isRegistered() } returns false
            }

            describe("on view ready") {
                beforeEachTest {
                    presenter.onViewReady()
                }

                it("should display the integration view") {
                    verify { view.displayOnBoarding(true) }
                }
            }
        }
    }
})
