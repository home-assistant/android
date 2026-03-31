package io.homeassistant.companion.android.frontend

import android.webkit.WebView
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class FrontendJsBridgeTest {

    private val handler: FrontendJsHandler = mockk(relaxed = true)
    private val serverId = 42

    @Test
    fun `Given payload when getExternalAuth called then handler is called with payload and serverId`() = runTest {
        val payload = """{"callback":"authCallback","force":true}"""
        val bridge = FrontendJsBridge(
            handler = handler,
            serverIdProvider = { serverId },
            scope = this,
        )

        bridge.getExternalAuth(payload)
        advanceUntilIdle()

        coVerify(exactly = 1) { handler.getExternalAuth(payload, serverId) }
    }

    @Test
    fun `Given payload when revokeExternalAuth called then handler is called with payload and serverId`() = runTest {
        val payload = """{"callback":"revokeCallback"}"""
        val bridge = FrontendJsBridge(
            handler = handler,
            serverIdProvider = { serverId },
            scope = this,
        )

        bridge.revokeExternalAuth(payload)
        advanceUntilIdle()

        coVerify(exactly = 1) { handler.revokeExternalAuth(payload, serverId) }
    }

    @Test
    fun `Given message when externalBus called then handler is called with message`() = runTest {
        val message = """{"type":"config/get","id":1}"""
        val bridge = FrontendJsBridge(
            handler = handler,
            serverIdProvider = { serverId },
            scope = this,
        )

        bridge.externalBus(message)
        advanceUntilIdle()

        coVerify(exactly = 1) { handler.externalBus(message) }
    }

    @Test
    fun `Given onHomeAssistantSetTheme called then handler is called`() = runTest {
        val bridge = FrontendJsBridge(
            handler = handler,
            serverIdProvider = { serverId },
            scope = this,
        )

        bridge.onHomeAssistantSetTheme()
        advanceUntilIdle()

        coVerify(exactly = 1) { handler.onHomeAssistantSetTheme() }
    }

    @Test
    fun `Given webView when attachToWebView called then removes old interface before adding new one`() = runTest {
        val webView: WebView = mockk(relaxed = true)
        val bridge = FrontendJsBridge(
            handler = handler,
            serverIdProvider = { serverId },
            scope = this,
        )

        bridge.attachToWebView(webView)

        verifyOrder {
            webView.removeJavascriptInterface("externalApp")
            webView.addJavascriptInterface(bridge, "externalApp")
        }
    }

    @Test
    fun `Given different serverId provider when getExternalAuth called then uses current serverId`() = runTest {
        var currentServerId = 1
        val dynamicBridge = FrontendJsBridge(
            handler = handler,
            serverIdProvider = { currentServerId },
            scope = this,
        )

        dynamicBridge.getExternalAuth("payload1")
        advanceUntilIdle()

        currentServerId = 2
        dynamicBridge.getExternalAuth("payload2")
        advanceUntilIdle()

        coVerify(exactly = 1) { handler.getExternalAuth("payload1", 1) }
        coVerify(exactly = 1) { handler.getExternalAuth("payload2", 2) }
    }
}
