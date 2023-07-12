package io.homeassistant.companion.android.util

import android.net.Uri
import io.homeassistant.companion.android.common.data.MalformedHttpUrlException
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
        return when {
            isAbsoluteUrl(input) -> {
                URL(input)
            }
            input.startsWith("homeassistant://navigate/") -> {
                (base.toString() + input.removePrefix("homeassistant://navigate/")).toHttpUrlOrNull()?.toUrl()
            }
            else -> {
                (base.toString() + input.removePrefix("/")).toHttpUrlOrNull()?.toUrl()
            }
        }
    }

    fun isAbsoluteUrl(it: String?): Boolean {
        return Regex("^https?://").containsMatchIn(it.toString())
    }

    fun splitNfcTagId(it: Uri?): String? {
        val matches =
            Regex("^https?://www\\.home-assistant\\.io/tag/(.*)").find(
                it.toString()
            )
        return matches?.groups?.get(1)?.value
    }
}
