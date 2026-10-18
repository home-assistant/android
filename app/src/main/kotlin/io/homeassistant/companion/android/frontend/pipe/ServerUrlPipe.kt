package io.homeassistant.companion.android.frontend.pipe

import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.io.BufferedInputStream
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
internal class Route(val page: HttpUrl, val target: HttpUrl) {
    val pageOrigin = page.toString().removeSuffix("/")
    val targetOrigin = target.toString().removeSuffix("/")
    val targetHost = if (target.port ==
        HttpUrl.defaultPort(target.scheme)
    ) {
        target.host
    } else {
        "${target.host}:${target.port}"
    }
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

    /**
     * Sends the page's requests for [page]'s origin to [target]'s origin from now on, or directly to
     * [page] again when [target] is null. Returns once WebView uses the new setting.
     */
    @MainThread
    suspend fun route(page: HttpUrl, target: HttpUrl?) {
        val executor = Executor { it.run() }
        if (target == null) {
            route = null
            suspendCancellableCoroutine { done ->
                ProxyController.getInstance().clearProxyOverride(executor) { done.resume(Unit) }
            }
            Timber.i("Frontend requests go directly to the page's server URL again")
            return
        }
        val port = start(page, target)
        val config = ProxyConfig.Builder()
            .addProxyRule("$LOCALHOST:$port")
            // With reverse bypass, only the page origin uses the pipe; everything else stays direct
            .addBypassRule("${page.host}:${page.port}")
            .setReverseBypassEnabled(true)
            .build()
        suspendCancellableCoroutine { done ->
            ProxyController.getInstance().setProxyOverride(config, executor) { done.resume(Unit) }
        }
        Timber.i("Frontend requests of the page now go through the pipe to the server URL in use")
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
                threads.execute { serve(client) }
            }
        }
        return socket.localPort
    }

    private fun serve(client: Socket) = try {
        client.use { pipe(it) }
    } catch (e: IOException) {
        Timber.d("Pipe connection ended: ${e.javaClass.simpleName}")
    }

    private fun pipe(client: Socket) {
        val fromPage = BufferedInputStream(client.getInputStream())
        val toPage = client.getOutputStream()
        val route = route ?: return toPage.answer("503 Service Unavailable")
        var head = Head.read(fromPage) ?: return
        // WebView tunnels WebSockets through a proxy; the request inside the tunnel is plain http
        val tunnel = head.method == "CONNECT"
        if (tunnel) {
            if (head.target != "${route.page.host}:${route.page.port}") return toPage.answer("403 Forbidden")
            toPage.answer("200 Connection Established")
            head = Head.read(fromPage) ?: return
        } else if (!head.target.startsWith(route.pageOrigin)) {
            return toPage.answer("403 Forbidden")
        }
        connect(route.target).use { upstream ->
            val toServer = upstream.getOutputStream()
            toServer.write(head.forServer(route, keepAlive = tunnel).bytes())
            toServer.flush()
            // The rest of the page's side (a body, WebSocket frames) goes as it is
            threads.execute {
                try {
                    fromPage.copyTo(toServer)
                } catch (e: IOException) {
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
        if (!target.isHttps) {
            return Socket().apply { connect(InetSocketAddress(target.host, target.port), CONNECT_TIMEOUT_MS) }
        }
        val plain = Socket().apply { connect(InetSocketAddress(target.host, target.port), CONNECT_TIMEOUT_MS) }
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
        result.set("Proxy-Authorization", null)
        header("Origin")?.let { result.set("Origin", if (it == page) target else it) }
        header("Referer")?.let {
            result.set("Referer", if (it.startsWith(page)) target + it.removePrefix(page) else it)
        }
        if (!keepAlive) result.set("Connection", "close")
        return result
    }

    /** The response as the page expects it on its own origin. */
    fun forPage(route: Route, keepAlive: Boolean): Head {
        val result = Head(start, headers.toMutableList())
        header("Location")?.let {
            if (it.startsWith(route.targetOrigin)) {
                result.set(
                    "Location",
                    route.pageOrigin + it.removePrefix(route.targetOrigin),
                )
            }
        }
        // The page origin is plain http: WebView drops Secure cookies there
        val cookies = headers.filter { it.first.equals("Set-Cookie", ignoreCase = true) }.map { it.second }
        result.set("Set-Cookie", null)
        cookies.forEach {
            result.headers += "Set-Cookie" to it.replace(SECURE, "").replace(SAME_SITE_NONE, "; SameSite=Lax")
        }
        result.set("Strict-Transport-Security", null)
        result.set("Alt-Svc", null)
        if (!keepAlive) {
            result.set("Connection", "close")
            result.set("Keep-Alive", null)
        }
        return result
    }

    companion object {
        private val SECURE = Regex(";\\s*Secure(?=;|$)", RegexOption.IGNORE_CASE)
        private val SAME_SITE_NONE = Regex(";\\s*SameSite=None", RegexOption.IGNORE_CASE)

        /** Reads up to the empty line that ends a head, or null when the stream ended first. */
        fun read(input: InputStream): Head? {
            val bytes = java.io.ByteArrayOutputStream()
            var matched = 0
            while (matched < 4) {
                val b = input.read()
                if (b < 0) return null
                bytes.write(b)
                if (bytes.size() > MAX_HEAD_BYTES) throw IOException("Head too large")
                matched = when {
                    b == '\r'.code && (matched == 0 || matched == 2) -> matched + 1
                    b == '\n'.code && (matched == 1 || matched == 3) -> matched + 1
                    b == '\r'.code -> 1
                    else -> 0
                }
            }
            val lines = bytes.toString(Charsets.ISO_8859_1.name()).split("\r\n").filter { it.isNotEmpty() }
            val headers = lines.drop(1).mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) null else line.substring(0, colon).trim() to line.substring(colon + 1).trim()
            }
            return Head(lines.firstOrNull() ?: return null, headers.toMutableList())
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
