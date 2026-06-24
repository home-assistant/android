package io.homeassistant.companion.android.frontend.handler

import android.content.pm.PackageManager
import android.net.Uri
import app.cash.turbine.test
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.frontend.EvaluateJavascriptUsage
import io.homeassistant.companion.android.frontend.WebViewAction
import io.homeassistant.companion.android.frontend.addto.FrontendEntityAddToManager
import io.homeassistant.companion.android.frontend.download.DownloadResult
import io.homeassistant.companion.android.frontend.download.FrontendDownloadManager
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.externalbus.incoming.BarcodeCloseMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.BarcodeNotifyMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.BarcodeNotifyPayload
import io.homeassistant.companion.android.frontend.externalbus.incoming.BarcodeScanMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.BarcodeScanPayload
import io.homeassistant.companion.android.frontend.externalbus.incoming.ConfigGetMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.ConnectionStatusMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.ConnectionStatusPayload
import io.homeassistant.companion.android.frontend.externalbus.incoming.EntityAddToGetActionsMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.EntityAddToGetActionsPayload
import io.homeassistant.companion.android.frontend.externalbus.incoming.EntityAddToMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.EntityAddToPayload
import io.homeassistant.companion.android.frontend.externalbus.incoming.ExoPlayerPlayHlsMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.ExoPlayerPlayHlsPayload
import io.homeassistant.companion.android.frontend.externalbus.incoming.ExoPlayerResizeMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.ExoPlayerResizePayload
import io.homeassistant.companion.android.frontend.externalbus.incoming.ExoPlayerStopMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.HandleBlobMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.HapticMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.HapticType
import io.homeassistant.companion.android.frontend.externalbus.incoming.ImprovConfigureDeviceMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.ImprovConfigureDevicePayload
import io.homeassistant.companion.android.frontend.externalbus.incoming.ImprovScanMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.MatterCommissionMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.OpenAssistMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.OpenAssistPayload
import io.homeassistant.companion.android.frontend.externalbus.incoming.OpenAssistSettingsMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.OpenSettingsMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.TagWriteMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.TagWritePayload
import io.homeassistant.companion.android.frontend.externalbus.incoming.ThemeUpdateMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.ThreadImportCredentialsMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.UnknownIncomingMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.OutgoingExternalBusMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ResultMessage
import io.homeassistant.companion.android.frontend.improv.BluetoothCapabilities
import io.homeassistant.companion.android.frontend.navigation.FrontendEvent
import io.homeassistant.companion.android.frontend.session.AuthPayload
import io.homeassistant.companion.android.frontend.session.ExternalAuthResult
import io.homeassistant.companion.android.frontend.session.RevokeAuthResult
import io.homeassistant.companion.android.frontend.session.ServerSessionManager
import io.homeassistant.companion.android.matter.MatterManager
import io.homeassistant.companion.android.thread.ThreadManager
import io.homeassistant.companion.android.webview.addto.EntityAddToAction
import io.homeassistant.companion.android.webview.externalbus.ExternalEntityAddToAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlin.io.encoding.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull

@OptIn(ExperimentalCoroutinesApi::class, EvaluateJavascriptUsage::class)
class FrontendMessageHandlerTest {

    private val externalBusRepository: FrontendExternalBusRepository = mockk(relaxed = true)
    private val packageManager: PackageManager = mockk()
    private val matterManager: MatterManager = mockk()
    private val threadManager: ThreadManager = mockk()
    private val appVersionProvider: AppVersionProvider = mockk()
    private val sessionManager: ServerSessionManager = mockk(relaxed = true)
    private val downloadManager: FrontendDownloadManager = mockk(relaxed = true)
    private val bluetoothCapabilities: BluetoothCapabilities = BluetoothCapabilities { true }
    private val entityAddToManager: FrontendEntityAddToManager =
        mockk(relaxed = true)
    private lateinit var handler: FrontendMessageHandler

