package io.homeassistant.companion.android.frontend.externalbus

import app.cash.turbine.test
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.frontend.EvaluateJavascriptUsage
import io.homeassistant.companion.android.frontend.WebViewAction
import io.homeassistant.companion.android.frontend.externalbus.incoming.ConfigGetMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.ConnectionStatusMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.UnknownIncomingMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ConfigResultMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.OutgoingExternalBusMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(EvaluateJavascriptUsage::class)
class FrontendExternalBusRepositoryTest {

    private lateinit var repository: FrontendExternalBusRepository

    @BeforeEach
    fun setup() {
        repository = FrontendExternalBusRepository()
    }

    @Test
    fun `Given connection-status message when received then emit ConnectionStatusMessage`() = runTest {
        val json = Json.parseToJsonElement("""{"type":"connection-status","id":1,"payload":{"event":"connected"}}""")

        repository.incomingMessages().test {
            repository.onMessageReceived(json)

            val message = assertInstanceOf(ConnectionStatusMessage::class.java, awaitItem())
            assertEquals(1, message.id)
            assertTrue(message.payload.isConnected)
        }
    }

    @Test
    fun `Given config-get message when received then emit ConfigGetMessage`() = runTest {
        val json = Json.parseToJsonElement("""{"type":"config/get","id":42}""")

        repository.incomingMessages().test {
            repository.onMessageReceived(json)

            val message = assertInstanceOf(ConfigGetMessage::class.java, awaitItem())
            assertEquals(42, message.id)
        }
    }

    @Test
    fun `Given unknown type when received then emit UnknownIncomingMessage`() = runTest {
        val json = Json.parseToJsonElement("""{"type":"future-feature","id":99,"payload":{"data":"something"}}""")

        repository.incomingMessages().test {
            repository.onMessageReceived(json)

            val message = assertInstanceOf(UnknownIncomingMessage::class.java, awaitItem())
            assertTrue(message.content.toString().contains("future-feature"))
        }
    }

    @Test
    fun `Given non-object JsonElement when received then do not emit`() = runTest {
        repository.incomingMessages().test {
            repository.onMessageReceived(JsonNull)

            expectNoEvents()
        }
    }

    @Test
    fun `Given object without type when received then emit UnknownIncomingMessage`() = runTest {
        val json = buildJsonObject { put("data", "value") }

        repository.incomingMessages().test {
            repository.onMessageReceived(json)

            assertInstanceOf(UnknownIncomingMessage::class.java, awaitItem())
        }
    }

    @Test
    fun `Given message without id when received then id is null`() = runTest {
        val json = Json.parseToJsonElement("""{"type":"config/get"}""")

        repository.incomingMessages().test {
            repository.onMessageReceived(json)

            val message = awaitItem() as ConfigGetMessage
            assertEquals(null, message.id)
        }
    }

    @Test
    fun `Given outgoing message when sent then emit as EvaluateScript on webViewActions flow`() = runTest {
        val configResponse = ConfigResultMessage(
            id = 1,
            hasNfc = true,
            canCommissionMatter = false,
            canExportThread = true,
            hasBarCodeScanner = 2,
            canSetupImprov = false,
            appVersion = AppVersion.from("1.0.0 (1)"),
        )

        repository.webViewActions().test {
            repository.send(configResponse)

            val action = assertInstanceOf(WebViewAction.EvaluateScript::class.java, awaitItem())
            assertEquals("externalBus(${frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(configResponse)});", action.script)
        }
    }

    @Test
    fun `Given evaluateScript called then suspend until result is completed`() = runTest {
        val testScript = "testCallback(true)"
        val expectedResult = "success"

        repository.webViewActions().test {
            // Start evaluateScript in background - it will suspend
            val resultDeferred = async { repository.evaluateScript(testScript) }

            // Collect the emitted action
            val action = assertInstanceOf(WebViewAction.EvaluateScript::class.java, awaitItem())
            assertEquals(testScript, action.script)
            assertFalse(action.result.isCompleted)

            // Simulate WebView completing the result
            action.result.complete(expectedResult)

            // Now evaluateScript should return
            val result = resultDeferred.await()
            assertEquals(expectedResult, result)
        }
    }
}
