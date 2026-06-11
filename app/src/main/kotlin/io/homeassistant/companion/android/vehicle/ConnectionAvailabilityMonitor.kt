package io.homeassistant.companion.android.vehicle

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.servers.ServerManager
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

sealed interface ConnectionAvailability {
    data object Available : ConnectionAvailability
    data object Unavailable : ConnectionAvailability
}

interface ConnectionAvailabilityMonitor {
    fun observeAvailability(): Flow<ConnectionAvailability>
}

internal val GRACE_PERIOD: Duration = 10.seconds

internal val HEALTHY_POLL_INTERVAL: Duration = 15.seconds

internal val DEGRADED_POLL_INTERVAL: Duration = 1.seconds

@Singleton
internal class ConnectionAvailabilityMonitorImpl @Inject constructor(private val serverManager: ServerManager) :
    ConnectionAvailabilityMonitor {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAvailability(): Flow<ConnectionAvailability> {
        return haReachableFlow()
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

    private fun haReachableFlow(): Flow<Boolean> = flow {
        while (currentCoroutineContext().isActive) {
            val reachable = isHaReachable()
            emit(reachable)
            delay(if (reachable) HEALTHY_POLL_INTERVAL else DEGRADED_POLL_INTERVAL)
        }
    }.distinctUntilChanged()

    private suspend fun isHaReachable(): Boolean {
        return try {
            if (!serverManager.isRegistered()) {
                true
            } else {
                serverManager.webSocketRepository().sendPing()
            }
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
internal abstract class ConnectionAvailabilityMonitorModule {
    @Binds
    @Singleton
    abstract fun bindConnectionAvailabilityMonitor(
        impl: ConnectionAvailabilityMonitorImpl,
    ): ConnectionAvailabilityMonitor
}
