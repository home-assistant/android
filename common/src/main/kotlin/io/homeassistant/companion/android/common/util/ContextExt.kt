package io.homeassistant.companion.android.common.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun Context.getSharedPreferencesSuspend(name: String, mode: Int = Context.MODE_PRIVATE): SharedPreferences {
    return withContext(Dispatchers.IO) {
        getSharedPreferences(
            name,
            mode,
        )
    }
}
