package io.homeassistant.companion.android

import android.content.Context
import io.sentry.android.core.SentryAndroid
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLProtocolException

fun initCrashReporting(context: Context, enabled: Boolean) {
    // Don't init on debug builds or when disabled
    if (BuildConfig.DEBUG || !enabled)
        return

    SentryAndroid.init(context) { options ->
        options.isEnableAutoSessionTracking = true
        options.isEnableNdk = false
        options.dsn = "https://2d646f40f9574e0b9579e301a69bb030@o427061.ingest.sentry.io/5372876"

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
