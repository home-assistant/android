package io.homeassistant.companion.android.frontend.webview

import android.content.Context
import androidx.webkit.WebViewFeature
import io.homeassistant.companion.android.common.data.network.LocalConnectProxy
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
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

    @Test
    fun `Given session retained again before clear executes then proxy is not stopped`() {
        every { WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) } returns false

        manager.retainSession()
        manager.releaseSession()
        manager.retainSession()

        verify(exactly = 0, timeout = 2_000) { localConnectProxy.stop() }
    }

    @Test
    fun `Given retained session when released then clears proxy`() {
        every { WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) } returns false

        manager.retainSession()
        manager.releaseSession()

        verify(timeout = 2_000) { localConnectProxy.stop() }
    }

    @Test
    fun `Given multiple retained sessions when one released then proxy stays active`() {
        every { WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) } returns false

        manager.retainSession()
        manager.retainSession()
        manager.releaseSession()

        verify(exactly = 0) { localConnectProxy.stop() }
        manager.releaseSession()
        verify(timeout = 2_000, exactly = 1) { localConnectProxy.stop() }
    }
}
