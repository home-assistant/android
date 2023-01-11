package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.data.MalformedHttpUrlException
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlUtil {
    fun formattedUrlString(url: String): String {
        return if (url == "") throw MalformedHttpUrlException() else try {
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

    fun buildAuthenticationUrl(url: String, redirect: String): String {
        return url.toHttpUrlOrNull()!!
            .newBuilder()
            .addPathSegments("auth/authorize")
            .addEncodedQueryParameter("response_type", "code")
            .addEncodedQueryParameter("client_id", AuthenticationService.CLIENT_ID)
            .addEncodedQueryParameter("redirect_uri", redirect)
            .build()
            .toString()
    }
}
