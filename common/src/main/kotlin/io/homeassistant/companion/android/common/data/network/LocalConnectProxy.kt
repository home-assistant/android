package io.homeassistant.companion.android.common.data.network

import io.homeassistant.companion.android.util.sensitive
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
private const val MAX_CONNECT_PROXY_WORKERS = 32

/**
 * Minimal HTTP CONNECT proxy bound to localhost.
 *
 * WebView can route HTTPS and WebSocket traffic through this proxy so hostname resolution uses
 * [NetworkAwareDns] while the browser still performs TLS with the original hostname (correct SNI
 * and certificate validation).
 */
@Singleton
class LocalConnectProxy @Inject constructor(private val networkAwareDns: NetworkAwareDns) {
    private val lifecycleLock = Any()
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var workerPool: ExecutorService? = null

    /** Returns the local port the proxy listens on, or `null` when stopped. */
    fun currentPort(): Int? = if (running.get()) serverSocket?.localPort else null

    /** Returns the local port the proxy listens on, or `null` if startup failed. */
    fun start(): Int? = synchronized(lifecycleLock) {
        if (running.get()) {
            return serverSocket?.localPort
        }
        return try {
            val socket = ServerSocket()
            socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
            serverSocket = socket
            workerPool = Executors.newFixedThreadPool(MAX_CONNECT_PROXY_WORKERS) { runnable ->
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
            stopInternal()
            null
        }
    }

    fun stop() = synchronized(lifecycleLock) {
        stopInternal()
    }

    private fun stopInternal() {
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

            val address = connectToTarget(host = host, port = port)
            try {
                client.getOutputStream().write(CONNECT_ESTABLISHED_RESPONSE)
                client.getOutputStream().flush()
                relay(input, client.getOutputStream(), address)
            } finally {
                address.close()
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

    private fun connectToTarget(host: String, port: Int): Socket {
        val boundLookup = networkAwareDns.lookupBoundToActiveNetwork(host)
        val addresses = orderConnectAddresses(boundLookup.addresses)
        if (addresses.isEmpty()) {
            throw java.net.UnknownHostException(host)
        }

        var lastFailure: Exception? = null
        for (address in addresses) {
            try {
                return openSocketOnNetwork(
                    network = boundLookup.network,
                    address = address,
                    port = port,
                    connectTimeoutMs = CONNECT_READ_TIMEOUT_MS,
                )
            } catch (exception: Exception) {
                lastFailure = exception
                Timber.tag(TAG).d(
                    exception,
                    "CONNECT to %s via %s failed, trying next address",
                    sensitive(host),
                    sensitive(address.hostAddress ?: address.toString()),
                )
            }
        }
        throw lastFailure ?: java.net.UnknownHostException(host)
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
 * Orders resolved addresses for CONNECT attempts: IPv6 first, then IPv4 when both exist.
 */
internal fun orderConnectAddresses(addresses: List<InetAddress>): List<InetAddress> {
    if (addresses.isEmpty()) {
        return emptyList()
    }
    val ipv6Addresses = addresses.filterIsInstance<Inet6Address>()
    val ipv4Addresses = addresses.filterIsInstance<Inet4Address>()
    return if (ipv6Addresses.isNotEmpty() && ipv4Addresses.isNotEmpty()) {
        (ipv6Addresses + ipv4Addresses).distinct()
    } else {
        addresses.distinct()
    }
}

/**
 * Parses a CONNECT target such as `host:443` or `[2001:db8::1]:443`.
 */
internal fun parseConnectTarget(target: String): Pair<String, Int> {
    if (target.startsWith("[")) {
        val host = target.substringAfter("[").substringBefore("]")
        val portSuffix = target.substringAfter("]", missingDelimiterValue = "")
        val port = if (portSuffix.startsWith(":")) {
            portSuffix.substring(1).toValidConnectPortOrNull()
        } else {
            null
        } ?: DEFAULT_CONNECT_PORT
        return host to port
    }

    val portSeparator = target.lastIndexOf(':')
    if (portSeparator > 0) {
        val host = target.substring(0, portSeparator)
        val port = target.substring(portSeparator + 1).toValidConnectPortOrNull()
        if (host.count { it == ':' } >= 3 && port != null) {
            return host to port
        }
        if (host.contains('.') || !host.contains(':')) {
            return host to (port ?: DEFAULT_CONNECT_PORT)
        }
    }

    LiteralIpAddressParser.parse(target)?.let { _ ->
        return target to DEFAULT_CONNECT_PORT
    }

    return target to DEFAULT_CONNECT_PORT
}

private fun String.toValidConnectPortOrNull(): Int? {
    val port = toIntOrNull() ?: return null
    return port.takeIf { it in MIN_CONNECT_PORT..MAX_CONNECT_PORT }
}

/**
 * Reads a single CRLF- or LF-terminated line without buffering bytes beyond the line end.
 */
internal fun readConnectProxyAsciiLine(input: InputStream): String? {
    val builder = StringBuilder()
    while (true) {
        if (builder.length > MAX_CONNECT_PROXY_LINE_LENGTH) {
            throw ConnectProxyLineTooLongException()
        }
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

private class ConnectProxyLineTooLongException : Exception()

private const val DEFAULT_CONNECT_PORT = 443
private const val MIN_CONNECT_PORT = 1
private const val MAX_CONNECT_PORT = 65535
private const val MAX_CONNECT_PROXY_LINE_LENGTH = 8192
