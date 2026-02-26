package io.homeassistant.companion.android.onboarding.locationforsecureconnection

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

/**
 * ViewModel for the security level configuration screen.
 */
@HiltViewModel(assistedFactory = LocationForSecureConnectionViewModelFactory::class)
class LocationForSecureConnectionViewModel @AssistedInject constructor(
    @Assisted private val serverId: Int,
    private val serverManager: ServerManager,
) : ViewModel() {
    val allowInsecureConnection: Flow<Boolean?> = flow {
        try {
            val value = serverManager.getServer(serverId)?.connection?.allowInsecureConnection
            emit(value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to get initial AllowInsecureConnection for server $serverId")
            emit(null)
        }
    }

    val hasPlainTextUrl: Flow<Boolean> = flow {
        try {
            val value = serverManager.getServer(serverId)?.connection?.hasPlainTextUrl ?: false
            emit(value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to get hasPlainTextUrl for server $serverId")
            emit(false)
        }
    }

    suspend fun allowInsecureConnection(allowInsecureConnection: Boolean) {
        try {
            serverManager.getServer(serverId)?.let { server ->
                serverManager.updateServer(
                    server.copy(
                        connection = server.connection.copy(
                            allowInsecureConnection = allowInsecureConnection,
                        ),
                    ),
                )
            }
        } catch (e: Exception) {
            Timber.e(
                e,
                "Something went wrong while setting AllowInsecureConnection to $allowInsecureConnection for server $serverId",
            )
        }
    }
}

@AssistedFactory
interface LocationForSecureConnectionViewModelFactory {
    fun create(serverId: Int): LocationForSecureConnectionViewModel
}
