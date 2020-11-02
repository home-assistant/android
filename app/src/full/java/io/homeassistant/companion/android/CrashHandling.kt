package io.homeassistant.companion.android

import android.content.Context
import io.sentry.android.core.SentryAndroid
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun initCrashReporting(context: Context) {
    // Don't init on debug builds
    if (BuildConfig.DEBUG)
        return

    SentryAndroid.init(context) { options ->
        options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}"
        options.isEnableSessionTracking = true
        options.isEnableNdk = false
        options.dsn = "https://2d646f40f9574e0b9579e301a69bb030@o427061.ingest.sentry.io/5372876"
        options.setBeforeSend { event, hint ->
            return@setBeforeSend when (hint) {
                is SocketTimeoutException -> null
                is ConnectException -> null
                is UnknownHostException -> null
                else -> event
            }
        }
    }
}
