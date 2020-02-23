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
}
