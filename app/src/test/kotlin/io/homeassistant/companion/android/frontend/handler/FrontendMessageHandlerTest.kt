package io.homeassistant.companion.android.frontend.handler

import android.content.pm.PackageManager
import app.cash.turbine.test
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.externalbus.WebViewScript
import io.homeassistant.companion.android.frontend.externalbus.incoming.ConfigGetMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.ConnectionStatusMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.ConnectionStatusPayload
import io.homeassistant.companion.android.frontend.externalbus.incoming.OpenAssistMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.OpenAssistPayload
import io.homeassistant.companion.android.frontend.externalbus.incoming.OpenSettingsMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.ThemeUpdateMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.UnknownIncomingMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.OutgoingExternalBusMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ResultMessage
import io.homeassistant.companion.android.frontend.session.AuthPayload
import io.homeassistant.companion.android.frontend.session.ExternalAuthResult
import io.homeassistant.companion.android.frontend.session.RevokeAuthResult
import io.homeassistant.companion.android.frontend.session.ServerSessionManager
import io.homeassistant.companion.android.matter.MatterManager
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.thread.ThreadManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class FrontendMessageHandlerTest {

    private val externalBusRepository: FrontendExternalBusRepository = mockk(relaxed = true)
    private val packageManager: PackageManager = mockk()
    private val matterManager: MatterManager = mockk()
    private val threadManager: ThreadManager = mockk()
    private val appVersionProvider: AppVersionProvider = mockk()
    private val sessionManager: ServerSessionManager = mockk(relaxed = true)
    private lateinit var handler: FrontendMessageHandler

    @BeforeEach
    fun setup() {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_NFC) } returns false
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) } returns true
        every { matterManager.appSupportsCommissioning() } returns false
        every { threadManager.appSupportsThread() } returns false
        every { appVersionProvider() } returns AppVersion.from("1.0.0", 1)
        every { externalBusRepository.scriptsToEvaluate() } returns emptyFlow()

        handler = FrontendMessageHandler(
            externalBusRepository = externalBusRepository,
            packageManager = packageManager,
            matterManager = matterManager,
            threadManager = threadManager,
            appVersionProvider = appVersionProvider,
            sessionManager = sessionManager,
            isAutomotive = false,
        )
    }

    @Test
    fun `Given connected status message when messageResults then emits Connected`() = runTest {
        val message = ConnectionStatusMessage(
            id = null,
            payload = ConnectionStatusPayload(event = "connected"),
        )
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.Connected)
            expectNoEvents()
        }
    }

    @Test
    fun `Given disconnected status message when messageResults then emits Disconnected`() = runTest {
        val message = ConnectionStatusMessage(
            id = null,
            payload = ConnectionStatusPayload(event = "disconnected"),
        )
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.Disconnected)
            expectNoEvents()
        }
    }

    @Test
    fun `Given config get message when messageResults then emits ConfigSent and sends response`() = runTest {
        val messageId = 42
        val message = ConfigGetMessage(id = messageId)
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        val responseSlot = slot<OutgoingExternalBusMessage>()
        coEvery { externalBusRepository.send(capture(responseSlot)) } returns Unit

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.ConfigSent)
            expectNoEvents()
        }

        coVerify { externalBusRepository.send(any()) }
        assertEquals(messageId, responseSlot.captured.id)
    }

    @Test
    fun `Given config get message when messageResults then config response contains all expected fields`() = runTest {
        // Setup specific capabilities
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_NFC) } returns true
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) } returns true
        every { matterManager.appSupportsCommissioning() } returns true
        every { threadManager.appSupportsThread() } returns true
        every { appVersionProvider() } returns AppVersion.from("2.0.0", 200)

        val testHandler = FrontendMessageHandler(
            externalBusRepository = externalBusRepository,
            packageManager = packageManager,
            matterManager = matterManager,
            threadManager = threadManager,
            appVersionProvider = appVersionProvider,
            sessionManager = sessionManager,
            isAutomotive = false,
        )

        val message = ConfigGetMessage(id = 1)
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        val responseSlot = slot<OutgoingExternalBusMessage>()
        coEvery { externalBusRepository.send(capture(responseSlot)) } returns Unit

        testHandler.messageResults().test {
            awaitItem()
            expectNoEvents()
        }

        val configResult = (responseSlot.captured as ResultMessage).result.jsonObject
        // Field names match ConfigResult serialization: hasNfc -> canWriteTag, canExportThread -> canImportThreadCredentials
        assertEquals(true, configResult["canWriteTag"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(true, configResult["canCommissionMatter"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(true, configResult["canImportThreadCredentials"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(1, configResult["hasBarCodeScanner"]?.jsonPrimitive?.int)
        assertEquals("2.0.0 (200)", configResult["appVersion"]?.jsonPrimitive?.content)
    }

    @Test
    fun `Given device without capabilities when config get then config response has false values`() = runTest {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_NFC) } returns false
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) } returns false
        every { matterManager.appSupportsCommissioning() } returns false
        every { threadManager.appSupportsThread() } returns false

        val testHandler = FrontendMessageHandler(
            externalBusRepository = externalBusRepository,
            packageManager = packageManager,
            matterManager = matterManager,
            threadManager = threadManager,
            appVersionProvider = appVersionProvider,
            sessionManager = sessionManager,
            isAutomotive = false,
        )

        val message = ConfigGetMessage(id = 1)
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        val responseSlot = slot<OutgoingExternalBusMessage>()
        coEvery { externalBusRepository.send(capture(responseSlot)) } returns Unit

        testHandler.messageResults().test {
            awaitItem()
            expectNoEvents()
        }

        val configResult = (responseSlot.captured as ResultMessage).result.jsonObject
        // Field names match ConfigResult serialization: hasNfc -> canWriteTag, canExportThread -> canImportThreadCredentials
        assertEquals(false, configResult["canWriteTag"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(false, configResult["canCommissionMatter"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(false, configResult["canImportThreadCredentials"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(0, configResult["hasBarCodeScanner"]?.jsonPrimitive?.int)
    }

    @Test
    fun `Given open assist message when messageResults then emits ShowAssist with payload`() = runTest {
        val message = OpenAssistMessage(
            id = 7,
            payload = OpenAssistPayload(pipelineId = "abc", startListening = false),
        )
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.ShowAssist)
            val showAssist = result as FrontendHandlerEvent.ShowAssist
            assertEquals("abc", showAssist.pipelineId)
            assertEquals(false, showAssist.startListening)
            expectNoEvents()
        }
    }

    @Test
    fun `Given open settings message when messageResults then emits OpenSettings`() = runTest {
        val message = OpenSettingsMessage(id = 1)
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.OpenSettings)
            expectNoEvents()
        }
    }

    @Test
    fun `Given theme update message when messageResults then emits ThemeUpdated`() = runTest {
        val message = ThemeUpdateMessage(id = null)
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.ThemeUpdated)
            expectNoEvents()
        }
    }

    @Test
    fun `Given unknown message when messageResults then emits UnknownMessage`() = runTest {
        val message = UnknownIncomingMessage(content = JsonPrimitive("unknown-type"))
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.UnknownMessage)
            expectNoEvents()
        }
    }

    @Test
    fun `Given script when evaluateScript then calls repository evaluateScript`() = runTest {
        val script = "console.log('test')"
        val expectedResult = "undefined"
        coEvery { externalBusRepository.evaluateScript(script) } returns expectedResult

        val result = handler.evaluateScript(script)

        assertEquals(expectedResult, result)
        coVerify { externalBusRepository.evaluateScript(script) }
    }

    @Test
    fun `Given scripts flow when scriptsToEvaluate then returns repository flow`() = runTest {
        val script = WebViewScript(script = "test()")
        every { externalBusRepository.scriptsToEvaluate() } returns flowOf(script)

        handler.scriptsToEvaluate().test {
            val result = awaitItem()
            assertEquals(script.script, result.script)
            awaitComplete()
        }
    }

    @Test
    fun `Given automotive device when config get then hasBarCodeScanner is 0`() = runTest {
        val automotiveHandler = FrontendMessageHandler(
            externalBusRepository = externalBusRepository,
            packageManager = packageManager,
            matterManager = matterManager,
            threadManager = threadManager,
            appVersionProvider = appVersionProvider,
            sessionManager = sessionManager,
            isAutomotive = true,
        )

        val message = ConfigGetMessage(id = 1)
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        val responseSlot = slot<OutgoingExternalBusMessage>()
        coEvery { externalBusRepository.send(capture(responseSlot)) } returns Unit

        automotiveHandler.messageResults().test {
            awaitItem()
            expectNoEvents()
        }

        coVerify { externalBusRepository.send(any()) }
        val configResult = (responseSlot.captured as? ResultMessage)?.result
        assertNotNull(configResult)
        assertEquals(0, configResult.jsonObject["hasBarCodeScanner"]?.jsonPrimitive?.int)
    }

    @Test
    fun `Given device without camera when config get then hasBarCodeScanner is 0`() = runTest {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) } returns false

        val handlerWithoutCamera = FrontendMessageHandler(
            externalBusRepository = externalBusRepository,
            packageManager = packageManager,
            matterManager = matterManager,
            threadManager = threadManager,
            appVersionProvider = appVersionProvider,
            sessionManager = sessionManager,
            isAutomotive = false,
        )

        val message = ConfigGetMessage(id = 1)
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        val responseSlot = slot<OutgoingExternalBusMessage>()
        coEvery { externalBusRepository.send(capture(responseSlot)) } returns Unit

        handlerWithoutCamera.messageResults().test {
            awaitItem()
            expectNoEvents()
        }

        coVerify { externalBusRepository.send(any()) }
        val configResult = (responseSlot.captured as? ResultMessage)?.result
        assertNotNull(configResult)
        assertEquals(0, configResult.jsonObject["hasBarCodeScanner"]?.jsonPrimitive?.int)
    }

    @Test
    fun `Given valid payload and successful auth when getExternalAuth then evaluates success callback`() = runTest {
        val payload = """{"callback":"authCallback","force":false}"""
        val authPayload = AuthPayload(callback = "authCallback", force = false)
        val authResult = ExternalAuthResult.Success(callbackScript = "authCallback(true, {token})")

        coEvery { sessionManager.getExternalAuth(1, authPayload) } returns authResult
        coEvery { externalBusRepository.evaluateScript("authCallback(true, {token})") } returns null

        handler.getExternalAuth(payload, serverId = 1)

        coVerify { externalBusRepository.evaluateScript("authCallback(true, {token})") }
    }

    @Test
    fun `Given valid payload and failed auth with error when getExternalAuth then evaluates callback and emits AuthError`() = runTest {
        val payload = """{"callback":"authCallback","force":false}"""
        val authPayload = AuthPayload(callback = "authCallback", force = false)
        val error = FrontendConnectionError.AuthenticationError(
            message = commonR.string.error_connection_failed,
            errorDetails = "Auth failed",
            rawErrorType = "ExternalAuthFailed",
        )
        val authResult = ExternalAuthResult.Failed(callbackScript = "authCallback(false)", error = error)

        coEvery { sessionManager.getExternalAuth(1, authPayload) } returns authResult
        coEvery { externalBusRepository.evaluateScript("authCallback(false)") } returns null
        every { externalBusRepository.incomingMessages() } returns emptyFlow()

        // Start collecting BEFORE calling getExternalAuth to catch the emitted event
        handler.messageResults().test {
            handler.getExternalAuth(payload, serverId = 1)

            val event = awaitItem()
            assertTrue(event is FrontendHandlerEvent.AuthError)
            assertEquals(error, (event as FrontendHandlerEvent.AuthError).error)
            expectNoEvents()
        }

        coVerify { externalBusRepository.evaluateScript("authCallback(false)") }
    }

    @Test
    fun `Given valid payload and failed auth without error when getExternalAuth then evaluates callback only`() = runTest {
        val payload = """{"callback":"authCallback","force":false}"""
        val authPayload = AuthPayload(callback = "authCallback", force = false)
        val authResult = ExternalAuthResult.Failed(callbackScript = "authCallback(false)", error = null)

        coEvery { sessionManager.getExternalAuth(1, authPayload) } returns authResult
        coEvery { externalBusRepository.evaluateScript("authCallback(false)") } returns null
        every { externalBusRepository.incomingMessages() } returns emptyFlow()

        handler.getExternalAuth(payload, serverId = 1)

        coVerify { externalBusRepository.evaluateScript("authCallback(false)") }

        // No AuthError should be emitted - flow should have no items from auth
        handler.messageResults().test {
            expectNoEvents()
            expectNoEvents()
        }
    }

    @Test
    fun `Given force true when getExternalAuth then passes force to sessionManager`() = runTest {
        val payload = """{"callback":"authCallback","force":true}"""
        val authPayload = AuthPayload(callback = "authCallback", force = true)
        val authResult = ExternalAuthResult.Success(callbackScript = "authCallback(true, {token})")

        coEvery { sessionManager.getExternalAuth(1, authPayload) } returns authResult
        coEvery { externalBusRepository.evaluateScript(any()) } returns null

        handler.getExternalAuth(payload, serverId = 1)

        coVerify { sessionManager.getExternalAuth(1, authPayload) }
    }

    @Test
    fun `Given valid payload and successful revoke when revokeExternalAuth then evaluates success callback`() = runTest {
        val payload = """{"callback":"revokeCallback","force":false}"""
        val authPayload = AuthPayload(callback = "revokeCallback", force = false)
        val revokeResult = RevokeAuthResult.Success(callbackScript = "revokeCallback(true)")

        coEvery { sessionManager.revokeExternalAuth(1, authPayload) } returns revokeResult
        coEvery { externalBusRepository.evaluateScript("revokeCallback(true)") } returns null

        handler.revokeExternalAuth(payload, serverId = 1)

        coVerify { externalBusRepository.evaluateScript("revokeCallback(true)") }
    }

    @Test
    fun `Given valid payload and failed revoke when revokeExternalAuth then evaluates failure callback`() = runTest {
        val payload = """{"callback":"revokeCallback","force":false}"""
        val authPayload = AuthPayload(callback = "revokeCallback", force = false)
        val revokeResult = RevokeAuthResult.Failed(callbackScript = "revokeCallback(false)")

        coEvery { sessionManager.revokeExternalAuth(1, authPayload) } returns revokeResult
        coEvery { externalBusRepository.evaluateScript("revokeCallback(false)") } returns null

        handler.revokeExternalAuth(payload, serverId = 1)

        coVerify { externalBusRepository.evaluateScript("revokeCallback(false)") }
    }

    @Test
    fun `Given message when externalBus then forwards to repository`() = runTest {
        val message = """{"type":"test","id":1}"""

        handler.externalBus(message)

        coVerify { externalBusRepository.onMessageReceived(message) }
    }
}
