package io.homeassistant.companion.android.webview

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebViewUrlOverrideTest {

    @Test
    fun `Given same origin URL with different path then WebView should handle it`() {
        val targetUrl = Uri.parse("http://homeassistant.local:8123/lovelace/default")

        val shouldOpen = shouldOpenInExternalBrowser(
            currentUrl = "http://homeassistant.local:8123/",
            targetUrl = targetUrl,
        )

        assertFalse(shouldOpen)
    }

    @Test
    fun `Given different origin URL then external browser should handle it`() {
        val targetUrl = Uri.parse("https://www.home-assistant.io/docs")

        val shouldOpen = shouldOpenInExternalBrowser(
            currentUrl = "http://homeassistant.local:8123/",
            targetUrl = targetUrl,
        )

        assertTrue(shouldOpen)
    }

    @Test
    fun `Given missing current URL then external browser should handle it`() {
        val targetUrl = Uri.parse("http://homeassistant.local:8123/lovelace/default")

        val shouldOpen = shouldOpenInExternalBrowser(
            currentUrl = null,
            targetUrl = targetUrl,
        )

        assertTrue(shouldOpen)
    }
}
