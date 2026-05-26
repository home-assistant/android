package io.homeassistant.companion.android.common.data.network

import java.net.URI

/**
 * Extracts the hostname from a URL string for WebView error matching.
 */
fun String.toLogicalHostname(): String? {
    return try {
        URI(this).host
    } catch (_: Exception) {
        null
    }
}
