package io.homeassistant.companion.android.util

import android.net.Uri
import android.util.Log
import io.homeassistant.companion.android.common.data.MalformedHttpUrlException
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI
import java.net.URL

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
                    e.message
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
            Log.w("UrlUtil", "Invalid input, returning base only")
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
                builder?.build()?.toUrl()
            }
        }
    }

    fun isAbsoluteUrl(it: String?): Boolean {
        return Regex("^https?://").containsMatchIn(it.toString())
    }

    /** @return `true` if both URLs have the same 'base': an equal protocol, host, port and userinfo */
    fun URL.baseIsEqual(other: URL?): Boolean =
        if (other == null) {
            false
        } else {
            host?.lowercase() == other.host?.lowercase() &&
                port.let { if (it == -1) defaultPort else it } == other.port.let { if (it == -1) defaultPort else it } &&
                protocol?.lowercase() == other.protocol?.lowercase() &&
                userInfo == other.userInfo
        }

    fun splitNfcTagId(it: Uri?): String? {
        val matches =
            Regex("^https?://www\\.home-assistant\\.io/tag/(.*)").find(
                it.toString()
            )
        return matches?.groups?.get(1)?.value
    }
}
