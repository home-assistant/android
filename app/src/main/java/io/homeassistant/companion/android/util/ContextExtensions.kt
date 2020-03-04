package io.homeassistant.companion.android.util

import android.content.Context
import android.util.TypedValue

fun Context.getAttribute(attr: Int, fallbackAttr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return if (value.resourceId != 0) attr else fallbackAttr
}
