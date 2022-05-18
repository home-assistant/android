package io.homeassistant.companion.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun initCrashReporting(context: Context, enabled: Boolean) {
    // Noop
}

suspend fun getLatestFatalCrash(context: Context, enabled: Boolean): String? = withContext(Dispatchers.IO) {
    // Noop
    return@withContext null
}
