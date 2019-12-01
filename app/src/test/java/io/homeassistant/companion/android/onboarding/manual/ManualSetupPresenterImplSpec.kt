package io.homeassistant.companion.android.onboarding.manual

import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.mockk.Called
import io.mockk.coVerifyAll
import io.mockk.mockk
import io.mockk.verify
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ManualSetupPresenterImplSpec : Spek({

    beforeEachTest {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    afterEachTest {
        Dispatchers.resetMain()
    }

    describe("presenter") {
        val authenticationUseCase by memoized { mockk<AuthenticationUseCase>(relaxUnitFun = true) }
        val view by memoized { mockk<ManualSetupView>(relaxUnitFun = true) }
        val presenter by memoized { ManualSetupPresenterImpl(view, authenticationUseCase) }

        describe("on click ok with valid url") {
            beforeEachTest {
                presenter.onClickOk("https://demo.home-assistant.io:8123/lovelace/default_view?home_assistant=1&true=false")
            }

            it("should save the url") {
                coVerifyAll { authenticationUseCase.saveUrl(URL("https://demo.home-assistant.io:8123")) }
            }

            it("should notify the listener") {
                verify { view.urlSaved() }
            }
        }

        describe("on click with invalid url") {
            beforeEachTest {
                presenter.onClickOk("home assistant")
            }

            it("should not save the url") {
                coVerifyAll { authenticationUseCase wasNot Called }
            }

            it("shouldn't notify the listener") {
                verify(exactly = 0) { view.urlSaved() }
            }
            it("should display url error") {
                verify { view.displayUrlError() }
            }
        }
    }
})
