package io.homeassistant.companion.android.common.data.servers

import com.sun.net.httpserver.HttpServer
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import io.homeassistant.companion.android.common.data.network.NetworkChangeObserver
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UrlReachabilityCheckerImplTest {

    private data class RecordedRequest(val method: String, val path: String, val cookie: String?)

    private val homeAssistantApis: HomeAssistantApis = mockk()
    private val networkChanges = MutableSharedFlow<Unit>()
    private val networkChangeObserver: NetworkChangeObserver = mockk {
        every { observerNetworkChange } returns networkChanges
    }

    private lateinit var server: HttpServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var responseStatus = 200
    private var responseHeaders = emptyMap<String, String>()

    /** When set, the server holds every request until the latch is released. */
    private var holdRequests: CountDownLatch? = null

    /** A jar standing in for the WebView cookie jar of the shared client, which always has a session cookie. */
    private val sessionCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return listOf(Cookie.Builder().name("session").value("secret").hostOnlyDomain(url.host).build())
        }
    }

    private val url: HttpUrl get() = "http://127.0.0.1:${server.address.port}/".toHttpUrl()

    @BeforeEach
    fun setup() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            requests += RecordedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                cookie = exchange.requestHeaders.getFirst("Cookie"),
            )
            holdRequests?.await(5, TimeUnit.SECONDS)
            responseHeaders.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
            exchange.sendResponseHeaders(responseStatus, -1)
            exchange.close()
        }
        server.start()
        coEvery { homeAssistantApis.getOkHttpClient() } returns OkHttpClient.Builder()
            .cookieJar(sessionCookieJar)
            .build()
    }

    @AfterEach
    fun tearDown() {
        holdRequests?.countDown()
        server.stop(0)
    }

    private fun createChecker(scope: CoroutineScope): UrlReachabilityCheckerImpl {
        return UrlReachabilityCheckerImpl(homeAssistantApis, networkChangeObserver, scope, ReachabilityCache())
    }

    @Test
    fun `Given a server that answers when probing then it is reachable and a HEAD request was sent`() = runTest {
        val checker = createChecker(backgroundScope)

        assertTrue(checker.isReachable(url))

        assertEquals(listOf("HEAD"), requests.map { it.method })
    }

    @Test
    fun `Given an error response when probing then the URL is still reachable`() = runTest {
        responseStatus = 503
        val checker = createChecker(backgroundScope)

        assertTrue(checker.isReachable(url))
    }

    @Test
    fun `Given a redirect when probing then it is not followed`() = runTest {
        responseStatus = 302
        responseHeaders = mapOf("Location" to "http://127.0.0.1:${server.address.port}/elsewhere")
        val checker = createChecker(backgroundScope)

        assertTrue(checker.isReachable(url))

        assertEquals(listOf("/"), requests.map { it.path })
    }

    @Test
    fun `Given a cookie jar on the shared client when probing then no cookie is sent`() = runTest {
        val checker = createChecker(backgroundScope)

        assertTrue(checker.isReachable(url))

        assertNull(requests.single().cookie)
    }

    @Test
    fun `Given nothing listening when probing then the URL is not reachable`() = runTest {
        val closedUrl = url
        server.stop(0)
        val checker = createChecker(backgroundScope)

        assertFalse(checker.isReachable(closedUrl))
    }

    @Test
    fun `Given a recent result when probing again then the server is not asked again`() = runTest {
        val checker = createChecker(backgroundScope)

        assertTrue(checker.isReachable(url))
        assertTrue(checker.isReachable(url))

        assertEquals(1, requests.size)
    }

    @Test
    fun `Given a network change when probing again then the server is asked again`() = runTest(UnconfinedTestDispatcher()) {
        val checker = createChecker(backgroundScope)
        assertTrue(checker.isReachable(url))

        networkChanges.emit(Unit)
        assertTrue(checker.isReachable(url))

        assertEquals(2, requests.size)
    }

    @Test
    fun `Given a probe in progress when its coroutine is cancelled then the call is cancelled right away`() = runTest {
        holdRequests = CountDownLatch(1)
        val checker = createChecker(backgroundScope)
        val probe = launch(Dispatchers.IO) { checker.isReachable(url) }
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + 5_000
            while (requests.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        }
        assertTrue(requests.isNotEmpty())

        val elapsedMs = measureTimeMillis { probe.cancelAndJoin() }

        assertTrue(probe.isCancelled)
        assertTrue(elapsedMs < 2_000, "Cancelling took ${elapsedMs}ms")
    }
}
