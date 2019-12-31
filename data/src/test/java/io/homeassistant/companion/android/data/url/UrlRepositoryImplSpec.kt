package io.homeassistant.companion.android.data.url

import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.data.wifi.WifiHelper
import io.homeassistant.companion.android.domain.MalformedHttpUrlException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.mockk
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object UrlRepositoryImplSpec : Spek({
    describe("a repository") {
        val localStorage by memoized { mockk<LocalStorage>(relaxUnitFun = true) }
        val wifiHelper by memoized { mockk<WifiHelper>(relaxed = true) }
        val repository by memoized { UrlRepositoryImpl(localStorage, wifiHelper) }

        mapOf(
            "https://demo.home-assistant.io:8123/lovelace/0/default_view?home_assistant=1&true=false" to "https://demo.home-assistant.io:8123/",
            "https://demo.home-assistant.io/lovelace/0/default_view?home_assistant=1&true=false" to "https://demo.home-assistant.io/",
            "https://demo.home-assistant.io" to "https://demo.home-assistant.io/",
            "https://192.168.1.1:8123/lovelace/0" to "https://192.168.1.1:8123/",
            "https://192.168.1.1:8123" to "https://192.168.1.1:8123/",
            "https://192.168.1.1" to "https://192.168.1.1/"
        ).forEach {
            describe("save valid url \"${it.key}\"") {
                beforeEachTest {
                    runBlocking { repository.saveUrl(it.key, false) }
                }

                it("should save url") {
                    coVerifyAll { localStorage.putString("remote_url", it.value) }
                }
            }
        }

        listOf(
            "home assistant",
            "http://192.168..132:8123",
            "http://....:8123",
            "http://......",
            "ftp://192.168.1.1"
        ).forEach {
            describe("save invalid url \"$it\"") {
                lateinit var throwable: Throwable
                beforeEachTest {
                    runBlocking {
                        try {
                            repository.saveUrl(it, false)
                        } catch (e: Exception) {
                            throwable = e
                        }
                    }
                }

                it("shouldn't save url") {
                    coVerify(exactly = 0) {
                        localStorage.putString("remote_url", any())
                    }
                }
                it("should throw an exception") {
                    Assertions.assertThat(throwable).isInstanceOf(MalformedHttpUrlException::class.java)
                }
            }
        }

        describe("get instance url") {
            describe("remote set, get internal") {
                var url: URL? = null
                beforeEachTest {
                    coEvery { localStorage.getString("remote_url") } returns "https://demo.home-assistant.io/"
                    coEvery { localStorage.getString("local_url") } returns null
                    url = runBlocking {
                        repository.getUrl(true)
                    }
                }

                it("should return the remote url") {
                    Assertions.assertThat(url).isNull()
                }
            }
            describe("remote set, get external") {
                lateinit var url: URL
                beforeEachTest {
                    coEvery { localStorage.getString("remote_url") } returns "https://demo.home-assistant.io/"
                    coEvery { localStorage.getString("local_url") } returns null
                    url = runBlocking {
                        repository.getUrl(false)
                    }!!
                }

                it("should return the remote url") {
                    Assertions.assertThat(url).isEqualTo(URL("https://demo.home-assistant.io/"))
                }
            }
            describe("local set, get internal") {
                var url: URL? = null
                beforeEachTest {
                    coEvery { localStorage.getString("remote_url") } returns null
                    coEvery { localStorage.getString("local_url") } returns "https://demo.home-assistant.io/"
                    url = runBlocking {
                        repository.getUrl(false)
                    }
                }

                it("should return the local url") {
                    Assertions.assertThat(url).isNull()
                }
            }
            describe("local set, get external") {
                lateinit var url: URL
                beforeEachTest {
                    coEvery { localStorage.getString("remote_url") } returns null
                    coEvery { localStorage.getString("local_url") } returns "https://demo.home-assistant.io/"
                    url = runBlocking {
                        repository.getUrl(true)
                    }!!
                }

                it("should return the local url") {
                    Assertions.assertThat(url).isEqualTo(URL("https://demo.home-assistant.io/"))
                }
            }
            describe("both set, get internal") {
                lateinit var url: URL
                beforeEachTest {
                    coEvery { localStorage.getString("remote_url") } returns "https://demo2.home-assistant.io/"
                    coEvery { localStorage.getString("local_url") } returns "https://demo.home-assistant.io/"
                    url = runBlocking {
                        repository.getUrl(true)
                    }!!
                }

                it("should return the local url") {
                    Assertions.assertThat(url).isEqualTo(URL("https://demo.home-assistant.io/"))
                }
            }
            describe("both set, get external") {
                lateinit var url: URL
                beforeEachTest {
                    coEvery { localStorage.getString("remote_url") } returns "https://demo2.home-assistant.io/"
                    coEvery { localStorage.getString("local_url") } returns "https://demo.home-assistant.io/"
                    url = runBlocking {
                        repository.getUrl(false)
                    }!!
                }

                it("should return the local url") {
                    Assertions.assertThat(url).isEqualTo(URL("https://demo2.home-assistant.io/"))
                }
            }
        }
    }
})
