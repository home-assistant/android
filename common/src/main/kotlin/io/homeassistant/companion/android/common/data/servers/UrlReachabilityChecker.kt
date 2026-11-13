package io.homeassistant.companion.android.common.data.servers

import androidx.annotation.VisibleForTesting
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import timber.log.Timber

/**
 * Checks whether a server URL currently answers, used to pick the first working URL out of the
 * ordered list of URLs configured for a server.
 */
internal interface UrlReachabilityChecker {
    /**
     * @return `true` if [url] answered with any HTTP response within the timeout, whatever the
     *   status code. A response, even an error one, proves that the server can be reached.
     */
    suspend fun isReachable(url: HttpUrl): Boolean

    /** Forgets all cached results, to be called when the network changed. */
    fun invalidate()
}

@Singleton
internal class UrlReachabilityCheckerImpl @VisibleForTesting constructor(
    private val homeAssistantApis: HomeAssistantApis,
    private val backgroundDispatcher: CoroutineDispatcher,
) : UrlReachabilityChecker {

    @Inject
    constructor(homeAssistantApis: HomeAssistantApis) : this(homeAssistantApis, Dispatchers.IO)

    private val cache = ReachabilityCache()

    override suspend fun isReachable(url: HttpUrl): Boolean = cache.getOrProbe(url, ::probe)

    override fun invalidate() = cache.clear()

    private suspend fun probe(url: HttpUrl): Boolean = withContext(backgroundDispatcher) {
        try {
            val client = homeAssistantApis.getOkHttpClient().newBuilder()
                .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            client.newCall(Request.Builder().url(url).head().build()).execute().use { true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "URL not reachable")
            false
        }
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
