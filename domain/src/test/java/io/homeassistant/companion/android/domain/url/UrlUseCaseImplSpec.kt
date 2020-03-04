package io.homeassistant.companion.android.domain.url

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object UrlUseCaseImplSpec : Spek({
    describe("integration use case") {

        val urlRepository by memoized {
            mockk<UrlRepository>(
                relaxed = true,
                relaxUnitFun = true
            )
        }
        val useCase by memoized { UrlUseCaseImpl(urlRepository) }

        describe("getApiUrls") {
            beforeEachTest {
                runBlocking {
                    useCase.getApiUrls()
                }
            }

            it("should call repository") {
                coVerify {
                    urlRepository.getApiUrls()
                }
            }
        }

        describe("saveRegistrationUrls") {
            beforeEachTest {
                coEvery {
                    urlRepository.saveRegistrationUrls("1", "2", "3")
                } just Runs

                runBlocking {
                    useCase.saveRegistrationUrls("1", "2", "3")
                }
            }

            it("should call repository") {
                coVerify {
                    urlRepository.saveRegistrationUrls("1", "2", "3")
                }
            }
        }

        describe("getUrl") {
            beforeEachTest {
                runBlocking { useCase.getUrl(true) }
            }

            it("should call the repository") {
                coVerify { urlRepository.getUrl(true) }
            }
        }

        describe("getUrl") {
            beforeEachTest {
                runBlocking { useCase.getUrl(false) }
            }

            it("should call the repository") {
                coVerify { urlRepository.getUrl(false) }
            }
        }

        describe("saveUrl") {
            beforeEachTest {
                runBlocking { useCase.saveUrl("1", true) }
            }

            it("should call the repository") {
                coVerify { urlRepository.saveUrl("1", true) }
            }
        }

        describe("getHomeWifiSsids") {
            beforeEachTest {
                runBlocking { useCase.getHomeWifiSsids() }
            }

            it("should call the repository") {
                coVerify { urlRepository.getHomeWifiSsids() }
            }
        }

        describe("saveHomeWifiSsid") {
            beforeEachTest {
                runBlocking { useCase.saveHomeWifiSsids(setOf("1", "2")) }
            }

            it("should call the repository") {
                coVerify { urlRepository.saveHomeWifiSsids(setOf("1", "2")) }
            }
        }
    }
})
