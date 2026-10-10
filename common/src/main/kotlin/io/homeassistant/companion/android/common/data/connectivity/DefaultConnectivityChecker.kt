package io.homeassistant.companion.android.common.data.connectivity

import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.di.SuspendProvider
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.util.UrlUtil
import io.homeassistant.companion.android.util.sensitive
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber

@Serializable
private data class ManifestResponse(val name: String? = null) {
    fun isHomeAssistant(): Boolean = name == HOME_ASSISTANT_NAME

    companion object {
        private const val HOME_ASSISTANT_NAME = "Home Assistant"
    }
}

private val CONNECT_TIMEOUT = 5.seconds

/**
 * Budget for a single read of a response. A phone on a weak mobile connection regularly stalls for
 * several seconds in the middle of a response, and cutting that short reports a healthy server as
 * broken. The 30 seconds call timeout of the default client still caps the whole request.
 */
private val READ_TIMEOUT = 10.seconds

/**
 * Lazily builds and caches a single [OkHttpClient].
 *
 * Creation is guarded by a [Mutex] so concurrent callers share one instance instead of each building
 * their own.
 */
private class OkHttpClientProvider(private val defaultOkHttpClientProvider: SuspendProvider<OkHttpClient>) {
    @Volatile
    private var okHttpClient: OkHttpClient? = null
    private val okHttpClientMutex = Mutex()

    suspend operator fun invoke(): OkHttpClient {
        return okHttpClient ?: okHttpClientMutex.withLock {
            okHttpClient ?: configureOkHttpClientForChecker(defaultOkHttpClientProvider()).also { okHttpClient = it }
        }
    }

    /**
     * Preconfigures the provided [OkHttpClient] with timeouts for connectivity testing.
     */
    private fun configureOkHttpClientForChecker(client: OkHttpClient): OkHttpClient = client.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .readTimeout(READ_TIMEOUT)
        .build()
}

/**
 * Default implementation of [ConnectivityChecker] that performs real network operations.
 */
internal class DefaultConnectivityChecker @Inject constructor(
    defaultOkHttpClientProvider: SuspendProvider<OkHttpClient>,
) : ConnectivityChecker {

    private val okHttpClientProvider = OkHttpClientProvider(defaultOkHttpClientProvider)

    override suspend fun dns(hostname: String): ConnectivityCheckResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(CONNECT_TIMEOUT) {
                val addresses = InetAddress.getAllByName(hostname)
                val addressList = addresses.joinToString(", ") { it.hostAddress ?: "" }
                ConnectivityCheckResult.Success(commonR.string.connection_check_dns, addressList)
            }
        } catch (e: TimeoutCancellationException) {
            // Must come before the CancellationException it extends, which would propagate instead
            // and leave the checks without a result.
            Timber.d(e, "DNS resolution timed out for ${sensitive(hostname)}")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_dns_timeout)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "DNS resolution failed for ${sensitive(hostname)}")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_dns)
        }
    }

    override suspend fun port(hostname: String, port: Int): ConnectivityCheckResult = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(hostname, port), CONNECT_TIMEOUT.inWholeMilliseconds.toInt())
            }
            ConnectivityCheckResult.Success(commonR.string.connection_check_port, port.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            Timber.d(e, "Port $port timed out on ${sensitive(hostname)}")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_port_timeout)
        } catch (e: Exception) {
            Timber.d(e, "Port $port not reachable on ${sensitive(hostname)}")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_port)
        }
    }

    override suspend fun tls(url: String): ConnectivityCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .head() // Don't get the body as we are only checking TLS
                .build()
            okHttpClientProvider().newCall(request).execute().use { response ->
                val handshake = response.handshake
                if (handshake != null) {
                    Timber.d("TLS check success for ${sensitive(url)} with ${handshake.tlsVersion}")
                    ConnectivityCheckResult.Success(commonR.string.connection_check_tls_success)
                } else {
                    Timber.d("Connection succeeded but no TLS handshake for ${sensitive(url)}")
                    ConnectivityCheckResult.Failure(commonR.string.connection_check_error_tls)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedIOException) {
            Timber.d(e, "TLS handshake timed out for ${sensitive(url)}")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_tls_timeout)
        } catch (e: Exception) {
            Timber.d(e, "TLS check failed for ${sensitive(url)}")
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_tls)
        }
    }

    override suspend fun homeAssistant(url: String): ManifestCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${UrlUtil.extractBaseUrl(url)}manifest.json")
                .build()
            okHttpClientProvider().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.d("Manifest request to ${sensitive(url)} answered with HTTP ${response.code}")
                    return@use ManifestCheckResult.NotReached(
                        ConnectivityCheckResult.Failure(
                            commonR.string.connection_check_error_http_status,
                            response.code.toString(),
                        ),
                    )
                }
                verifyManifest(response, url)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedIOException) {
            // Covers both the read timeout and the call timeout of the client.
            Timber.d(e, "Manifest request timed out for ${sensitive(url)}")
            ManifestCheckResult.NotReached(
                ConnectivityCheckResult.Failure(commonR.string.connection_check_error_server_timeout),
            )
        } catch (e: IOException) {
            Timber.d(e, "Manifest request failed for ${sensitive(url)}")
            ManifestCheckResult.NotReached(
                ConnectivityCheckResult.Failure(commonR.string.connection_check_error_server),
            )
        }
    }

    /**
     * Reads the manifest of an answered request and tells whether it belongs to a Home Assistant instance.
     */
    private fun verifyManifest(response: Response, url: String): ManifestCheckResult = try {
        val manifest = kotlinJsonMapper.decodeFromString<ManifestResponse>(response.body.string())

        if (manifest.isHomeAssistant()) {
            ManifestCheckResult.Verified
        } else {
            Timber.d("Manifest name mismatch: ${manifest.name}")
            ManifestCheckResult.NotVerified(
                ConnectivityCheckResult.Failure(commonR.string.connection_check_error_not_home_assistant),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: InterruptedIOException) {
        Timber.d(e, "Reading the manifest of ${sensitive(url)} timed out")
        ManifestCheckResult.NotVerified(
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_server_timeout),
        )
    } catch (e: Exception) {
        // An interrupted download, or a document that is not a manifest at all.
        Timber.d(e, "Manifest of ${sensitive(url)} could not be read")
        ManifestCheckResult.NotVerified(
            ConnectivityCheckResult.Failure(commonR.string.connection_check_error_manifest),
        )
    }
}
