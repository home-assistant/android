package io.homeassistant.companion.android.util.compose.webview

import android.net.Uri
import android.webkit.WebBackForwardList
import android.webkit.WebHistoryItem
import android.webkit.WebView
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WebViewBackNavigationTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers { createMockUri(firstArg()) }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `Given no history and root loaded url when resolving back action then returns None`() {
        val action = resolveBackAction(webViewWithoutHistory(), Uri.parse("https://ha.local:8123/?external_auth=1"))

        assertEquals(BackAction.None, action)
    }

    @Test
    fun `Given no history and null loaded url when resolving back action then returns None`() {
        val action = resolveBackAction(webViewWithoutHistory(), loadedUrl = null)

        assertEquals(BackAction.None, action)
    }

    @Test
    fun `Given no history and sub-path loaded url when resolving back action then returns None`() {
        val action = resolveBackAction(
            webViewWithoutHistory(),
            Uri.parse("https://ha.local:8123/history?external_auth=1"),
        )

        assertEquals(BackAction.None, action)
    }

    @Test
    fun `Given no history and sub-path loaded url with extra params when resolving back action then returns None`() {
        val action = resolveBackAction(
            webViewWithoutHistory(),
            Uri.parse("https://ha.local:8123/history?start_date=2026-01-01&external_auth=1#tab"),
        )

        assertEquals(BackAction.None, action)
    }

    @Test
    fun `Given same-origin previous url when resolving back action then returns GoBack`() {
        val webView = webViewWithHistory("https://ha.local:8123/lovelace/0")

        val action = resolveBackAction(webView, Uri.parse("https://ha.local:8123/history?external_auth=1"))

        assertEquals(BackAction.GoBack, action)
    }

    @Test
    fun `Given cross-origin previous url and sub-path loaded url when resolving back action then returns NavigateToRoot`() {
        val webView = webViewWithHistory("https://other.server:8123/lovelace/0")

        val action = resolveBackAction(
            webView,
            Uri.parse("https://ha.local:8123/history?start_date=2026-01-01&external_auth=1#tab"),
        )

        assertTrue(action is BackAction.NavigateToRoot)
        val rootUrl = (action as BackAction.NavigateToRoot).rootUrl
        assertEquals("/", rootUrl.path)
        assertEquals("ha.local", rootUrl.host)
        assertEquals("1", rootUrl.getQueryParameter("external_auth"))
        assertEquals(null, rootUrl.getQueryParameter("start_date"))
        assertEquals(null, rootUrl.fragment)
    }

    @Test
    fun `Given cross-origin previous url and root loaded url when resolving back action then returns None`() {
        val webView = webViewWithHistory("https://other.server:8123/lovelace/0")

        val action = resolveBackAction(webView, Uri.parse("https://ha.local:8123/?external_auth=1"))

        assertEquals(BackAction.None, action)
    }

    @Test
    fun `Given non-http previous url when resolving back action then returns NavigateToRoot`() {
        val webView = webViewWithHistory("about:blank")

        val action = resolveBackAction(webView, Uri.parse("https://ha.local:8123/history?external_auth=1"))

        assertTrue(action is BackAction.NavigateToRoot)
    }

    private fun webViewWithoutHistory(): WebView = mockk {
        every { canGoBack() } returns false
    }

    private fun webViewWithHistory(previousUrl: String): WebView {
        val historyItem = mockk<WebHistoryItem> {
            every { url } returns previousUrl
        }
        val backForwardList = mockk<WebBackForwardList> {
            every { currentIndex } returns 1
            every { getItemAtIndex(0) } returns historyItem
        }
        return mockk {
            every { canGoBack() } returns true
            every { copyBackForwardList() } returns backForwardList
        }
    }

    /**
     * Builds a mock [Uri] backed by a [FakeUri] data class so that the extension functions
     * `hasSameOrigin`/`hasNonRootPath` and the builder chain used in `resolveBackAction`
     * return consistent values without a real Android framework.
     */
    private fun createMockUri(uriString: String): Uri = mockUriFrom(FakeUri.parse(uriString))

    private fun mockUriFrom(fake: FakeUri): Uri {
        return mockk {
            every { scheme } returns fake.scheme
            every { host } returns fake.host
            every { port } returns fake.port
            every { path } returns fake.path
            every { fragment } returns fake.fragment
            every { getQueryParameter(any()) } answers { fake.queryParams[firstArg<String>()] }
            every { this@mockk.toString() } returns fake.toString()
            every { buildUpon() } answers { mockUriBuilder(fake.copy()) }
        }
    }

    private fun mockUriBuilder(state: FakeUri): Uri.Builder = mockk {
        every { path(any()) } answers {
            state.path = firstArg()
            this@mockk
        }
        every { clearQuery() } answers {
            state.queryParams = linkedMapOf()
            this@mockk
        }
        every { appendQueryParameter(any(), any()) } answers {
            state.queryParams[firstArg()] = secondArg()
            this@mockk
        }
        every { fragment(any()) } answers {
            state.fragment = firstArg()
            this@mockk
        }
        every { build() } answers { mockUriFrom(state.copy(queryParams = LinkedHashMap(state.queryParams))) }
    }

    private data class FakeUri(
        val scheme: String?,
        val host: String?,
        val port: Int,
        var path: String?,
        var queryParams: LinkedHashMap<String, String>,
        var fragment: String?,
    ) {
        override fun toString(): String {
            val hostPart = if (host != null) {
                val portPart = if (port != -1) ":$port" else ""
                "://$host$portPart"
            } else {
                ":"
            }
            val pathPart = path.orEmpty()
            val queryPart = if (queryParams.isEmpty()) "" else "?" + queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
            val fragmentPart = fragment?.let { "#$it" } ?: ""
            return "$scheme$hostPart$pathPart$queryPart$fragmentPart"
        }

        companion object {
            fun parse(uri: String): FakeUri {
                // Handle opaque URIs (e.g. about:blank)
                if (!uri.contains("://")) {
                    val (scheme, rest) = uri.split(":", limit = 2)
                    return FakeUri(
                        scheme = scheme,
                        host = null,
                        port = -1,
                        path = rest.takeIf { it.isNotEmpty() },
                        queryParams = linkedMapOf(),
                        fragment = null,
                    )
                }
                val schemeEnd = uri.indexOf("://")
                val scheme = uri.substring(0, schemeEnd)
                val afterScheme = uri.substring(schemeEnd + 3)
                val fragment = afterScheme.substringAfter('#', "").takeIf { it.isNotEmpty() }
                val beforeFragment = afterScheme.substringBefore('#')
                val query = beforeFragment.substringAfter('?', "").takeIf { it.isNotEmpty() }
                val beforeQuery = beforeFragment.substringBefore('?')
                val slashIndex = beforeQuery.indexOf('/')
                val authority = if (slashIndex == -1) beforeQuery else beforeQuery.substring(0, slashIndex)
                val path = if (slashIndex == -1) "" else beforeQuery.substring(slashIndex)
                val host = authority.substringBefore(':')
                val port = authority.substringAfter(':', "").toIntOrNull() ?: -1
                val params = linkedMapOf<String, String>()
                query?.split('&')?.forEach { kv ->
                    val parts = kv.split('=', limit = 2)
                    if (parts.size == 2) params[parts[0]] = parts[1]
                }
                return FakeUri(scheme, host, port, path, params, fragment)
            }
        }
    }
}
