package io.homeassistant.companion.android.common.data.connectivity

import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.di.SuspendProvider
import io.mockk.every
import io.mockk.mockkStatic
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.CipherSuite
import okhttp3.Handshake
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.TlsVersion
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

private const val HOME_ASSISTANT_MANIFEST = """{"name": "Home Assistant", "short_name": "Home Assistant"}"""
private const val SERVER_URL = "https://example.com:8123/lovelace/0"
private val MANIFEST_MEDIA_TYPE = "application/manifest+json".toMediaType()
private const val LOOPBACK_HOST = "127.0.0.1"

class DefaultConnectivityCheckerTest {

    private val requestedUrls = mutableListOf<String>()

    /**
     * Builds a checker whose HTTP calls are answered by [respond] instead of hitting the network.
     */
    private fun checker(respond: (Request) -> Response): DefaultConnectivityChecker {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestedUrls += chain.request().url.toString()
                respond(chain.request())
            }
            .build()
        return DefaultConnectivityChecker(SuspendProvider { client })
    }

    private fun answering(code: Int, body: ResponseBody) = checker { request ->
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .body(body)
            .build()
    }

    private fun answering(code: Int, body: String) = answering(code, body.toResponseBody(MANIFEST_MEDIA_TYPE))

    /** A checker for the checks that never issue an HTTP request. */
    private fun offlineChecker() = checker { throw AssertionError("no HTTP request expected") }

    /** A body whose download never delivers a byte, like a phone losing its connection mid answer. */
    private fun stallingBody(): ResponseBody = object : Source {
        override fun read(sink: Buffer, byteCount: Long): Long = throw SocketTimeoutException("timeout")

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() = Unit
    }.buffer().asResponseBody(MANIFEST_MEDIA_TYPE)

    @Nested
    inner class Dns {

        @Test
        fun `Given a hostname that resolves when checking DNS then its addresses are reported`() = runTest {
            val result = offlineChecker().dns("localhost")

            assertTrue(result is ConnectivityCheckResult.Success)
            assertEquals(commonR.string.connection_check_dns, (result as ConnectivityCheckResult.Success).messageResId)
            assertTrue(result.details?.isNotBlank() == true)
        }

        @Test
        fun `Given a resolution that outlives its timeout when checking DNS then the check reports a timeout`() = runTest {
            mockkStatic(InetAddress::class) {
                every { InetAddress.getAllByName(any()) } throws timeoutException()

                val result = offlineChecker().dns("home-assistant.local")

                assertEquals(
                    ConnectivityCheckResult.Failure(commonR.string.connection_check_error_dns_timeout),
                    result,
                )
            }
        }

        @Test
        fun `Given a cancelled resolution when checking DNS then the cancellation is not turned into a failure`() = runTest {
            mockkStatic(InetAddress::class) {
                every { InetAddress.getAllByName(any()) } throws CancellationException("cancelled")

                val thrown = runCatching { offlineChecker().dns("home-assistant.local") }.exceptionOrNull()

                assertTrue(thrown is CancellationException)
            }
        }

        /**
         * A genuine [TimeoutCancellationException], which has no public constructor. The checker cannot
         * be made to wait for its real timeout without a blocking sleep, and the point of the test is
         * that this exception fails the check instead of cancelling the run, as it did when the
         * [CancellationException] it extends was caught first.
         */
        private suspend fun timeoutException(): TimeoutCancellationException = try {
            withTimeout(1.milliseconds) { delay(1.seconds) }
            error("the withTimeout above must time out")
        } catch (e: TimeoutCancellationException) {
            e
        }
    }

    @Nested
    inner class Port {

        @Test
        fun `Given a port with a listening socket when checking then the check succeeds`() = runTest {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { socket ->
                val result = offlineChecker().port(LOOPBACK_HOST, socket.localPort)

                assertEquals(
                    ConnectivityCheckResult.Success(commonR.string.connection_check_port, socket.localPort.toString()),
                    result,
                )
            }
        }

        @Test
        fun `Given a port nothing listens on when checking then the check fails`() = runTest {
            val closedPort = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

            val result = offlineChecker().port(LOOPBACK_HOST, closedPort)

            assertEquals(ConnectivityCheckResult.Failure(commonR.string.connection_check_error_port), result)
        }
    }

    @Nested
    inner class Tls {

        @Test
        fun `Given a TLS handshake when checking TLS then the certificate is valid`() = runTest {
            val checker = checker { request ->
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("")
                    .handshake(
                        Handshake.get(
                            TlsVersion.TLS_1_3,
                            CipherSuite.TLS_AES_128_GCM_SHA256,
                            emptyList(),
                            emptyList(),
                        ),
                    )
                    .body("".toResponseBody())
                    .build()
            }

            val result = checker.tls(SERVER_URL)

            assertEquals(ConnectivityCheckResult.Success(commonR.string.connection_check_tls_success), result)
        }

        @Test
        fun `Given an answer without TLS handshake when checking TLS then the check fails`() = runTest {
            val result = answering(200, "").tls(SERVER_URL)

            assertEquals(ConnectivityCheckResult.Failure(commonR.string.connection_check_error_tls), result)
        }

        @Test
        fun `Given a handshake that never completes when checking TLS then the check reports a timeout`() = runTest {
            val result = checker { throw SocketTimeoutException("timeout") }.tls(SERVER_URL)

            assertEquals(ConnectivityCheckResult.Failure(commonR.string.connection_check_error_tls_timeout), result)
        }

        @Test
        fun `Given an unreachable server when checking TLS then the check fails`() = runTest {
            val result = checker { throw IOException("unreachable") }.tls(SERVER_URL)

            assertEquals(ConnectivityCheckResult.Failure(commonR.string.connection_check_error_tls), result)
        }
    }

    @Nested
    inner class HomeAssistantVerification {

        @Test
        fun `Given a Home Assistant manifest when verifying then the check succeeds`() = runTest {
            val result = answering(200, HOME_ASSISTANT_MANIFEST).homeAssistant(SERVER_URL)

            assertEquals(ManifestCheckResult.Verified, result)
        }

        @ParameterizedTest
        @CsvSource(
            "https://example.com:8123/lovelace/0, https://example.com:8123/manifest.json",
            "https://example.com, https://example.com/manifest.json",
            "http://homeassistant.local:8123/, http://homeassistant.local:8123/manifest.json",
            "https://abc123.ui.nabu.casa/lovelace/0?edit=1, https://abc123.ui.nabu.casa/manifest.json",
        )
        fun `Given a server URL when verifying then the manifest of its base URL is requested`(
            serverUrl: String,
            manifestUrl: String,
        ) = runTest {
            answering(200, HOME_ASSISTANT_MANIFEST).homeAssistant(serverUrl)

            assertEquals(listOf(manifestUrl), requestedUrls)
        }

        @ParameterizedTest
        @ValueSource(ints = [401, 404, 500, 502])
        fun `Given an error status when verifying then the check reports it instead of blaming the manifest`(code: Int) = runTest {
            val result = answering(code, "<html>Error</html>").homeAssistant(SERVER_URL)

            assertEquals(
                ManifestCheckResult.NotReached(
                    ConnectivityCheckResult.Failure(
                        commonR.string.connection_check_error_http_status,
                        code.toString(),
                    ),
                ),
                result,
            )
        }

        @Test
        fun `Given a manifest that is not JSON when verifying then the connection holds and the manifest is rejected`() = runTest {
            val result = answering(200, "<html>Login</html>").homeAssistant(SERVER_URL)

            assertEquals(
                ManifestCheckResult.NotVerified(
                    ConnectivityCheckResult.Failure(commonR.string.connection_check_error_manifest),
                ),
                result,
            )
        }

        @ParameterizedTest
        @ValueSource(strings = ["{}", """{"name": "Other"}"""])
        fun `Given a manifest of another product when verifying then the check reports it is not Home Assistant`(
            manifest: String,
        ) = runTest {
            val result = answering(200, manifest).homeAssistant(SERVER_URL)

            assertEquals(
                ManifestCheckResult.NotVerified(
                    ConnectivityCheckResult.Failure(commonR.string.connection_check_error_not_home_assistant),
                ),
                result,
            )
        }

        @Test
        fun `Given an empty answer when verifying then the connection holds and the manifest is rejected`() = runTest {
            val result = answering(200, "").homeAssistant(SERVER_URL)

            assertEquals(
                ManifestCheckResult.NotVerified(
                    ConnectivityCheckResult.Failure(commonR.string.connection_check_error_manifest),
                ),
                result,
            )
        }

        @Test
        fun `Given a connection stalling mid answer when verifying then the check reports a timeout`() = runTest {
            val result = checker { throw SocketTimeoutException("timeout") }.homeAssistant(SERVER_URL)

            assertEquals(
                ManifestCheckResult.NotReached(
                    ConnectivityCheckResult.Failure(commonR.string.connection_check_error_server_timeout),
                ),
                result,
            )
        }

        @Test
        fun `Given an answer stalling mid body when verifying then the connection holds and the manifest times out`() = runTest {
            val result = answering(200, stallingBody()).homeAssistant(SERVER_URL)

            assertEquals(
                ManifestCheckResult.NotVerified(
                    ConnectivityCheckResult.Failure(commonR.string.connection_check_error_server_timeout),
                ),
                result,
            )
        }

        @Test
        fun `Given an unreachable server when verifying then the check reports a connection failure`() = runTest {
            val result = checker { throw IOException("unreachable") }.homeAssistant(SERVER_URL)

            assertEquals(
                ManifestCheckResult.NotReached(
                    ConnectivityCheckResult.Failure(commonR.string.connection_check_error_server),
                ),
                result,
            )
        }
    }
}
