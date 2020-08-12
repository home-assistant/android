package io.homeassistant.companion.android.util

import java.net.URL
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlHandler {
    fun handle(base: URL?, input: String): URL? {
        return if (isAbsoluteUrl(input)) {
            URL(input)
        } else {
            base?.toHttpUrlOrNull()
                ?.newBuilder()
                ?.addPathSegments(input.trimStart('/'))
                ?.build()
                ?.toUrl()
        }
    }

    fun isAbsoluteUrl(it: String?): Boolean {
        return Regex("^https?://").containsMatchIn(it.toString())
    }

    fun splitNfcTagId(it: String?): String? {
        val matches =
            Regex("^https?://www\\.home-assistant\\.io/tag/([0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{12})").find(
                it.toString()
            )
        return matches?.groups?.get(1)?.value
    }
}
