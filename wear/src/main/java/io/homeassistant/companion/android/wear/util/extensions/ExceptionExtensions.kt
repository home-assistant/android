package io.homeassistant.companion.android.wear.util.extensions

import io.homeassistant.companion.android.wear.BuildConfig
import java.lang.Exception

inline fun <T> catch (action: () -> T): T? {
    return try {
        action()
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            e.printStackTrace()
        }
        null
    }
}