package io.homeassistant.companion.android.testing.unit

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

private const val LOCALHOST = "localhost"
private const val EMPTY_OK_RESPONSE = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"

/**
 * Local HTTPS server for tests, listening on the loopback address and presenting [certificate], which
 * should be issued for `localhost`.
 *
 * Answers every request with an empty `200 OK` and closes the connection, until it is closed itself.
 */
class LocalTlsServer(certificate: HeldCertificate) : AutoCloseable {

    private val serverSocket: ServerSocket = HandshakeCertificates.Builder()
        .heldCertificate(certificate)
        .build()
        .sslContext().serverSocketFactory
        .createServerSocket(0, 1, InetAddress.getLoopbackAddress())

    private val serverThread = thread {
        while (!serverSocket.isClosed) {
            serveOneConnection()
        }
    }

    /** The `https://localhost` URL of this server. */
    val url: String = "https://$LOCALHOST:${serverSocket.localPort}/"

    /** Answers a single request, completing the TLS handshake on the first read. */
    private fun serveOneConnection() {
        try {
            serverSocket.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                while (reader.readLine()?.isNotEmpty() == true) {
                    // Skip the request headers until the blank line ending them
                }
                socket.getOutputStream().write(EMPTY_OK_RESPONSE.toByteArray())
                socket.getOutputStream().flush()
            }
        } catch (_: IOException) {
            // Server closed, or the client dropped the connection (e.g. it rejected the certificate)
        }
    }

    /** Stops the server and waits for its thread to end. */
    override fun close() {
        serverSocket.close()
        serverThread.join()
    }
}
