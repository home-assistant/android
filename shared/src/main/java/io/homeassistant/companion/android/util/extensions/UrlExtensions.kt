package io.homeassistant.companion.android.util.extensions

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URL

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