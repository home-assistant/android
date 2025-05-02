package io.homeassistant.companion.android.unifiedpush

import android.content.Context
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.NetworkType
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.notifications.MessagingManager
import io.homeassistant.companion.android.onboarding.getMessagingToken
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import timber.log.Timber

class UnifiedPushManager @Inject constructor(
    @ApplicationContext val context: Context,
    private val serverManager: ServerManager,
    private val messagingManager: MessagingManager,
) {
    companion object {
        const val DISTRIBUTOR_DISABLED = "disabled"

        // Synchronize registration for when it is called from UnifiedPushWorker.
        @Synchronized
        fun register(context: Context) =
            UnifiedPush.register(context)

        @Synchronized
        fun unregister(context: Context) =
            UnifiedPush.unregister(context)
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var distributors: List<String> = emptyList()
    private var retried = false

    fun saveDistributor(distributor: String?) {
        Timber.d("saveDistributor(): $distributor")
        if (distributor == null || distributor == DISTRIBUTOR_DISABLED) {
            unregister(context)
            updateEndpoint(null)
            retried = false
        } else {
            UnifiedPush.saveDistributor(context, distributor)
            register(context)
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

    fun updateEndpoint(endpoint: PushEndpoint?) {
        Timber.d("updateEndpoint(): ${endpoint?.url}")
        mainScope.launch {
            val url = endpoint?.url.orEmpty()
            val token = if (endpoint != null) {
                // Use public key as push token to allow for encryption.
                endpoint.pubKeySet?.let { it.auth + ":" + it.pubKey } ?: ""
            } else {
                // Revert to FCM token when disabling UnifiedPush.
                getMessagingToken()
            }
            val registered = messagingManager.isUnifiedPushEnabled()
            messagingManager.setUnifiedPushEnabled(endpoint != null)

            if (!serverManager.isRegistered()) {
                Timber.d("Not trying to update registration since we aren't authenticated.")
                return@launch
            }
            val encrypt = endpoint != null && token.isNotBlank()
            serverManager.defaultServers.forEach {
                launch {
                    try {
                        serverManager.integrationRepository(it.id).updateRegistration(
                            deviceRegistration = DeviceRegistration(
                                pushUrl = url,
                                pushToken = token,
                                pushEncrypt = encrypt
                            ),
                            allowReregistration = false
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Issue updating push url")
                    }
                }
            }
            if (!registered) {
                Toast.makeText(context, commonR.string.unifiedpush_enabled, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun onRegistrationFailed(reason: FailedReason) {
        if (!retried) { // Only retry once to prevent infinite loop.
            retried = true
            when (reason) {
                FailedReason.INTERNAL_ERROR -> {
                    Timber.d("Retrying registration.")
                    UnifiedPushWorker.start(context) // Retry immediately
                }
                FailedReason.NETWORK -> { // Retry once network is connected
                    Timber.d("Retrying registration once network is connected.")
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                    UnifiedPushWorker.start(context, constraints)
                }
                else -> mainScope.launch {
                    messagingManager.setUnifiedPushEnabled(false)
                }
            }
        } else {
            Timber.d("Already retried registering")
        }
    }
}
