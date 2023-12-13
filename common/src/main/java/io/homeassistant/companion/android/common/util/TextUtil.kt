package io.homeassistant.companion.android.common.util

import okio.ByteString.Companion.toByteString
import java.util.Locale

fun ByteArray.toHexString(): String {
    return toByteString().hex().uppercase()
}

fun String.capitalize(locale: Locale) =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
