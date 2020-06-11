package io.homeassistant.companion.android.util.extensions

import io.homeassistant.companion.android.resources.BuildConfig
import java.lang.Exception

inline fun <T> catch(action: () -> T): T? {
    return try {
        action()
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            e.printStackTrace()
        }
        null
    }
}
