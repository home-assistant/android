package io.homeassistant.companion.android.common.data

import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/** Applies the current timeout when creating a call while sharing the client's connection pool. */
internal class RequestTimeoutCallFactory(private val client: OkHttpClient, private val timeout: () -> Duration) :
    Call.Factory {
    override fun newCall(request: Request): Call {
        val timeoutMillis = timeout().inWholeMilliseconds
        return client.newBuilder()
            .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)
    }
}
