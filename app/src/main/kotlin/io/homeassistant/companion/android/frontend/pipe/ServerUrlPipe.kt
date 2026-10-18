package io.homeassistant.companion.android.frontend.pipe

import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl
import timber.log.Timber

private const val LOCALHOST = "127.0.0.1"
private const val MAX_HEAD_BYTES = 64 * 1024
private const val CONNECT_TIMEOUT_MS = 10_000
private const val COPY_BUFFER_BYTES = 16 * 1024

/** Requests for the [page] origin go to the [target] origin. */
internal class Route(page: HttpUrl, val target: HttpUrl) {
    val pageOrigin = page.toString().removeSuffix("/")
    val targetOrigin = target.toString().removeSuffix("/")

    /** What WebView names in a CONNECT for the page origin. */
    val pageAuthority = "${page.host}:${page.port}"

    /** The Host header of the target: its host, and its port when it is not the default one. */
    val targetHost = targetOrigin.substringAfter("://")
}

/**
 * Keeps an open frontend page alive while the app moves to another URL of the same server.
 *
 * The page stays on the origin it was loaded from. When the app uses another server URL, WebView sends
 * the page's requests for that origin to this local proxy (androidx.webkit [ProxyController], for that
 * origin only). The proxy rewrites the head of each request and response (host, origin, redirects,
 * cookies) and copies everything else unchanged, so uploads, ranges and WebSockets pass through as
 * they are.
 *
 * Only plain-http page origins can be piped: a request to an https origin is end-to-end encrypted.
 *
 * Other apps on the device can connect to the pipe's local port too. That gives them nothing they do not
 * have already: the pipe only forwards requests for the page origin, only to the server's own remote URL,
 * which is reachable from anywhere, and adds no credentials (the WebView sends the page's own cookies and
 * tokens). The one thing the app could add, a TLS client certificate, is never used: servers that need one
 * are not piped.
 */
@Singleton
class ServerUrlPipe @Inject constructor() {

    @Volatile
    private var route: Route? = null
    private var server: ServerSocket? = null
    private val threads = Executors.newCachedThreadPool()

    /** Whether WebView can send a single origin through the pipe. */
    val isSupported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE_REVERSE_BYPASS)

    /** Whether the page's requests currently go through the pipe. */
    val isActive: Boolean
        get() = route != null

    /** Sends the page's requests for [page]'s origin to [target]'s origin. Returns once WebView uses it. */
    @MainThread
    suspend fun route(page: HttpUrl, target: HttpUrl) {
        val port = start(page, target)
        val config = ProxyConfig.Builder()
            .addProxyRule("$LOCALHOST:$port")
            // With reverse bypass, only the page origin uses the pipe; everything else stays direct
            .addBypassRule("${page.host}:${page.port}")
            .setReverseBypassEnabled(true)
            .build()
        awaitProxy { executor, done -> ProxyController.getInstance().setProxyOverride(config, executor, done) }
        Timber.i("Frontend requests of the page now go through the pipe to the server URL in use")
    }

    /** Sends the page's requests directly again. Returns once WebView uses it. */
    @MainThread
    suspend fun clear() {
        if (route == null) return
        route = null
        awaitProxy { executor, done -> ProxyController.getInstance().clearProxyOverride(executor, done) }
        Timber.i("Frontend requests go directly to the page's server URL again")
    }

    private suspend fun awaitProxy(change: (Executor, Runnable) -> Unit) = suspendCancellableCoroutine { done ->
        change(Executor { it.run() }) { done.resume(Unit) }
    }

    /** Makes the pipe send [page]'s requests to [target] and returns the local port it listens on. */
    @VisibleForTesting
    internal fun start(page: HttpUrl, target: HttpUrl): Int {
        route = Route(page.origin(), target.origin())
        return ensureServer()
    }

    private fun ensureServer(): Int {
        server?.let { return it.localPort }
        val socket = ServerSocket(0, 50, InetAddress.getByName(LOCALHOST))
        server = socket
        threads.execute {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    Timber.w(e, "Pipe stopped accepting")
                    break
                }
                // An exception escaping a pool thread would crash the app; serve catches all of them
                threads.execute { serve(client) }
            }
        }
        return socket.localPort
    }

    @Suppress("TooGenericExceptionCaught") // see ensureServer
    private fun serve(client: Socket) = try {
        client.use { pipe(it) }
    } catch (e: Exception) {
        Timber.d("Pipe connection ended: ${e.javaClass.simpleName}")
    }

    private fun pipe(client: Socket) {
        val fromPage = BufferedInputStream(client.getInputStream())
        val toPage = client.getOutputStream()
        val route = route ?: return toPage.answer("503 Service Unavailable")
        request(route, fromPage, toPage)?.let { (head, tunnel) -> forward(route, head, tunnel, fromPage, toPage) }
    }

    /** The page's request to forward and whether it came through a tunnel, or null after refusing it. */
    private fun request(route: Route, fromPage: InputStream, toPage: OutputStream): Pair<Head, Boolean>? {
        val head = Head.read(fromPage)
        // WebView tunnels WebSockets through a proxy; the request inside the tunnel is plain http
        val tunnel = head?.method == "CONNECT"
        val allowed = when {
            head == null -> false
            tunnel -> head.target == route.pageAuthority
            else -> head.target == route.pageOrigin || head.target.startsWith(route.pageOrigin + "/")
        }
        return when {
            head == null -> null
            !allowed -> null.also { toPage.answer("403 Forbidden") }
            !tunnel -> head to false
            else -> {
                toPage.answer("200 Connection Established")
                Head.read(fromPage)?.let { it to true }
            }
        }
    }

    private fun forward(route: Route, head: Head, tunnel: Boolean, fromPage: InputStream, toPage: OutputStream) {
        connect(route.target).use { upstream ->
            val toServer = upstream.getOutputStream()
            toServer.write(head.forServer(route, keepAlive = tunnel).bytes())
            toServer.flush()
            // The rest of the page's side (a body, WebSocket frames) goes as it is
            threads.execute {
                @Suppress("TooGenericExceptionCaught") // see ensureServer
                try {
                    fromPage.copyTo(toServer)
                } catch (e: Exception) {
                    Timber.d("Pipe upload ended: ${e.javaClass.simpleName}")
                } finally {
                    runCatching { upstream.shutdownOutput() }
                }
            }
            val fromServer = BufferedInputStream(upstream.getInputStream())
            val response = Head.read(fromServer) ?: return
            toPage.write(response.forPage(route, keepAlive = tunnel).bytes())
            fromServer.copyTo(toPage)
        }
    }

    private fun connect(target: HttpUrl): Socket {
        val plain = Socket().apply { connect(InetSocketAddress(target.host, target.port), CONNECT_TIMEOUT_MS) }
        if (!target.isHttps) return plain
        val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(plain, target.host, target.port, true) as SSLSocket
        tls.sslParameters = tls.sslParameters.apply { serverNames = listOf(SNIHostName(target.host)) }
        tls.startHandshake()
        if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(target.host, tls.session)) {
            tls.close()
            throw IOException("Certificate does not match the server URL")
        }
        return tls
    }
}

