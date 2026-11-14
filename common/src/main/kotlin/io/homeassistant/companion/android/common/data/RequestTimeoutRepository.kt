package io.homeassistant.companion.android.common.data

import io.homeassistant.companion.android.di.qualifiers.NamedThemesStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val PREF_REQUEST_TIMEOUT_SECONDS = "network_request_timeout_seconds"

/** Stores the timeout used for new native Home Assistant API requests. */
@Singleton
class RequestTimeoutRepository @Inject constructor(@NamedThemesStorage private val localStorage: LocalStorage) {
    private val mutex = Mutex()
    private var loaded = false
    private val currentTimeout = MutableStateFlow(DEFAULT_REQUEST_TIMEOUT)

    /** Current timeout, initialized by [load] before API requests are created. */
    val timeout = currentTimeout.asStateFlow()

    /** Loads the saved timeout once, falling back to 30 seconds for unknown values. */
    suspend fun load() {
        mutex.withLock {
            if (!loaded) {
                val saved = localStorage.getLong(PREF_REQUEST_TIMEOUT_SECONDS)?.seconds
                currentTimeout.value = saved?.takeIf { it in REQUEST_TIMEOUT_OPTIONS } ?: DEFAULT_REQUEST_TIMEOUT
                loaded = true
            }
        }
    }

    /** Saves and applies a supported timeout even if the calling settings screen closes during the write. */
    suspend fun setTimeout(timeout: Duration) {
        require(timeout in REQUEST_TIMEOUT_OPTIONS) { "Unsupported request timeout" }
        mutex.withLock {
            withContext(NonCancellable) {
                localStorage.putLong(PREF_REQUEST_TIMEOUT_SECONDS, timeout.inWholeSeconds)
                currentTimeout.value = timeout
                loaded = true
            }
        }
    }
}

/** Default native API request timeout, preserving the existing behavior. */
val DEFAULT_REQUEST_TIMEOUT = 30.seconds

/** Supported request timeouts; none disable the timeout entirely. */
val REQUEST_TIMEOUT_OPTIONS = listOf(5.seconds, 10.seconds, 15.seconds, DEFAULT_REQUEST_TIMEOUT, 60.seconds)
