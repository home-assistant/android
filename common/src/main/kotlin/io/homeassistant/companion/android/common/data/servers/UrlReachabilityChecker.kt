package io.homeassistant.companion.android.common.data.servers

import androidx.annotation.VisibleForTesting
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import io.homeassistant.companion.android.common.data.network.NetworkChangeObserver
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber

/**
 * Checks whether a server URL currently answers, used to pick the first working URL out of the
 * ordered list of URLs configured for a server.
 */
internal interface UrlReachabilityChecker {
    /**
     * @return `true` if [url] answered with any HTTP response within the timeout, whatever the
     *   status code. A response, even an error one, proves that the server can be reached.
     *   Redirects are not followed, so a redirect response is enough to count as reachable.
     */
    suspend fun isReachable(url: HttpUrl): Boolean
}

@Singleton
internal class UrlReachabilityCheckerImpl @VisibleForTesting constructor(
    private val homeAssistantApis: HomeAssistantApis,
    networkChangeObserver: NetworkChangeObserver,
    scope: CoroutineScope,
    private val cache: ReachabilityCache,
) : UrlReachabilityChecker {

    @Inject
    constructor(
        homeAssistantApis: HomeAssistantApis,
        networkChangeObserver: NetworkChangeObserver,
    ) : this(
        homeAssistantApis,
        networkChangeObserver,
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ReachabilityCache(),
    )

    init {
        // Results describe the network the device was on when probing, so they are dropped when it changes.
        // This must be the only place that clears the cache for network changes: the observer replays its
        // last event to every new subscriber, so clearing per subscriber would empty the cache for nothing.
        scope.launch {
            networkChangeObserver.observerNetworkChange.collect { cache.clear() }
        }
    }

    override suspend fun isReachable(url: HttpUrl): Boolean = cache.getOrProbe(url, ::probe)

    private suspend fun probe(url: HttpUrl): Boolean {
        return try {
            val client = homeAssistantApis.getOkHttpClient().newBuilder()
                .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                // A probe only asks whether a server answers. Following redirects could leave the checked URL,
                // for example from HTTPS to HTTP, which would bypass the filter for insecure URLs. The shared
                // cookie jar holds the session cookies of the frontend, so it must not take part in probes.
                .followRedirects(false)
                .followSslRedirects(false)
                .cookieJar(CookieJar.NO_COOKIES)
                .build()
            client.newCall(Request.Builder().url(url).head().build()).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "URL not reachable")
            false
        }
    }

    /** Suspends until the call got a response, cancelling the call when the coroutine is cancelled. */
    private suspend fun Call.await() = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                    continuation.resume(Unit)
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            },
        )
    }

    private companion object {
        const val PROBE_TIMEOUT_SECONDS = 3L
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface UrlReachabilityCheckerModule {
    @Binds
    fun bindUrlReachabilityChecker(impl: UrlReachabilityCheckerImpl): UrlReachabilityChecker
}
