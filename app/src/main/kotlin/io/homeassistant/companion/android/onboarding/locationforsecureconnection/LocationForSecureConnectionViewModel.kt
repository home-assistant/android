package io.homeassistant.companion.android.onboarding.locationforsecureconnection

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
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

    // Read on every collection so the screen shows the stored choice when navigating back to it.
    private suspend fun getServer(): Server? = try {
        serverManager.getServer(serverId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Failed to get the server $serverId")
        null
    }

    val allowInsecureConnection: Flow<Boolean?> = flow {
        emit(getServer()?.connection?.allowInsecureConnection)
    }

    val hasPlainTextUrl: Flow<Boolean> = flow {
        emit(getServer()?.connection?.hasPlainTextUrl ?: false)
    }

    suspend fun allowInsecureConnection(allowInsecureConnection: Boolean) {
        try {
            getServer()?.let { server ->
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
