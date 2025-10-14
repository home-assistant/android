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

    fun handle(base: URL?, input: String): URL? {
        val asURI = try {
            URI(input.removePrefix("homeassistant://navigate/"))
        } catch (e: Exception) {
            Timber.w("Invalid input, returning base only")
            null
        }
        return when {
            asURI == null -> {
                base
            }

            isAbsoluteUrl(input) -> {
                asURI.toURL()
            }

            else -> { // Input is relative to base URL
                val builder = base
                    ?.toHttpUrlOrNull()
                    ?.newBuilder()
                if (!asURI.path.isNullOrBlank()) builder?.addPathSegments(asURI.path.trim().removePrefix("/"))
                if (!asURI.query.isNullOrBlank()) builder?.query(asURI.query.trim())
                if (!asURI.fragment.isNullOrBlank()) builder?.fragment(asURI.fragment.trim())
                builder?.build()?.toUrl()
            }
        }
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
 * Determines if this URL uses a publicly accessible Fully Qualified Domain Name (FQDN).
 *
 * A URL is considered to use a public FQDN if:
 * 1. Its hostname does NOT end with a known local TLD (`.local`, `.lan`, `.home`, `.internal`,
 *    `.localdomain`), AND
 * 2. When resolved via DNS, ALL of its IP addresses are public (not private RFC 1918 addresses,
 *    not loopback, not link-local, and not any-local addresses).
 *
 * This function performs DNS resolution on the IO dispatcher and may block briefly while
 * resolving the hostname.
 *
 * @return `true` if the URL uses a public FQDN, `false` if it uses a local/private FQDN or
 *         if DNS resolution fails.
 */
suspend fun URL.usesPublicFqdn(): Boolean {
    return usesPublicFqdn(host)
}

private suspend fun usesPublicFqdn(fqdn: String): Boolean {
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
