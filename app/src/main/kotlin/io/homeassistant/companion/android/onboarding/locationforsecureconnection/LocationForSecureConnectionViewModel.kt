package io.homeassistant.companion.android.onboarding.locationforsecureconnection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
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

    private val server: Deferred<Server?> = viewModelScope.async {
        try {
            serverManager.getServer(serverId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to get the server $serverId")
            null
        }
    }

    val allowInsecureConnection: Flow<Boolean?> = flow {
        emit(server.await()?.connection?.allowInsecureConnection)
    }

    val hasPlainTextUrl: Flow<Boolean> = flow {
        emit(server.await()?.connection?.hasPlainTextUrl ?: false)
    }

    suspend fun allowInsecureConnection(allowInsecureConnection: Boolean) {
        try {
            server.await()?.let { server ->
                serverManager.updateServer(
                    server.copy(
                        connection = server.connection.copy(
                            allowInsecureConnection = allowInsecureConnection,
                        ),
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
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
