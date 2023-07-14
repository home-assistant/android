package io.homeassistant.companion.android.common.util

import okhttp3.internal.and
import java.util.Locale

private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()

fun ByteArray.toHexString(): String { // From https://stackoverflow.com/a/9855338/4214819
    val hexChars = CharArray(this.size * 2)
    for (j in 0 until this.size) {
        val v = get(j) and 0xFF
        hexChars[j * 2] = HEX_ARRAY[v ushr 4]
        hexChars[j * 2 + 1] = HEX_ARRAY[v and 0x0F]
    }
    return String(hexChars)
}

fun String.capitalize(locale: Locale) =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
