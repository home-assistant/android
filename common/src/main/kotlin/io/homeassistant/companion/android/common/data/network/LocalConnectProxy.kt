package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import timber.log.Timber

private const val CONNECT_READ_TIMEOUT_MS = 30_000

/**
 * Minimal HTTP CONNECT proxy bound to localhost.
 *
 * WebView can route HTTPS and WebSocket traffic through this proxy so hostname resolution uses
 * [NetworkAwareDns] while the browser still performs TLS with the original hostname (correct SNI
 * and certificate validation).
 */
@Singleton
class LocalConnectProxy @Inject constructor(
    private val networkAwareDns: NetworkAwareDns,
    private val connectivityManager: ConnectivityManager,
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var workerPool: ExecutorService? = null

    /** Returns the local port the proxy listens on, or `null` if startup failed. */
    fun start(): Int? {
        if (running.get()) {
            return serverSocket?.localPort
        }
        return try {
            val socket = ServerSocket()
            socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
            serverSocket = socket
            workerPool = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "LocalConnectProxy-worker").apply { isDaemon = true }
            }
            running.set(true)
            acceptThread = thread(name = "LocalConnectProxy-accept", isDaemon = true) {
                acceptConnections(socket)
            }
            Timber.tag(TAG).d("Local CONNECT proxy listening on 127.0.0.1:%d", socket.localPort)
            socket.localPort
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to start local CONNECT proxy")
            stop()
            null
        }
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "Error closing CONNECT proxy server socket")
        }
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
        workerPool?.shutdownNow()
        workerPool = null
    }

    private fun acceptConnections(serverSocket: ServerSocket) {
        while (running.get()) {
            try {
                val client = serverSocket.accept()
                workerPool?.execute { handleClient(client) }
            } catch (e: Exception) {
                if (running.get()) {
                    Timber.tag(TAG).d(e, "CONNECT proxy accept failed")
                }
            }
        }
    }

    private fun handleClient(client: Socket) {
        client.soTimeout = CONNECT_READ_TIMEOUT_MS
        try {
            val input = client.getInputStream()
            val requestLine = readConnectProxyAsciiLine(input) ?: return
            if (!requestLine.startsWith("CONNECT ", ignoreCase = true)) {
                Timber.tag(TAG).d("CONNECT proxy ignoring non-CONNECT request: %s", requestLine)
                sendErrorResponse(client, HTTP_BAD_GATEWAY)
                return
            }
            val target = requestLine
                .substringAfter("CONNECT ")
                .substringBefore(" ")
                .trim()
            val (host, port) = parseConnectTarget(target)

            while (true) {
                val headerLine = readConnectProxyAsciiLine(input) ?: break
                if (headerLine.isEmpty()) {
                    break
                }
            }

            val address = selectAddress(networkAwareDns.lookup(host))
            val remote = openNetworkSocket(address, port)
            try {
                client.getOutputStream().write(CONNECT_ESTABLISHED_RESPONSE)
                client.getOutputStream().flush()
                relay(input, client.getOutputStream(), remote)
            } finally {
                remote.close()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "CONNECT proxy session failed")
            sendErrorResponse(client, HTTP_BAD_GATEWAY)
        } finally {
            runCatching { client.close() }
        }
    }

    private fun sendErrorResponse(client: Socket, response: ByteArray) {
        runCatching {
            client.getOutputStream().write(response)
            client.getOutputStream().flush()
        }
    }

    private fun openNetworkSocket(address: InetAddress, port: Int): Socket {
        val network = connectivityManager.activeNetwork
        val socket = network?.socketFactory?.createSocket() ?: Socket()
        socket.connect(InetSocketAddress(address, port), CONNECT_READ_TIMEOUT_MS)
        return socket
    }

    private fun selectAddress(addresses: List<InetAddress>): InetAddress {
        val ipv6 = addresses.filterIsInstance<Inet6Address>()
        val ipv4 = addresses.filterIsInstance<Inet4Address>()
        return when {
            ipv4.isEmpty() && ipv6.isNotEmpty() -> ipv6.first()
            ipv6.isEmpty() && ipv4.isNotEmpty() -> ipv4.first()
            else -> addresses.first()
        }
    }

    /**
     * Relays bytes from [clientInput] to [remoteSocket] and back.
     *
     * Uses the same [InputStream] that parsed the CONNECT headers so no TLS bytes are lost to
     * reader buffering.
     */
    private fun relay(clientInput: InputStream, clientOutput: java.io.OutputStream, remoteSocket: Socket) {
        val remoteInput = remoteSocket.getInputStream()
        val remoteOutput = remoteSocket.getOutputStream()
        val clientToRemote = thread(isDaemon = true) {
            runCatching { clientInput.copyTo(remoteOutput) }
            runCatching { remoteSocket.shutdownOutput() }
        }
        val remoteToClient = thread(isDaemon = true) {
            runCatching { remoteInput.copyTo(clientOutput) }
            runCatching { clientOutput.flush() }
        }
        clientToRemote.join()
        remoteToClient.join()
    }

    private companion object {
        private const val TAG = "LocalConnectProxy"
        private val CONNECT_ESTABLISHED_RESPONSE =
            "HTTP/1.1 200 Connection Established\r\n\r\n".encodeToByteArray()
        private val HTTP_BAD_GATEWAY =
            "HTTP/1.1 502 Bad Gateway\r\n\r\n".encodeToByteArray()
    }
}

/**
 * Parses a CONNECT target such as `host:443` or `[2001:db8::1]:443`.
 */
internal fun parseConnectTarget(target: String): Pair<String, Int> {
    if (target.startsWith("[")) {
        val host = target.substringAfter("[").substringBefore("]")
        val port = target.substringAfter("]:").toIntOrNull() ?: 443
        return host to port
    }
    val portSeparator = target.lastIndexOf(':')
    if (portSeparator == -1) {
        return target to 443
    }
    val host = target.substring(0, portSeparator)
    val port = target.substring(portSeparator + 1).toIntOrNull() ?: 443
    return host to port
}

/**
 * Reads a single CRLF- or LF-terminated line without buffering bytes beyond the line end.
 */
internal fun readConnectProxyAsciiLine(input: InputStream): String? {
    val builder = StringBuilder()
    while (true) {
        val byte = input.read()
        if (byte == -1) {
            return builder.toString().takeIf { it.isNotEmpty() }
        }
        if (byte == '\n'.code) {
            var line = builder.toString()
            if (line.endsWith("\r")) {
                line = line.dropLast(1)
            }
            return line
        }
        builder.append(byte.toChar())
    }
}
