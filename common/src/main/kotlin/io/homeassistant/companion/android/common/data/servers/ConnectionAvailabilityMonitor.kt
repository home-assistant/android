package io.homeassistant.companion.android.common.data.servers

import androidx.annotation.VisibleForTesting
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.servers.ServerManager.Companion.SERVER_ID_ACTIVE
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import timber.log.Timber

@VisibleForTesting
internal val GRACE_PERIOD: Duration = 10.seconds

@VisibleForTesting
internal val HEALTHY_POLL_INTERVAL: Duration = 15.seconds

@VisibleForTesting
internal val DEGRADED_POLL_INTERVAL: Duration = 1.seconds

/**
 * Whether a Home Assistant server currently answers the app.
 */
sealed interface ConnectionAvailability {
    /** The server answers, or there is no server to reach. */
    data object Available : ConnectionAvailability

    /** The server has not answered for longer than the grace period. */
    data object Unavailable : ConnectionAvailability
}

/**
 * Reports whether a server can be reached over its WebSocket connection.
 *
 * The server is pinged periodically. A failed ping only turns into [ConnectionAvailability.Unavailable]
 * after a grace period without a successful ping, so short interruptions are not reported. Pings are
 * more frequent while the server does not answer, so recovery is reported quickly.
 *
 * A failed ping does not tell why the server is unreachable: the device may have no network, or the
 * server may be down.
 */
interface ConnectionAvailabilityMonitor {
    /**
     * Observes the availability of the server [serverId], or of the active server for [SERVER_ID_ACTIVE].
     *
     * Emits only on changes, starting with the current availability. When the server does not exist,
     * for instance before onboarding, [ConnectionAvailability.Available] is emitted.
     */
    fun observeAvailability(serverId: Int = SERVER_ID_ACTIVE): Flow<ConnectionAvailability>
}

@Singleton
internal class ConnectionAvailabilityMonitorImpl @Inject constructor(private val serverManager: ServerManager) :
    ConnectionAvailabilityMonitor {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAvailability(serverId: Int): Flow<ConnectionAvailability> {
        return reachableFlow(serverId)
            .transformLatest { reachable ->
                if (reachable) {
                    emit(ConnectionAvailability.Available)
                } else {
                    delay(GRACE_PERIOD)
                    emit(ConnectionAvailability.Unavailable)
                }
            }
            .distinctUntilChanged()
    }

    private fun reachableFlow(serverId: Int): Flow<Boolean> = flow {
        while (currentCoroutineContext().isActive) {
            val reachable = isReachable(serverId)
            emit(reachable)
            delay(if (reachable) HEALTHY_POLL_INTERVAL else DEGRADED_POLL_INTERVAL)
        }
    }.distinctUntilChanged()

    private suspend fun isReachable(serverId: Int): Boolean {
        return try {
            val server = serverManager.getServer(serverId) ?: return true
            serverManager.webSocketRepository(server.id).sendPing()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to ping WebSocket")
            false
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface ConnectionAvailabilityMonitorModule {
    @Binds
    fun bindConnectionAvailabilityMonitor(impl: ConnectionAvailabilityMonitorImpl): ConnectionAvailabilityMonitor
}
