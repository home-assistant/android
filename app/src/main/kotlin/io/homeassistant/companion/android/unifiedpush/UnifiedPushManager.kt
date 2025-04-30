package io.homeassistant.companion.android.unifiedpush

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.os.Build
import androidx.core.content.getSystemService
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.notifications.MessagingManager
import io.homeassistant.companion.android.onboarding.getMessagingToken
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import timber.log.Timber

class UnifiedPushManager @Inject constructor(
    @ApplicationContext val context: Context,
    private val serverManager: ServerManager,
    private val messagingManager: MessagingManager
) {
    companion object {
        const val DISTRIBUTOR_DISABLED = "disabled"
    }

    private var distributors: List<String> = emptyList()

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var retried = false

    // Synchronize registration for when it is called from UnifiedPushWorker.
    @Synchronized
    internal fun register() {
        UnifiedPush.register(context)
    }

    @Synchronized
    private fun unregister() {
        UnifiedPush.unregister(context)
    }

    fun saveDistributor(distributor: String?) {
        if (distributor == null || distributor == DISTRIBUTOR_DISABLED) {
            unregister()
            retried = false
        } else {
            UnifiedPush.saveDistributor(context, distributor)
            register()
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

    fun tryRegisterDefaultDistributor(): Boolean {
        var result = false
        UnifiedPush.tryUseCurrentOrDefaultDistributor(context) { success ->
            if (success) {
                register()
            }
            result = success
        }
        return result
    }

    fun updateEndpoint(endpoint: PushEndpoint?) {
        mainScope.launch {
            val url = endpoint?.url.orEmpty()
            val token = if (endpoint != null) {
                endpoint.pubKeySet?.let { it.auth + ":" + it.pubKey }
            } else {
                // Restore FCM token when UnifiedPush is disabled.
                messagingManager.fcmToken.ifBlank { getMessagingToken() }
            }
            messagingManager.upToken = token.orEmpty()

            if (!serverManager.isRegistered()) {
                Timber.d("Not trying to update registration since we aren't authenticated.")
                return@launch
            }
            serverManager.defaultServers.forEach {
                launch {
                    try {
                        serverManager.integrationRepository(it.id).updateRegistration(
                            deviceRegistration = DeviceRegistration(
                                pushUrl = url,
                                pushToken = token,
                            ),
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
        if (!retried) { // Only retry once to prevent infinite loop.
            retried = true
            when (reason) {
                FailedReason.INTERNAL_ERROR -> UnifiedPushWorker.start(context)
                FailedReason.NETWORK -> {
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                    UnifiedPushWorker.start(context, constraints)
                }

                else -> {}
            }
        } else {
            Timber.d("Already retried registering")
        }
    }
}