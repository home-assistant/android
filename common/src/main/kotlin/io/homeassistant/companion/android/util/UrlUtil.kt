package io.homeassistant.companion.android.util

import android.net.Uri
import io.homeassistant.companion.android.common.data.MalformedHttpUrlException
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

object UrlUtil {
    fun formattedUrlString(url: String): String {
        return if (url == "") {
            throw MalformedHttpUrlException()
        } else {
            try {
                val httpUrl = url.toHttpUrl()
                HttpUrl.Builder()
                    .scheme(httpUrl.scheme)
                    .host(httpUrl.host)
                    .port(httpUrl.port)
                    .toString()
            } catch (e: IllegalArgumentException) {
                throw MalformedHttpUrlException(
                    e.message,
                )
            }
        }
    }

    fun buildAuthenticationUrl(url: String): String {
        return url.toHttpUrlOrNull()!!
            .newBuilder()
            .addPathSegments("auth/authorize")
            .addEncodedQueryParameter("response_type", "code")
            .addEncodedQueryParameter("client_id", AuthenticationService.CLIENT_ID)
            .build()
            .toString()
    }

    /**
     * Resolves a URL input string against a base URL.
     *
     * @param base The base URL to resolve relative URLs against. Can be null if input is absolute.
     * @param input The URL string to resolve. Supported formats:
     *   - Absolute URL (http://... or https://...)
     *   - Relative path to be resolved against base
     *   - Deep link URL with homeassistant://navigate/ prefix
     * @return The resolved URL, the base URL if input is invalid, or null if resolution fails
     */
    fun handle(base: URL?, input: String): URL? {
        val normalizedInput = input.removePrefix("homeassistant://navigate/")

        val uri = try {
            URI(normalizedInput)
        } catch (e: Exception) {
            Timber.w(e, "Invalid URI input: $normalizedInput")
            return base
        }

        return when {
            isAbsoluteUrl(input) -> {
                uri.runCatching { toURL() }
                    .onFailure { Timber.w(it, "Failed to convert URI to URL: $normalizedInput") }
                    .getOrNull()
            }

            else -> buildRelativeUrl(base, uri)
        }
    }

    private fun buildRelativeUrl(base: URL?, uri: URI): URL? {
        val builder = base?.toHttpUrlOrNull()?.newBuilder() ?: return null

        return builder.apply {
            uri.path?.takeIf { it.isNotBlank() }?.let {
                addPathSegments(it.trim().removePrefix("/"))
            }
            uri.query?.takeIf { it.isNotBlank() }?.let {
                query(it.trim())
            }
            uri.fragment?.takeIf { it.isNotBlank() }?.let {
                fragment(it.trim())
            }
        }.build().toUrl()
    }

    fun isAbsoluteUrl(it: String?): Boolean {
        return Regex("^https?://").containsMatchIn(it.toString())
    }

    /** @return `true` if both URLs have the same 'base': an equal protocol, host, port and userinfo */
    fun URL.baseIsEqual(other: URL?): Boolean = if (other == null) {
        false
    } else {
        host?.lowercase() == other.host?.lowercase() &&
            port.let {
                if (it ==
                    -1
                ) {
                    defaultPort
                } else {
                    it
                }
            } == other.port.let { if (it == -1) defaultPort else it } &&
            protocol?.lowercase() == other.protocol?.lowercase() &&
            userInfo == other.userInfo
    }

    fun splitNfcTagId(it: Uri?): String? {
        val matches =
            Regex("^https?://www\\.home-assistant\\.io/tag/(.*)").find(
                it.toString(),
            )
        return matches?.groups?.get(1)?.value
    }
}

/**
 * Determines if this URL is publicly accessible using Fully Qualified Domain Name (FQDN) or a public IP.
 *
 * A URL is considered to be publicly accessible if:
 * 1. Its hostname does NOT end with a known local TLD (`.local`, `.lan`, `.home`, `.internal`,
 *    `.localdomain`), AND
 * 2. When resolved via DNS, ALL of its IP addresses are public (not private RFC 1918 addresses,
 *    not loopback, not link-local, and not any-local addresses).
 *
 * This function performs DNS resolution on the IO dispatcher and may block briefly while
 * resolving the hostname.
 *
 * @return `true` if the URL is publicly accessible, `false` if it is local/private or
 *         if DNS resolution fails.
 */
suspend fun URL.isPubliclyAccessible(): Boolean {
    return isPubliclyAccessible(host)
}

private suspend fun isPubliclyAccessible(fqdn: String): Boolean {
    // Check TLD
    val localTlds = listOf(".local", ".lan", ".home", ".internal", ".localdomain")
    if (localTlds.any { fqdn.endsWith(it, ignoreCase = true) }) {
        return false
    }

    // Resolve and check IP
    return try {
        val addresses = withContext(Dispatchers.IO) {
            InetAddress.getAllByName(fqdn)
        }
        addresses.none { it.isPrivateOrLocal() }
    } catch (e: UnknownHostException) {
        false
    }
}

private fun InetAddress.isPrivateOrLocal(): Boolean {
    return this.isSiteLocalAddress ||
        // Private IP ranges (RFC 1918)
        this.isLoopbackAddress ||
        // 127.0.0.0/8 or ::1
        this.isLinkLocalAddress ||
        // 169.254.0.0/16 or fe80::/10
        this.isAnyLocalAddress // 0.0.0.0 or ::
}
