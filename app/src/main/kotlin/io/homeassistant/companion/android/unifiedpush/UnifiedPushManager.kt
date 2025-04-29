package io.homeassistant.companion.android.unifiedpush

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushMessage
import timber.log.Timber

class UnifiedPushManager @Inject constructor(
    @ApplicationContext val context: Context,
    private val serverManager: ServerManager,
) {
    companion object {
        const val DISTRIBUTOR_DISABLED = "disabled"
    }

    private var distributors: List<String> = emptyList()

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private val networkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            UnifiedPush.register(context)
            val cm = context.getSystemService<ConnectivityManager>()
            cm?.unregisterNetworkCallback(this)
        }
    }

    private var retried = false

    fun saveDistributor(distributor: String?) {
        if (distributor == null || distributor == DISTRIBUTOR_DISABLED) {
            UnifiedPush.unregister(context)
            retried = false
        } else {
            UnifiedPush.saveDistributor(context, distributor)
            UnifiedPush.register(context)
        }
    }

    fun getDistributors(): List<String> {
        if (distributors.isEmpty()) {
            distributors = UnifiedPush.getDistributors(context)
        }
        return distributors
    }

    fun getDistributor(): String? =
        UnifiedPush.getAckDistributor(context)

    fun updateEndpoint(endpoint: String?) {
        mainScope.launch {
            if (!serverManager.isRegistered()) {
                Timber.d("Not trying to update registration since we aren't authenticated.")
                return@launch
            }
            val url = endpoint.orEmpty()
            serverManager.defaultServers.forEach {
                launch {
                    try {
                        serverManager.integrationRepository(it.id).updateRegistration(
                            deviceRegistration = DeviceRegistration(pushUrl = url),
                            allowReregistration = false
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Issue updating push url")
                    }
                }
            }
        }
    }

    fun onRegistrationFailed(reason: FailedReason) {
        if (!retried) {
            retried = true
            when (reason) {
                FailedReason.INTERNAL_ERROR -> UnifiedPush.register(context)
                FailedReason.NETWORK -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val cm = context.getSystemService<ConnectivityManager>()
                        cm?.registerDefaultNetworkCallback(networkCallback)
                    }
                }

                else -> {}
            }
        } else {
            Timber.d("Already retried registering")
        }
    }
}