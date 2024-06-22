package io.homeassistant.companion.android.common.util

import java.util.Locale
import okio.ByteString.Companion.toByteString

fun ByteArray.toHexString(): String {
    return toByteString().hex().uppercase()
}

fun String.capitalize(locale: Locale) =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
