package io.homeassistant.companion.android.util.extensions

import java.net.URL
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun URL?.handle(input: String): URL? {
    return if (input.isAbsoluteUrl()) {
        URL(input)
    } else {
        this?.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments(input.trimStart('/'))
            ?.build()
            ?.toUrl()
    }
}

fun String?.isAbsoluteUrl(): Boolean {
    return Regex("^https?://").containsMatchIn(toString())
}