/** The start line and headers of a request or response, the only part the pipe reads. */
@VisibleForTesting
internal class Head(val start: String, val headers: MutableList<Pair<String, String>>) {
    val method get() = start.substringBefore(' ')
    val target get() = start.split(' ').getOrElse(1) { "" }

    fun header(name: String) = headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

    fun set(name: String, value: String?) {
        headers.removeAll { it.first.equals(name, ignoreCase = true) }
        if (value != null) headers += name to value
    }

    fun bytes(): ByteArray = buildString {
        append(start).append("\r\n")
        headers.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
        append("\r\n")
    }.toByteArray(Charsets.ISO_8859_1)

    /** The request as the server URL in use expects it. */
    fun forServer(route: Route, keepAlive: Boolean): Head {
        val page = route.pageOrigin
        val target = route.targetOrigin
        val path = this.target.removePrefix(page).ifEmpty { "/" }
        val result = Head("$method $path ${start.substringAfterLast(' ')}", headers.toMutableList())
        result.set("Host", route.targetHost)
        result.set("Proxy-Connection", null)
        header("Origin")?.let { result.set("Origin", if (it == page) target else it) }
        header("Referer")?.let {
            result.set("Referer", if (it.startsWith(page)) target + it.removePrefix(page) else it)
        }
        if (!keepAlive) result.set("Connection", "close")
        return result
    }

    /** The response as the page expects it on its own origin. */
    fun forPage(route: Route, keepAlive: Boolean): Head {
        val result = Head(
            start,
            headers.mapTo(mutableListOf()) { (name, value) ->
                when {
                    name.equals("Location", ignoreCase = true) && value.startsWith(route.targetOrigin) ->
                        name to route.pageOrigin + value.removePrefix(route.targetOrigin)
                    // The page origin is plain http: WebView drops Secure cookies there
                    name.equals("Set-Cookie", ignoreCase = true) ->
                        name to value.replace(SECURE, "").replace(SAME_SITE_NONE, "; SameSite=Lax")
                    else -> name to value
                }
            },
        )
        if (!keepAlive) result.set("Connection", "close")
        return result
    }

    companion object {
        private val SECURE = Regex(";\\s*Secure(?=;|$)", RegexOption.IGNORE_CASE)
        private val SAME_SITE_NONE = Regex(";\\s*SameSite=None", RegexOption.IGNORE_CASE)

        /** "\r\n\r\n" as the last 4 bytes read. */
        private const val END_OF_HEAD = 0x0D0A0D0A

        /** Reads up to the empty line that ends a head, or null when the stream ended first. */
        fun read(input: InputStream): Head? {
            val bytes = ByteArrayOutputStream()
            var last4 = 0
            while (last4 != END_OF_HEAD) {
                val b = input.read()
                if (b < 0) return null
                bytes.write(b)
                if (bytes.size() > MAX_HEAD_BYTES) throw IOException("Head too large")
                last4 = (last4 shl Byte.SIZE_BITS) or b
            }
            val lines = bytes.toString(Charsets.ISO_8859_1.name()).split("\r\n").filter { it.isNotEmpty() }
            val headers = lines.drop(1).mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) null else line.substring(0, colon).trim() to line.substring(colon + 1).trim()
            }
            return lines.firstOrNull()?.let { Head(it, headers.toMutableList()) }
        }
    }
}

private fun OutputStream.answer(status: String) {
    write("HTTP/1.1 $status\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
    flush()
}

private fun InputStream.copyTo(out: OutputStream) {
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
        out.flush()
    }
}

private fun HttpUrl.origin(): HttpUrl = newBuilder().encodedPath("/").query(null).fragment(null).build()
