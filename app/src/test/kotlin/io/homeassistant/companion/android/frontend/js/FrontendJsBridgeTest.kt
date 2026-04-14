package io.homeassistant.companion.android.frontend.js

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.frontend.externalbus.frontendExternalBusJson
import io.homeassistant.companion.android.frontend.session.AuthPayload
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.util.FailFastExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.modules.plus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@ExtendWith(ConsoleLogExtension::class, FailFastExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class FrontendJsBridgeTest {

    private val handler: FrontendJsHandler = mockk(relaxed = true)
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val serverId = 42
    private val serverUrl = "https://example.com"

    /** Identifies which bridge protocol to test. */
    enum class BridgeVersion { V1, V2 }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun createBridge(
        stateProvider: () -> BridgeState = { BridgeState(serverId = serverId, url = serverUrl) },
        scope: TestScope,
    ) = FrontendJsBridge(
        handler = handler,
        serverManager = serverManager,
        scope = scope,
        stateProvider = stateProvider,
    )

    private fun mockGetServer(version: String?) {
        val server = Server(
            _name = "test",
            _version = version,
            connection = ServerConnectionInfo(externalUrl = serverUrl),
            session = ServerSessionInfo(),
            user = ServerUserInfo(),
        )
        coEvery { serverManager.getServer(any<Int>()) } returns server
    }

    private fun mockWebViewFeatureSupported(supported: Boolean) {
        mockkStatic(WebViewFeature::class)
        every { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) } returns supported
    }

    private fun mockWebViewCompat() {
        mockkStatic(WebViewCompat::class)
        every { WebViewCompat.addWebMessageListener(any(), any(), any(), any()) } returns Unit
        every { WebViewCompat.removeWebMessageListener(any(), any()) } returns Unit
    }

    /**
     * Attaches the bridge using the given [bridgeVersion] and returns a function that sends messages.
     *
     * The returned lambda takes (methodName/type, payloadJson) and sends the message through
     * the appropriate protocol path.
     */
    private suspend fun attach(bridge: FrontendJsBridge, bridgeVersion: BridgeVersion): (String, String) -> Unit = when (bridgeVersion) {
        BridgeVersion.V1 -> attachV1(bridge)
        BridgeVersion.V2 -> attachV2(bridge)
    }

    private suspend fun attachV1(bridge: FrontendJsBridge): (String, String) -> Unit {
        mockGetServer(version = "2025.12.0")
        mockWebViewFeatureSupported(supported = false)
        val jsInterfaceSlot = slot<Any>()
        val webView: WebView = mockk(relaxed = true)
        every { webView.addJavascriptInterface(capture(jsInterfaceSlot), any()) } returns Unit

        bridge.attachToWebView(webView)

        val captured = jsInterfaceSlot.captured
        return { methodName, payload ->
            captured.javaClass.getMethod(methodName, String::class.java).invoke(captured, payload)
        }
    }

    private suspend fun attachV2(bridge: FrontendJsBridge): (String, String) -> Unit {
        mockGetServer(version = "2026.4.2")
        mockWebViewFeatureSupported(supported = true)
        mockkStatic(WebViewCompat::class)
        every { WebViewCompat.removeWebMessageListener(any(), any()) } returns Unit
        val listenerSlot = slot<WebViewCompat.WebMessageListener>()
        every { WebViewCompat.addWebMessageListener(any(), any(), any(), capture(listenerSlot)) } returns Unit
        mockkStatic(Uri::class)
        val mockCurrentUri: Uri = mockk {
            every { scheme } returns "https"
            every { host } returns "example.com"
            every { port } returns -1
        }
        every { Uri.parse(serverUrl) } returns mockCurrentUri
        val webView: WebView = mockk(relaxed = true)

        bridge.attachToWebView(webView)

        val listener = listenerSlot.captured
        val origin: Uri = mockk {
            every { scheme } returns "https"
            every { host } returns "example.com"
            every { port } returns -1
        }
        return { type, payloadJson ->
            val envelope = """{"type":"$type","payload":$payloadJson}"""
            val message: WebMessageCompat = mockk { every { data } returns envelope }
            val replyProxy: JavaScriptReplyProxy = mockk(relaxed = true)
            listener.onPostMessage(webView, message, origin, true, replyProxy)
        }
    }

    @Nested
    inner class AttachToWebView {

        @Test
        fun `Given old server with WebMessageListener supported then removes V2 and registers V1`() = runTest {
            mockGetServer(version = "2025.12.0")
            mockWebViewFeatureSupported(supported = true)
            mockWebViewCompat()
            val webView: WebView = mockk(relaxed = true)
            val bridge = createBridge(scope = this)

            bridge.attachToWebView(webView)

            verify { WebViewCompat.removeWebMessageListener(webView, FrontendJsBridge.EXTERNAL_APP_V2_LISTENER) }
            verify { webView.addJavascriptInterface(any(), FrontendJsBridge.EXTERNAL_APP_V1) }
        }

        @Test
        fun `Given V2 server and WebMessageListener supported then registers V2 listener`() = runTest {
            mockGetServer(version = "2026.4.2")
            mockWebViewFeatureSupported(supported = true)
            mockWebViewCompat()
            val webView: WebView = mockk(relaxed = true)
            val bridge = createBridge(scope = this)

            bridge.attachToWebView(webView)

            verify { WebViewCompat.addWebMessageListener(webView, FrontendJsBridge.EXTERNAL_APP_V2_LISTENER, any(), any()) }
            verify { webView.removeJavascriptInterface(FrontendJsBridge.EXTERNAL_APP_V1) }
        }

        @Test
        fun `Given V2 server but WebMessageListener not supported then falls back to V1`() = runTest {
            mockGetServer(version = "2026.4.2")
            mockWebViewFeatureSupported(supported = false)
            val webView: WebView = mockk(relaxed = true)
            val bridge = createBridge(scope = this)

            bridge.attachToWebView(webView)

            verify { webView.addJavascriptInterface(any(), FrontendJsBridge.EXTERNAL_APP_V1) }
        }

        @Test
        fun `Given null server then registers V1 interface`() = runTest {
            coEvery { serverManager.getServer(serverId) } returns null
            mockWebViewFeatureSupported(supported = true)
            mockWebViewCompat()
            val webView: WebView = mockk(relaxed = true)
            val bridge = createBridge(scope = this)

            bridge.attachToWebView(webView)

            verify { webView.addJavascriptInterface(any(), FrontendJsBridge.EXTERNAL_APP_V1) }
        }
    }

    @Nested
    inner class Dispatching {

        @ParameterizedTest
        @EnumSource(BridgeVersion::class)
        fun `Given getExternalAuth then handler receives parsed AuthPayload`(bridgeVersion: BridgeVersion) = runTest {
            val bridge = createBridge(scope = this)
            val invoke = attach(bridge, bridgeVersion)

            invoke("getExternalAuth", """{"callback":"externalAuthSetToken","force":true}""")
            advanceUntilIdle()

            coVerify(exactly = 1) {
                handler.getExternalAuth(AuthPayload(callback = "externalAuthSetToken", force = true), serverId)
            }
        }

        @ParameterizedTest
        @EnumSource(BridgeVersion::class)
        fun `Given revokeExternalAuth then handler receives parsed AuthPayload`(bridgeVersion: BridgeVersion) = runTest {
            val bridge = createBridge(scope = this)
            val invoke = attach(bridge, bridgeVersion)

            invoke("revokeExternalAuth", """{"callback":"externalAuthRevokeToken"}""")
            advanceUntilIdle()

            coVerify(exactly = 1) {
                handler.revokeExternalAuth(AuthPayload(callback = "externalAuthRevokeToken"), serverId)
            }
        }

        @ParameterizedTest
        @EnumSource(BridgeVersion::class)
        fun `Given externalBus then handler receives parsed JsonElement`(bridgeVersion: BridgeVersion) = runTest {
            val bridge = createBridge(scope = this)
            val invoke = attach(bridge, bridgeVersion)

            invoke("externalBus", """{"type":"config/get","id":1}""")
            advanceUntilIdle()

            val expected = buildJsonObject {
                put("type", "config/get")
                put("id", 1)
            }
            coVerify(exactly = 1) { handler.externalBus(expected) }
        }

        @ParameterizedTest
        @EnumSource(BridgeVersion::class)
        fun `Given changing serverId then uses current serverId each time`(bridgeVersion: BridgeVersion) = runTest {
            var currentServerId = 1
            val bridge = createBridge(
                stateProvider = { BridgeState(serverId = currentServerId, url = serverUrl) },
                scope = this,
            )
            val invoke = attach(bridge, bridgeVersion)
            val payload = """{"callback":"externalAuthSetToken","force":false}"""
            val expectedPayload = AuthPayload(callback = "externalAuthSetToken", force = false)

            invoke("getExternalAuth", payload)
            advanceUntilIdle()

            currentServerId = 2
            invoke("getExternalAuth", payload)
            advanceUntilIdle()

            coVerify(exactly = 1) { handler.getExternalAuth(expectedPayload, 1) }
            coVerify(exactly = 1) { handler.getExternalAuth(expectedPayload, 2) }
        }
    }

    @Nested
    inner class CallbackValidation {

        @ParameterizedTest
        @EnumSource(BridgeVersion::class)
        fun `Given unexpected callback in getExternalAuth then handler is not called`(bridgeVersion: BridgeVersion) = runTest {
            var failFastTriggered = false
            FailFast.setHandler { _, _ -> failFastTriggered = true }
            val bridge = createBridge(scope = this)
            val invoke = attach(bridge, bridgeVersion)

            invoke("getExternalAuth", """{"callback":"unknown","force":false}""")
            advanceUntilIdle()

            coVerify(exactly = 0) { handler.getExternalAuth(any(), any()) }
            assertTrue(failFastTriggered)
        }

        @ParameterizedTest
        @EnumSource(BridgeVersion::class)
        fun `Given unexpected callback in revokeExternalAuth then handler is not called`(bridgeVersion: BridgeVersion) = runTest {
            var failFastTriggered = false
            FailFast.setHandler { _, _ -> failFastTriggered = true }
            val bridge = createBridge(scope = this)
            val invoke = attach(bridge, bridgeVersion)

            invoke("revokeExternalAuth", """{"callback":"unknown"}""")
            advanceUntilIdle()

            coVerify(exactly = 0) { handler.revokeExternalAuth(any(), any()) }
            assertTrue(failFastTriggered)
        }
    }

    @Nested
    inner class Deserialization {

        @Test
        fun `Given unknown type in JSON when deserialized then produces Unknown variant`() {
            val json = Json(frontendExternalBusJson) {
                serializersModule += BridgeMessage.serializersModule
            }
            val input = """{"type":"futureFeature","payload":{"key":"value"}}"""

            val result = json.decodeFromString<BridgeMessage>(input)

            assertTrue(result is BridgeMessage.Unknown)
        }

        @Test
        fun `Given known type in JSON when deserialized then produces correct variant`() {
            val json = Json(frontendExternalBusJson) {
                serializersModule += BridgeMessage.serializersModule
            }
            val input = """{"type":"getExternalAuth","payload":{"callback":"externalAuthSetToken","force":true}}"""

            val result = json.decodeFromString<BridgeMessage>(input)

            assertTrue(result is BridgeMessage.GetExternalAuth)
            val auth = result as BridgeMessage.GetExternalAuth
            assertEquals(AuthPayload(callback = "externalAuthSetToken", force = true), auth.payload)
        }
    }
}