    @BeforeEach
    fun setup() {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_NFC) } returns false
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) } returns true
        every { matterManager.appSupportsCommissioning() } returns false
        every { threadManager.appSupportsThread() } returns false
        every { appVersionProvider() } returns AppVersion.from("1.0.0", 1)
        every { externalBusRepository.webViewActions() } returns emptyFlow()

        handler = FrontendMessageHandler(
            externalBusRepository = externalBusRepository,
            packageManager = packageManager,
            matterManager = matterManager,
            threadManager = threadManager,
            appVersionProvider = appVersionProvider,
            sessionManager = sessionManager,
            downloadManager = downloadManager,
            bluetoothCapabilities = bluetoothCapabilities,
            entityAddToManager = entityAddToManager,
            isAutomotive = false,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
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
            downloadManager = downloadManager,
            bluetoothCapabilities = { true },
            entityAddToManager = entityAddToManager,
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
        assertEquals(true, configResult["canSetupImprov"]?.jsonPrimitive?.content?.toBoolean())
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
            downloadManager = downloadManager,
            bluetoothCapabilities = { false },
            entityAddToManager = entityAddToManager,
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
        assertEquals(false, configResult["canSetupImprov"]?.jsonPrimitive?.content?.toBoolean())
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
    fun `Given open assist settings message when messageResults then emits OpenAssistSettings`() = runTest {
        val message = OpenAssistSettingsMessage(id = 1)
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.OpenAssistSettings)
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
    fun `Given tag write message with tag when messageResults then emits WriteNfcTag with tagId`() = runTest {
        val message = TagWriteMessage(id = 42, payload = TagWritePayload(tag = "abc-123"))
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.WriteNfcTag)
            val nfcEvent = result as FrontendHandlerEvent.WriteNfcTag
            assertEquals(42, nfcEvent.messageId)
            assertEquals("abc-123", nfcEvent.tagId)
            expectNoEvents()
        }
    }

    @Test
    fun `Given tag write message without tag when messageResults then emits WriteNfcTag with null tagId`() = runTest {
        val message = TagWriteMessage(id = 7, payload = TagWritePayload(tag = null))
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.WriteNfcTag)
            val nfcEvent = result as FrontendHandlerEvent.WriteNfcTag
            assertEquals(7, nfcEvent.messageId)
            assertEquals(null, nfcEvent.tagId)
            expectNoEvents()
        }
    }

    @Test
    fun `Given tag write message without id when messageResults then emits WriteNfcTag with messageId -1`() = runTest {
        val message = TagWriteMessage(id = null)
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.WriteNfcTag)
            assertEquals(-1, (result as FrontendHandlerEvent.WriteNfcTag).messageId)
            expectNoEvents()
        }
    }

    @Test
    fun `Given Improv scan message when messageResults then emits StartImprovScan`() = runTest {
        val message = ImprovScanMessage(id = 50)
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.StartImprovScan)
            expectNoEvents()
        }
    }

    @Test
    fun `Given Improv configure_device message when messageResults then emits ConfigureImprovDevice with name`() = runTest {
        val message = ImprovConfigureDeviceMessage(
            id = 51,
            payload = ImprovConfigureDevicePayload(name = "Smart Plug"),
        )
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.ConfigureImprovDevice)
            assertEquals("Smart Plug", (result as FrontendHandlerEvent.ConfigureImprovDevice).deviceName)
            expectNoEvents()
        }
    }

    @Test
    fun `Given Matter commission message when messageResults then emits StartMatterCommissioning`() = runTest {
        val message = MatterCommissionMessage(id = 60)
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.StartMatterCommissioning)
            expectNoEvents()
        }
    }

    @Test
    fun `Given Thread import_credentials message when messageResults then emits ImportThreadCredentials`() = runTest {
        val message = ThreadImportCredentialsMessage(id = 61)
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.ImportThreadCredentials)
            expectNoEvents()
        }
    }

    @Test
    fun `Given bar_code scan message with full payload when messageResults then emits ShowBarcodeScanner`() = runTest {
        val message = BarcodeScanMessage(
            id = 60,
            payload = BarcodeScanPayload(
                title = "Scan code",
                description = "Point the camera",
                alternativeOptionLabel = "Enter manually",
            ),
        )
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.ShowBarcodeScanner)
            val show = result as FrontendHandlerEvent.ShowBarcodeScanner
            assertEquals(60, show.messageId)
            assertEquals("Scan code", show.title)
            assertEquals("Point the camera", show.description)
            assertEquals("Enter manually", show.alternativeOptionLabel)
            expectNoEvents()
        }
    }

    @Test
    fun `Given bar_code scan message without id when messageResults then ShowBarcodeScanner messageId is -1`() = runTest {
        val message = BarcodeScanMessage(
            id = null,
            payload = BarcodeScanPayload(title = "t", description = "d"),
        )
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.ShowBarcodeScanner)
            assertEquals(-1, (result as FrontendHandlerEvent.ShowBarcodeScanner).messageId)
            expectNoEvents()
        }
    }

    @Test
    fun `Given bar_code scan message without alternative_option_label when messageResults then label is null`() = runTest {
        val message = BarcodeScanMessage(
            id = 61,
            payload = BarcodeScanPayload(title = "t", description = "d"),
        )
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = assertInstanceOf(FrontendHandlerEvent.ShowBarcodeScanner::class.java, awaitItem())
            assertNull(result.alternativeOptionLabel)
            expectNoEvents()
        }
    }

    @Test
    fun `Given bar_code notify message when messageResults then emits NotifyBarcodeScanner with message`() = runTest {
        val message = BarcodeNotifyMessage(
            id = 62,
            payload = BarcodeNotifyPayload(message = "Code already paired"),
        )
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = assertInstanceOf(FrontendHandlerEvent.NotifyBarcodeScanner::class.java, awaitItem())
            assertEquals("Code already paired", result.message)
            expectNoEvents()
        }
    }

    @Test
    fun `Given bar_code close message when messageResults then emits CloseBarcodeScanner`() = runTest {
        val message = BarcodeCloseMessage(id = 63)
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertEquals(FrontendHandlerEvent.CloseBarcodeScanner, result)
            expectNoEvents()
        }
    }

    @Test
    fun `Given unknown message when messageResults then emits UnknownMessage`() = runTest {
        val message = UnknownIncomingMessage(discriminator = "unknown-type", content = JsonPrimitive("unknown-type"))
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.UnknownMessage)
            expectNoEvents()
        }
    }

    @Test
    fun `Given webViewActions flow when webViewActions then returns repository flow`() = runTest {
        val action = WebViewAction.EvaluateScript(script = "test()")
        every { externalBusRepository.webViewActions() } returns flowOf(action)

        handler.webViewActions().test {
            val result = assertInstanceOf(WebViewAction.EvaluateScript::class.java, awaitItem())
            assertEquals("test()", result.script)
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
            downloadManager = downloadManager,
            bluetoothCapabilities = bluetoothCapabilities,
            entityAddToManager = entityAddToManager,
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
            downloadManager = downloadManager,
            bluetoothCapabilities = bluetoothCapabilities,
            entityAddToManager = entityAddToManager,
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
    fun `Given successful auth when getExternalAuth then evaluates success callback`() = runTest {
        val authPayload = AuthPayload(callback = "externalAuthSetToken", force = false)
        val authResult = ExternalAuthResult.Success(callbackScript = "externalAuthSetToken(true, {token})")

        coEvery { sessionManager.getExternalAuth(1, authPayload) } returns authResult
        coEvery { externalBusRepository.evaluateScript("externalAuthSetToken(true, {token})") } returns null

        handler.getExternalAuth(authPayload, serverId = 1)

        coVerify { externalBusRepository.evaluateScript("externalAuthSetToken(true, {token})") }
    }

    @Test
    fun `Given failed auth with error when getExternalAuth then evaluates callback and emits AuthError`() = runTest {
        val authPayload = AuthPayload(callback = "externalAuthSetToken", force = false)
        val error = FrontendConnectionError.AuthRevoked(
            message = commonR.string.error_connection_failed,
            errorDetails = "Auth failed",
            rawErrorType = "ExternalAuthFailed",
        )
        val authResult = ExternalAuthResult.Failed(callbackScript = "externalAuthSetToken(false)", error = error)

        coEvery { sessionManager.getExternalAuth(1, authPayload) } returns authResult
        coEvery { externalBusRepository.evaluateScript("externalAuthSetToken(false)") } returns null
        every { externalBusRepository.incomingMessages() } returns emptyFlow()

        handler.messageResults().test {
            handler.getExternalAuth(authPayload, serverId = 1)

            val event = awaitItem()
            assertTrue(event is FrontendHandlerEvent.AuthError)
            assertEquals(error, (event as FrontendHandlerEvent.AuthError).error)
            expectNoEvents()
        }

        coVerify { externalBusRepository.evaluateScript("externalAuthSetToken(false)") }
    }

    @Test
    fun `Given failed auth without error when getExternalAuth then evaluates callback only`() = runTest {
        val authPayload = AuthPayload(callback = "externalAuthSetToken", force = false)
        val authResult = ExternalAuthResult.Failed(callbackScript = "externalAuthSetToken(false)", error = null)

        coEvery { sessionManager.getExternalAuth(1, authPayload) } returns authResult
        coEvery { externalBusRepository.evaluateScript("externalAuthSetToken(false)") } returns null
        every { externalBusRepository.incomingMessages() } returns emptyFlow()

        handler.getExternalAuth(authPayload, serverId = 1)

        coVerify { externalBusRepository.evaluateScript("externalAuthSetToken(false)") }

        handler.messageResults().test {
            expectNoEvents()
            expectNoEvents()
        }
    }

    @Test
    fun `Given force true when getExternalAuth then passes force to sessionManager`() = runTest {
        val authPayload = AuthPayload(callback = "externalAuthSetToken", force = true)
        val authResult = ExternalAuthResult.Success(callbackScript = "externalAuthSetToken(true, {token})")

        coEvery { sessionManager.getExternalAuth(1, authPayload) } returns authResult
        coEvery { externalBusRepository.evaluateScript(any()) } returns null

        handler.getExternalAuth(authPayload, serverId = 1)

        coVerify { sessionManager.getExternalAuth(1, authPayload) }
    }

    @Test
    fun `Given successful revoke when revokeExternalAuth then evaluates success callback`() = runTest {
        val authPayload = AuthPayload(callback = "externalAuthRevokeToken", force = false)
        val revokeResult = RevokeAuthResult.Success(callbackScript = "externalAuthRevokeToken(true)")

        coEvery { sessionManager.revokeExternalAuth(1, authPayload) } returns revokeResult
        coEvery { externalBusRepository.evaluateScript("externalAuthRevokeToken(true)") } returns null

        handler.revokeExternalAuth(authPayload, serverId = 1)

        coVerify { externalBusRepository.evaluateScript("externalAuthRevokeToken(true)") }
    }

    @Test
    fun `Given failed revoke when revokeExternalAuth then evaluates failure callback`() = runTest {
        val authPayload = AuthPayload(callback = "externalAuthRevokeToken", force = false)
        val revokeResult = RevokeAuthResult.Failed(callbackScript = "externalAuthRevokeToken(false)")

        coEvery { sessionManager.revokeExternalAuth(1, authPayload) } returns revokeResult
        coEvery { externalBusRepository.evaluateScript("externalAuthRevokeToken(false)") } returns null

        handler.revokeExternalAuth(authPayload, serverId = 1)

        coVerify { externalBusRepository.evaluateScript("externalAuthRevokeToken(false)") }
    }

    @Test
    fun `Given haptic messages when messageResults then emits PerformHaptic with correct types`() = runTest {
        val messages = flowOf(
            HapticMessage(payload = HapticType.Success),
            HapticMessage(payload = HapticType.Light),
            HapticMessage(payload = HapticType.Heavy),
        )
        every { externalBusRepository.incomingMessages() } returns messages

        handler.messageResults().test {
            assertEquals(HapticType.Success, (awaitItem() as FrontendHandlerEvent.PerformHaptic).hapticType)
            assertEquals(HapticType.Light, (awaitItem() as FrontendHandlerEvent.PerformHaptic).hapticType)
            assertEquals(HapticType.Heavy, (awaitItem() as FrontendHandlerEvent.PerformHaptic).hapticType)
            expectNoEvents()
        }
    }

    @Test
    fun `Given message when externalBus then forwards to repository`() = runTest {
        val message = buildJsonObject {
            put("type", "test")
            put("id", 1)
        }

        handler.externalBus(message)

        coVerify { externalBusRepository.onMessageReceived(message) }
    }

    @Test
    fun `Given handle blob message when messageResults then emits DownloadCompleted with result`() = runTest {
        val testData = "data:application/pdf;base64,SGVsbG8="
        val testFilename = "test.pdf"
        coEvery { downloadManager.handleBlob(data = testData, filename = testFilename) } returns DownloadResult.Forwarded
        every { externalBusRepository.incomingMessages() } returns flowOf(HandleBlobMessage(data = testData, filename = testFilename))

        handler.messageResults().test {
            val event = awaitItem()
            assertTrue(event is FrontendHandlerEvent.DownloadCompleted)
            assertEquals(DownloadResult.Forwarded, (event as FrontendHandlerEvent.DownloadCompleted).result)
            expectNoEvents()
        }

        coVerify { downloadManager.handleBlob(data = testData, filename = testFilename) }
    }

    @Test
    fun `Given exoplayer play_hls message with URL when messageResults then emits PlayHls and sends success result`() = runTest {
        mockkStatic(Uri::class)
        val mockUri = mockk<Uri>(relaxed = true)
        every { Uri.parse("https://example.com/stream.m3u8") } returns mockUri

        val message = ExoPlayerPlayHlsMessage(
            id = 9,
            payload = ExoPlayerPlayHlsPayload(url = "https://example.com/stream.m3u8", muted = true),
        )
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        val responseSlot = slot<OutgoingExternalBusMessage>()
        coEvery { externalBusRepository.send(capture(responseSlot)) } returns Unit

        handler.messageResults().test {
            val event = awaitItem()
            assertTrue(event is FrontendHandlerEvent.ExoPlayerAction.PlayHls)
            val playHls = event as FrontendHandlerEvent.ExoPlayerAction.PlayHls
            assertEquals(9, playHls.messageId)
            assertEquals(mockUri, playHls.url)
            assertEquals(true, playHls.muted)
            expectNoEvents()
        }

        coVerify { externalBusRepository.send(any()) }
        val result = responseSlot.captured as ResultMessage
        assertEquals(9, result.id)
        assertEquals(true, result.success)
    }

    @Test
    fun `Given exoplayer play_hls message without URL when messageResults then emits UnknownMessage and sends nothing`() = runTest {
        val message = ExoPlayerPlayHlsMessage(id = 9, payload = ExoPlayerPlayHlsPayload(url = null, muted = false))
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val event = awaitItem()
            assertTrue(event is FrontendHandlerEvent.UnknownMessage)
            expectNoEvents()
        }

        coVerify(exactly = 0) { externalBusRepository.send(any()) }
    }

    @Test
    fun `Given exoplayer stop message when messageResults then emits Stop`() = runTest {
        every { externalBusRepository.incomingMessages() } returns flowOf(ExoPlayerStopMessage())

        handler.messageResults().test {
            val event = awaitItem()
            assertEquals(FrontendHandlerEvent.ExoPlayerAction.Stop, event)
            expectNoEvents()
        }
    }

    @Test
    fun `Given exoplayer resize message when messageResults then emits Resize with payload values`() = runTest {
        val message = ExoPlayerResizeMessage(
            payload = ExoPlayerResizePayload(left = 1.5, top = 2.5, right = 100.5, bottom = 50.5),
        )
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val event = awaitItem()
            assertTrue(event is FrontendHandlerEvent.ExoPlayerAction.Resize)
            val resize = event as FrontendHandlerEvent.ExoPlayerAction.Resize
            assertEquals(1.5, resize.left)
            assertEquals(2.5, resize.top)
            assertEquals(100.5, resize.right)
            assertEquals(50.5, resize.bottom)
            expectNoEvents()
        }
    }

    @Test
    fun `Given entity add_to get_actions message when messageResults then sends response and emits EntityAddToActionsSent`() = runTest {
        val actions = listOf(
            ExternalEntityAddToAction(
                appPayload = "dGVzdA==",
                enabled = true,
                name = "Entity Widget",
                details = null,
                mdiIcon = "mdi:shape",
            ),
        )
        coEvery { entityAddToManager.getActionsForEntity("light.living_room") } returns actions

        val message = EntityAddToGetActionsMessage(
            id = 20,
            payload = EntityAddToGetActionsPayload(
                entityId = "light.living_room",
            ),
        )
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.EntityAddToActionsSent)
            expectNoEvents()
        }

        val sentSlot = slot<OutgoingExternalBusMessage>()
        coVerify { externalBusRepository.send(capture(sentSlot)) }
        val sent = sentSlot.captured as ResultMessage
        assertEquals(20, sent.id)
        assertTrue(sent.success)
    }

    @Test
    fun `Given entity add_to message when messageResults then emits EntityAddToExecuted with event from handler`() = runTest {
        val entityWidgetPayload = Base64.UrlSafe.encode(kotlinJsonMapper.encodeToString<EntityAddToAction>(EntityAddToAction.EntityWidget).encodeToByteArray())
        coEvery {
            entityAddToManager.execute("light.living_room", any())
        } returns FrontendEvent.ShowSnackbar(
            io.homeassistant.companion.android.common.R.string.add_to_android_auto_success,
        )

        val message = EntityAddToMessage(
            id = 21,
            payload = EntityAddToPayload(
                entityId = "light.living_room",
                appPayload = entityWidgetPayload,
            ),
        )
        every { externalBusRepository.incomingMessages() } returns flowOf(message)

        handler.messageResults().test {
            val result = awaitItem()
            assertTrue(result is FrontendHandlerEvent.EntityAddToExecuted)
            val event = (result as FrontendHandlerEvent.EntityAddToExecuted).event
            assertTrue(event is FrontendEvent.ShowSnackbar)
            expectNoEvents()
        }
    }
}
