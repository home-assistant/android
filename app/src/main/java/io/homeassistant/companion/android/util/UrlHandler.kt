package io.homeassistant.companion.android.util

import android.net.Uri
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URL

object UrlHandler {
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
