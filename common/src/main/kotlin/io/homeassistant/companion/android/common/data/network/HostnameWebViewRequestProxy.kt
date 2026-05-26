package io.homeassistant.companion.android.common.data.network

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Proxies selected WebView HTTP(S) requests through [OkHttpClient], which uses [NetworkAwareDns].
 *
 * Used as a fallback when [androidx.webkit.WebViewFeature.PROXY_OVERRIDE] is unavailable, for
 * example on older Android versions or outdated system WebView packages. Only GET and HEAD
 * requests are supported; WebSockets still rely on the system WebView stack.
 */
class HostnameWebViewRequestProxy @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    /**
     * Returns a [WebResourceResponse] when [request] targets [logicalHostname] and can be fetched
     * via OkHttp, or `null` to let the WebView handle the request normally.
     */
    fun intercept(logicalHostname: String?, request: WebResourceRequest): WebResourceResponse? {
        if (logicalHostname.isNullOrBlank()) {
            return null
        }
        val requestHost = request.url.host ?: return null
        if (!requestHost.equals(logicalHostname, ignoreCase = true)) {
            return null
        }

        val method = request.method.uppercase()
        if (method != "GET" && method != "HEAD") {
            Timber.d("Skipping WebView proxy for unsupported method %s to %s", method, logicalHostname)
            return null
        }

        return try {
            val okHttpRequest = Request.Builder()
                .url(request.url.toString())
                .method(method, null)
                .apply {
                    request.requestHeaders.forEach { (name, value) ->
                        addHeader(name, value)
                    }
                }
                .build()

            okHttpClient.newCall(okHttpRequest).execute().use { response ->
                if (!response.isSuccessful && response.code !in REDIRECT_STATUS_CODES) {
                    Timber.w(
                        "WebView proxy for %s returned HTTP %d",
                        request.url,
                        response.code,
                    )
                }
                val contentType = response.header("Content-Type")
                val mimeType = contentType?.substringBefore(";")?.trim() ?: DEFAULT_MIME_TYPE
                val encoding = contentType
                    ?.substringAfter("charset=", DEFAULT_ENCODING)
                    ?.trim()
                    ?.ifBlank { DEFAULT_ENCODING }
                    ?: DEFAULT_ENCODING
                val responseHeaders = response.headers.toMultimap()
                    .mapValues { entry -> entry.value.firstOrNull().orEmpty() }

                WebResourceResponse(
                    mimeType,
                    encoding,
                    response.code,
                    response.message.ifBlank { "OK" },
                    responseHeaders,
                    response.body.byteStream(),
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "WebView proxy failed for %s", request.url)
            null
        }
    }

    private companion object {
        private const val DEFAULT_MIME_TYPE = "text/html"
        private const val DEFAULT_ENCODING = "utf-8"
        private val REDIRECT_STATUS_CODES = 300..399
    }
}
