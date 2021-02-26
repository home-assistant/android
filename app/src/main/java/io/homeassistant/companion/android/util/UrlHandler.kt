package io.homeassistant.companion.android.util

import java.net.URL
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlHandler {
    fun handle(base: URL?, input: String): URL? {
        return when {
            isAbsoluteUrl(input) -> {
                URL(input)
            }
            input.startsWith("homeassistant://navigate/") -> {
                base?.toHttpUrlOrNull()
                    ?.newBuilder()
                    ?.addPathSegments(input.removePrefix("homeassistant://navigate/"))
                    ?.build()
                    ?.toUrl()
            }
            else -> {
                base?.toHttpUrlOrNull()
                    ?.newBuilder()
                    ?.addPathSegments(input.trimStart('/'))
                    ?.build()
                    ?.toUrl()
            }
        }
    }

    fun isAbsoluteUrl(it: String?): Boolean {
        return Regex("^https?://").containsMatchIn(it.toString())
    }

    fun splitNfcTagId(it: String?): String? {
        val matches =
            Regex("^https?://www\\.home-assistant\\.io/tag/(.*)").find(
                it.toString()
            )
        return matches?.groups?.get(1)?.value
    }
}
