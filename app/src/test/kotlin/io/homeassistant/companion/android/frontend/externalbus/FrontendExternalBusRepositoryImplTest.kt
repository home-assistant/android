package io.homeassistant.companion.android.frontend.externalbus

import app.cash.turbine.test
import io.homeassistant.companion.android.frontend.externalbus.incoming.ConfigGetMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.ConnectionStatusMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.UnknownIncomingMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ConfigResult
import io.homeassistant.companion.android.frontend.externalbus.outgoing.OutgoingExternalBusMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ResultMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FrontendExternalBusRepositoryImplTest {

    private lateinit var repository: FrontendExternalBusRepositoryImpl

    @BeforeEach
    fun setup() {
        repository = FrontendExternalBusRepositoryImpl()
    }

    @Test
    fun `Given connection-status message when received then emit ConnectionStatusMessage`() = runTest {
        val json = """{"type":"connection-status","id":1,"payload":{"event":"connected"}}"""

        repository.incomingMessages().test {
            repository.onMessageReceived(json)

            val message = awaitItem()
            assertInstanceOf(ConnectionStatusMessage::class.java, message)
            val statusMessage = message as ConnectionStatusMessage
            assertEquals(1, statusMessage.id)
            assertTrue(statusMessage.payload.isConnected)
        }
    }

    @Test
    fun `Given config-get message when received then emit ConfigGetMessage`() = runTest {
        val json = """{"type":"config/get","id":42}"""

        repository.incomingMessages().test {
            repository.onMessageReceived(json)

            val message = awaitItem()
            assertInstanceOf(ConfigGetMessage::class.java, message)
            assertEquals(42, (message as ConfigGetMessage).id)
        }
    }

    @Test
    fun `Given unknown type when received then emit UnknownIncomingMessage`() = runTest {
        val json = """{"type":"future-feature","id":99,"payload":{"data":"something"}}"""

        repository.incomingMessages().test {
            repository.onMessageReceived(json)

            val message = awaitItem()
            assertInstanceOf(UnknownIncomingMessage::class.java, message)
            val unknown = message as UnknownIncomingMessage
            assertTrue(unknown.content.toString().contains("future-feature"))
        }
    }

    @Test
    fun `Given invalid input when received then do not emit`() = runTest {
        repository.incomingMessages().test {
            repository.onMessageReceived("")
            repository.onMessageReceived("   ")
            repository.onMessageReceived("not valid json")
            repository.onMessageReceived("{invalid}")

            expectNoEvents()
        }
    }

    @Test
    fun `Given message without id when received then id is null`() = runTest {
        val json = """{"type":"config/get"}"""

        repository.incomingMessages().test {
            repository.onMessageReceived(json)

            val message = awaitItem() as ConfigGetMessage
            assertEquals(null, message.id)
        }
    }

    @Test
    fun `Given outgoing message when sent then emit as script on scriptsToEvaluate flow`() = runTest {
        val configResponse = ResultMessage.config(
            id = 1,
            config = ConfigResult(
                canWriteTag = true,
                canCommissionMatter = false,
                canImportThreadCredentials = true,
                hasBarCodeScanner = 2,
                appVersion = "1.0.0",
            ),
        )

        repository.scriptsToEvaluate().test {
            repository.send(configResponse)

            val script = awaitItem()
            assertEquals("externalBus(${frontendExternalBusJson.encodeToString<OutgoingExternalBusMessage>(configResponse)});", script.script)
            assertFalse(script.result.isCompleted)
        }
    }

    @Test
    fun `Given evaluateScript called then suspend until result is completed`() = runTest {
        val testScript = "testCallback(true)"
        val expectedResult = "success"

        repository.scriptsToEvaluate().test {
            // Start evaluateScript in background - it will suspend
            val resultDeferred = async { repository.evaluateScript(testScript) }

            // Collect the emitted script
            val webViewScript = awaitItem()
            assertEquals(testScript, webViewScript.script)
            assertFalse(webViewScript.result.isCompleted)

            // Simulate WebView completing the result
            webViewScript.result.complete(expectedResult)

            // Now evaluateScript should return
            val result = resultDeferred.await()
            assertEquals(expectedResult, result)
        }
    }
}
