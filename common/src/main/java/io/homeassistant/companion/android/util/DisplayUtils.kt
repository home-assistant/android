package io.homeassistant.companion.android.util

import android.content.res.Resources

object DisplayUtils {

    fun getScreenWidth(): Int {
        return Resources.getSystem().displayMetrics.widthPixels
    }
}
