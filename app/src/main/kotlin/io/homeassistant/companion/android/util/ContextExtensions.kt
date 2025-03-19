package io.homeassistant.companion.android.util

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.TypedValue
import androidx.activity.ComponentActivity
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

fun Context.getAttribute(attr: Int, fallbackAttr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return if (value.resourceId != 0) attr else fallbackAttr
}

fun Context.getHexForColor(@ColorRes attr: Int): String {
    val color = ContextCompat.getColor(this, attr)
    return String.format("#%06X", 0xFFFFFF and color) // https://stackoverflow.com/a/6540378
}

/** @return `true` if the device has an active network configured to reach the internet (not validated) */
fun Context.hasActiveConnection(): Boolean {
    val cm = getSystemService<ConnectivityManager>() ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        cm.activeNetwork?.let {
            cm.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } ?: false
    } else {
        @Suppress("DEPRECATION")
        cm.activeNetworkInfo?.isConnected == true
    }
}

fun Context.getActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}
