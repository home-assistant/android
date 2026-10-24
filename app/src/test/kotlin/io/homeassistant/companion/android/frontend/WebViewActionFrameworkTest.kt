package io.homeassistant.companion.android.frontend

import android.net.Uri
import android.webkit.WebView
import androidx.core.net.toUri
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [WebViewAction]s that require Android framework classes.
 *
 * This test class uses Robolectric (JUnit 4) because [Uri] is an Android framework class
 * that requires the Android runtime to function properly. The main [WebViewActionTest] uses
 * JUnit 5 for tests that don't require Android classes.
 * */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class WebViewActionFrameworkTest {

    private val webView: WebView = mockk(relaxed = true)

    @Test
    fun `Given ReadCurrentUriForExternal when webview has URL with parameters then returns expected Uri`() = runTest {
        val urls = mapOf(
            "http://homeassistant.local/?external_auth=1" to "http://homeassistant.local/",
            "https://example.com:8123/path/to/page?external_auth=1#fragment" to "https://example.com:8123/path/to/page#fragment",
            "http://192.168.1.1:8123/logbook?start_date=2026-09-15T13%3A00%3A00.000Z&end_date=2026-09-15T16%3A00%3A00.000Z" to "http://192.168.1.1:8123/logbook?start_date=2026-09-15T13%3A00%3A00.000Z&end_date=2026-09-15T16%3A00%3A00.000Z",
        )
        urls.forEach { (webViewUrl, expected) ->
            every { webView.url } returns webViewUrl

            val action = WebViewAction.ReadCurrentUriForExternal()
            action.run(webView)
            val result = action.await()

            assertEquals(expected.toUri(), result)
        }
    }
}
