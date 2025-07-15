package io.homeassistant.companion.android.common.notifications

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.AppDatabase
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * This interface provides an abstraction for push notifications using various protocols,
 * such as FCM and UnifiedPush.
 *
 * The methods `getUrl()` and `getToken()` are used for device registration and `onMessage()` is
 * a callback for when the provider receives a notification message.
 */
interface PushProvider {
    val mainScope: CoroutineScope
        get() = CoroutineScope(Dispatchers.Main + Job())

    /** @return `true` if this provider is available to the app on this device */
    fun isAvailable(context: Context): Boolean

    /** @return `true` if this provider is enabled on any server */
    fun isEnabled(context: Context): Boolean {
        val settingsDao = AppDatabase.getInstance(context).settingsDao()
        return serverManager(context).defaultServers.any {
            settingsDao.get(it.id)?.pushProvider == id()
        }
    }

    /** @return `true` if this provider is enabled for the specified server */
    fun isEnabled(context: Context, serverId: Int): Boolean {
        val settingsDao = AppDatabase.getInstance(context).settingsDao()
        return settingsDao.get(serverId)?.pushProvider == id()
    }

    /** @return Set of server IDs for which this provider is enabled */
    fun getEnabledServers(context: Context): Set<Int> {
        val settingsDao = AppDatabase.getInstance(context).settingsDao()
        val serverManager = serverManager(context)
        return serverManager.defaultServers.filter {
            settingsDao.get(it.id)?.pushProvider == id()
        }.map { it.id }.toSet()
    }

    /**
     * Get a list of available distributors for this provider. A distributor
     * is most likely to be another Android app.
     *
     * @return List of distributor names, or an empty list if the provider doesn't support
     * selecting a distributor
     */
    suspend fun getDistributors(): List<String>

    /**
     * Get the current distributor for this provider. A distributor is
     * most likely to be another Android app.
     *
     * @return Name of the distributor, or `null` if one isn't selected or
     * the provider doesn't support selecting a distributor
     */
    suspend fun getDistributor(): String?

    suspend fun getUrl(): String

    suspend fun getToken(): String

    fun onMessage(context: Context, notificationData: Map<String, String>)

    /**
     * Update the device registration using this push provider
     */
    suspend fun updateRegistration(context: Context) {
        mainScope.launch {
            val serverManager = serverManager(context)
            if (!serverManager.isRegistered()) {
                Timber.d("No server registered skipping update registration.")
                return@launch
            }
            getEnabledServers(context).forEach {
                launch {
                    try {
                        serverManager.integrationRepository(it).updateRegistration(
                            deviceRegistration = DeviceRegistration(
                                pushToken = getToken()
                            ),
                            allowReregistration = false
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Issue updating token")
                    }
                }
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PushProviderEntryPoint {
        fun serverManager(): ServerManager
    }

    fun serverManager(context: Context) =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PushProviderEntryPoint::class.java
        )
            .serverManager()
}

fun PushProvider.id(): String {
    val simpleName = this::class.simpleName ?: this::class.java.name
    return simpleName.lowercase(Locale.US).replace(" ", "_")
}
