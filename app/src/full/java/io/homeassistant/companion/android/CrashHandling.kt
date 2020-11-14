package io.homeassistant.companion.android

import android.content.Context
import io.sentry.android.core.SentryAndroid
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLProtocolException

fun initCrashReporting(context: Context, enabled: Boolean) {
    // Don't init on debug builds or when disabled
    if (BuildConfig.DEBUG || !enabled)
        return

    SentryAndroid.init(context) { options ->
        options.isEnableSessionTracking = true
        options.isEnableNdk = false
        options.dsn = "https://2d646f40f9574e0b9579e301a69bb030@o427061.ingest.sentry.io/5372876"
        options.setBeforeSend { event, hint ->
            return@setBeforeSend when (hint) {
                is ConnectException,
                is SocketTimeoutException,
                is SSLException,
                is SSLHandshakeException,
                is SSLProtocolException,
                is UnknownHostException -> null
                else -> event
            }
        }
    }
}
