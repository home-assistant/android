package io.homeassistant.companion.android.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FATAL_CRASH_FILE = "/fatalcrash/last_crash"

fun initCrashSaving(context: Context) {
    val handler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
        // Try saving the crash in a file
        try {
            val crashFile = File(context.applicationContext.cacheDir.absolutePath + FATAL_CRASH_FILE)
            if (!crashFile.exists()) {
                crashFile.parentFile?.mkdirs()
                crashFile.createNewFile()
            }

            crashFile.writeText(
                """|Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())}
                    |Thread: ${thread.name}
                    |Exception: ${exception.stackTraceToString()}
                """.trimMargin()
            )
        } catch (e: Exception) {
            Log.i("CrashSaving", "Tried saving fatal crash but encountered exception", e)
        }

        // Send to crash handling and/or system (and crash)
        handler?.uncaughtException(thread, exception)
    }
}

suspend fun getLatestFatalCrash(context: Context): String? = withContext(Dispatchers.IO) {
    var toReturn: String? = null
    try {
        val crashFile = File(context.applicationContext.cacheDir.absolutePath + FATAL_CRASH_FILE)
        if (crashFile.exists() &&
            crashFile.lastModified() >= (System.currentTimeMillis() - TimeUnit.HOURS.toMillis(12))
        ) { // Existing, recent file
            toReturn = crashFile.readText().trim().ifBlank { null }
        }
    } catch (e: Exception) {
        Log.e("CrashSaving", "Encountered exception while reading crash log file", e)
    }
    return@withContext toReturn
}
