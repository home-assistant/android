package io.homeassistant.companion.android

import android.content.Context
import android.util.Log
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLProtocolException

private const val FATAL_CRASH_FILE = "/fatalcrash/last_crash"

fun initCrashReporting(context: Context, enabled: Boolean) {
    // Don't init on debug builds or when disabled
    if (!shouldEnableCrashHandling(enabled)) {
        return
    }

    SentryAndroid.init(context) { options ->
        options.isEnableAutoSessionTracking = true
        options.isEnableNdk = false

        options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
            if (event.isCrashed && event.throwable != null) {
                try {
                    val crashFile = File(context.applicationContext.cacheDir.absolutePath + FATAL_CRASH_FILE)
                    if (!crashFile.exists()) {
                        crashFile.parentFile?.mkdirs()
                        crashFile.createNewFile()
                    }
                    val stacktraceWriter = PrintWriter(crashFile)
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(event.timestamp)
                    stacktraceWriter.print("$timestamp: ")
                    event.throwable?.printStackTrace(stacktraceWriter)
                    stacktraceWriter.close()
                } catch (e: Exception) {
                    Log.i("CrashHandling", "Tried saving fatal crash but encountered exception", e)
                }
            }

            return@BeforeSendCallback event
        }

        val ignoredEvents = arrayOf(
            ConnectException::class.java,
            SocketTimeoutException::class.java,
            SSLException::class.java,
            SSLHandshakeException::class.java,
            SSLPeerUnverifiedException::class.java,
            SSLProtocolException::class.java,
            UnknownHostException::class.java
        )

        options.ignoredExceptionsForType.addAll(ignoredEvents)
    }
}

suspend fun getLatestFatalCrash(context: Context, enabled: Boolean): String? = withContext(Dispatchers.IO) {
    if (!shouldEnableCrashHandling(enabled)) {
        return@withContext null
    }

    var toReturn: String? = null
    try {
        val crashFile = File(context.applicationContext.cacheDir.absolutePath + FATAL_CRASH_FILE)
        if (crashFile.exists() &&
            crashFile.lastModified() >= (System.currentTimeMillis() - TimeUnit.HOURS.toMillis(12))
        ) { // Existing, recent file
            toReturn = crashFile.readText().trim().ifBlank { null }
        }
    } catch (e: Exception) {
        Log.e("CrashHandling", "Encountered exception while reading crash log file", e)
    }
    return@withContext toReturn
}

private fun shouldEnableCrashHandling(enabled: Boolean) = !BuildConfig.DEBUG && enabled
