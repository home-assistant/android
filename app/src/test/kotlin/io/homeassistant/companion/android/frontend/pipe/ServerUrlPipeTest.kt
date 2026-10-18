package io.homeassistant.companion.android.frontend.pipe

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerUrlPipeTest {

    private val route = Route("http://192.168.1.2:8123/".toHttpUrl(), "https://example.ui.nabu.casa/".toHttpUrl())

    private fun head(text: String) = Head.read(ByteArrayInputStream(text.toByteArray(Charsets.ISO_8859_1)))!!

    @Test
    fun `Given a proxied request when rewriting it for the server then it targets the server URL in use`() {
        val request = head(
            "GET http://192.168.1.2:8123/api/states?x=1 HTTP/1.1\r\n" +
                "Host: 192.168.1.2:8123\r\nProxy-Connection: keep-alive\r\n" +
                "Origin: http://192.168.1.2:8123\r\nReferer: http://192.168.1.2:8123/lovelace\r\n\r\n",
        )

        val sent = request.forServer(route, keepAlive = false)

        assertEquals("GET /api/states?x=1 HTTP/1.1", sent.start)
        assertEquals("example.ui.nabu.casa", sent.header("Host"))
        assertEquals("https://example.ui.nabu.casa", sent.header("Origin"))
        assertEquals("https://example.ui.nabu.casa/lovelace", sent.header("Referer"))
        assertEquals("close", sent.header("Connection"))
        assertNull(sent.header("Proxy-Connection"))
    }

    @Test
    fun `Given a WebSocket handshake in a tunnel when rewriting it then the upgrade is kept`() {
        val request = head(
            "GET /api/websocket HTTP/1.1\r\nHost: 192.168.1.2:8123\r\nConnection: Upgrade\r\nUpgrade: websocket\r\n\r\n",
        )

        val sent = request.forServer(route, keepAlive = true)

        assertEquals("GET /api/websocket HTTP/1.1", sent.start)
        assertEquals("Upgrade", sent.header("Connection"))
        assertEquals("websocket", sent.header("Upgrade"))
    }

    @Test
    fun `Given a server response when rewriting it for the page then it stays on the page origin`() {
        val response = head(
            "HTTP/1.1 302 Found\r\nLocation: https://example.ui.nabu.casa/auth/login\r\n" +
                "Set-Cookie: ingress_session=abc; Path=/api/hassio_ingress/; Secure; SameSite=None\r\n" +
                "Strict-Transport-Security: max-age=31536000\r\n\r\n",
        )

        val received = response.forPage(route, keepAlive = false)

        assertEquals("http://192.168.1.2:8123/auth/login", received.header("Location"))
        assertEquals("ingress_session=abc; Path=/api/hassio_ingress/; SameSite=Lax", received.header("Set-Cookie"))
        assertNull(received.header("Strict-Transport-Security"))
        assertEquals("close", received.header("Connection"))
    }

    @Test
    fun `Given a stream that ends before the head is complete when reading then there is no head`() {
        assertNull(Head.read(ByteArrayInputStream("GET / HTTP/1.1\r\nHost: x\r\n".toByteArray())))
    }

    @Test
    fun `Given a request with a body when it goes through the pipe then the server gets it and the page its answer`() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val received = CompletableFuture<String>()
            Thread {
                server.accept().use { socket ->
                    val input = BufferedInputStream(socket.getInputStream())
                    val request = Head.read(input)!!
                    val body = String(input.readNBytes(5))
                    received.complete("${request.start} | ${request.header("Host")} | $body")
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok".toByteArray())
                }
            }.start()
            val pipe = ServerUrlPipe()
            val port = pipe.start("http://192.168.1.2:8123/".toHttpUrl(), "http://127.0.0.1:${server.localPort}/".toHttpUrl())

            val answer = Socket(InetAddress.getLoopbackAddress(), port).use { page ->
                page.getOutputStream().write(
                    "POST http://192.168.1.2:8123/api/template HTTP/1.1\r\nHost: 192.168.1.2:8123\r\nContent-Length: 5\r\n\r\nhello"
                        .toByteArray(),
                )
                page.getInputStream().readBytes().decodeToString()
            }

            assertEquals("POST /api/template HTTP/1.1 | 127.0.0.1:${server.localPort} | hello", received.get(5, TimeUnit.SECONDS))
            assertTrue(answer.startsWith("HTTP/1.1 200 OK"), answer)
            assertTrue(answer.endsWith("\r\n\r\nok"), answer)
        }
    }

    @Test
    fun `Given a request for another site when it reaches the pipe then it is refused`() {
        val pipe = ServerUrlPipe()
        val port = pipe.start("http://192.168.1.2:8123/".toHttpUrl(), "http://127.0.0.1:9/".toHttpUrl())

        val answer = Socket(InetAddress.getLoopbackAddress(), port).use { page ->
            page.getOutputStream().write("GET http://evil.example/ HTTP/1.1\r\nHost: evil.example\r\n\r\n".toByteArray())
            page.getInputStream().readBytes().decodeToString()
        }

        assertTrue(answer.startsWith("HTTP/1.1 403"), answer)
    }
}
