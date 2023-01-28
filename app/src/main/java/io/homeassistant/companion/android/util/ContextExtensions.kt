package io.homeassistant.companion.android.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.TypedValue
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat


fun Context.getAttribute(attr: Int, fallbackAttr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return if (value.resourceId != 0) attr else fallbackAttr
}

fun Context.getHexForColor(@ColorRes attr: Int): String {
    val color = ContextCompat.getColor(this, attr)
    return String.format("#%06X", 0xFFFFFF and color) // https://stackoverflow.com/a/6540378
}

fun Context.getAppMetaDataString(metaName: String): String {
    return try {
        packageManager
            .getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA
            ).metaData.getString(metaName, "")
    } catch (e: Exception) {
        ""
    }
}
