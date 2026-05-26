package io.homeassistant.companion.android.frontend.webview

import android.content.Context
import androidx.webkit.WebViewFeature
import io.homeassistant.companion.android.common.data.network.LocalConnectProxy
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WebViewConnectProxyManagerTest {

    private val localConnectProxy: LocalConnectProxy = mockk(relaxed = true)
    private val manager = WebViewConnectProxyManager(
        context = mockk<Context>(relaxed = true),
        localConnectProxy = localConnectProxy,
    )

    @BeforeEach
    fun setup() {
        mockkStatic(WebViewFeature::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(WebViewFeature::class)
    }

    @Test
    fun `Given proxy override unsupported when checking support then returns false`() {
        every { WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) } returns false

        assertFalse(manager.isProxyOverrideSupported())
        assertFalse(manager.isActive())
    }

    @Test
    fun `Given proxy override supported when not configured then isActive is false`() {
        every { WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) } returns true

        assertTrue(manager.isProxyOverrideSupported())
        assertFalse(manager.isActive())
    }
}
