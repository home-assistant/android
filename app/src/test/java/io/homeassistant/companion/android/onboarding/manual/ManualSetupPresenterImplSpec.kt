package io.homeassistant.companion.android.onboarding.manual

import io.homeassistant.companion.android.domain.MalformedHttpUrlException
import io.homeassistant.companion.android.domain.url.UrlUseCase
import io.mockk.coEvery
import io.mockk.coVerifyAll
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
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
        val urlUseCase by memoized { mockk<UrlUseCase>(relaxUnitFun = true) }
        val view by memoized { mockk<ManualSetupView>(relaxUnitFun = true) }
        val presenter by memoized { ManualSetupPresenterImpl(view, urlUseCase) }

        describe("on click ok with valid url") {
            beforeEachTest {
                presenter.onClickOk("https://demo.home-assistant.io:8123/lovelace/default_view?home_assistant=1&true=false")
                coEvery { urlUseCase.saveUrl("https://demo.home-assistant.io:8123/lovelace/default_view?home_assistant=1&true=false") } just runs
            }

            it("should save the url") {
                coVerifyAll { urlUseCase.saveUrl("https://demo.home-assistant.io:8123/lovelace/default_view?home_assistant=1&true=false", false) }
            }

            it("should notify the listener") {
                verify { view.urlSaved() }
            }
        }

        describe("on click with invalid url") {
            beforeEachTest {
                coEvery { urlUseCase.saveUrl("home assistant", false) } throws MalformedHttpUrlException()
                presenter.onClickOk("home assistant")
            }

            it("should save the url") {
                coVerifyAll { urlUseCase.saveUrl("home assistant", false) }
            }

            it("should display url error") {
                verify { view.displayUrlError() }
            }
        }
    }
})
