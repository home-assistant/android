package io.homeassistant.companion.android

import android.content.Context
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.SentryOptions.BeforeSendCallback
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.User
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLProtocolException
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import timber.log.Timber

fun initCrashReporting(context: Context, enabled: Boolean) {
    // Don't init on debug builds or when disabled
    if (!shouldEnableCrashHandling(enabled)) {
        Timber.i("Sentry crash reporting disabled")
        return
    }

    SentryAndroid.init(context) { options ->
        options.isEnableAutoSessionTracking = true

        // We are using Sentry Android core library that doesn't come with the support for NDK.
        options.isEnableNdk = false

        val ignoredEvents = arrayOf(
            ConnectException::class.java,
            SocketTimeoutException::class.java,
            SSLException::class.java,
            SSLHandshakeException::class.java,
            SSLPeerUnverifiedException::class.java,
            SSLProtocolException::class.java,
            UnknownHostException::class.java,
        )

        options.ignoredExceptionsForType.addAll(ignoredEvents)

        options.beforeSend = object : BeforeSendCallback {
            override fun execute(event: SentryEvent, p1: Hint): SentryEvent? {
                if (event.user != null) {
                    event.user = User().apply {
                        // The only information we want to keep about the user is his ID
                        id = event.user?.id
                    }
                }
                return event
            }
        }
        options.logObjectDetails()
    }
}

/**
 * This extension is a simple helper that logs all the properties of a given object.
 * In the context of Sentry it is useful to have a better visibility of the options set.
 *
 * We should avoid running this in production since it uses reflection and it is expensive.
 *
 * Currently:
 *
 * anrEnabled: true
 * anrReportInDebug: false
 * anrTimeoutIntervalMillis: 5000
 * attachAnrThreadDump: false
 * attachScreenshot: false
 * attachViewHierarchy: false
 * beforeScreenshotCaptureCallback: null
 * beforeViewHierarchyCaptureCallback: null
 * collectAdditionalContext: true
 * debugImagesLoader: io.sentry.android.core.NoOpDebugImagesLoader@OBJECT_REF
 * enableActivityLifecycleBreadcrumbs: true
 * enableActivityLifecycleTracingAutoFinish: true
 * enableAppComponentBreadcrumbs: true
 * enableAppLifecycleBreadcrumbs: true
 * enableAutoActivityLifecycleTracing: true
 * enableAutoTraceIdGeneration: true
 * enableFramesTracking: true
 * enableNdk: false
 * enableNetworkEventBreadcrumbs: true
 * enablePerformanceV2: true
 * enableRootCheck: true
 * enableScopeSync: true
 * enableSystemEventBreadcrumbs: true
 * frameMetricsCollector: io.sentry.android.core.internal.util.SentryFrameMetricsCollector@OBJECT_REF
 * nativeSdkName: null
 * ndkHandlerStrategy: SENTRY_HANDLER_STRATEGY_DEFAULT
 * reportHistoricalAnrs: false
 * startupCrashDurationThresholdMillis: 2000
 * startupCrashFlushTimeoutMillis: 5000
 */
private fun SentryOptions.logObjectDetails() {
    if (!BuildConfig.DEBUG) return
    val kClass = this::class
    val objectDetail = buildString {
        appendLine("Class: ${kClass.simpleName}")
        kClass.declaredMemberProperties.forEach { property ->
            property.isAccessible = true // Make private properties accessible
            val value = property.getter.call(this@logObjectDetails)
            appendLine("${property.name}: $value")
        }
    }
    Timber.i("Current value of options: $objectDetail")
}

private fun shouldEnableCrashHandling(enabled: Boolean) = !BuildConfig.DEBUG && enabled
