package io.homeassistant.companion.android.common.notifications

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.settings.PushProviderSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

interface PushProvider {
    val setting: PushProviderSetting

    /** @return `true` if this provider is available to the app on this device */
    fun isAvailable(context: Context): Boolean

    /** @return `true` if this provider is enabled on any server */
    fun isEnabled(context: Context): Boolean {
        val settingsDao = AppDatabase.getInstance(context).settingsDao()
        return serverManager(context).defaultServers.any {
            settingsDao.get(it.id)?.pushProvider == setting
        }
    }

    /** @return `true` if this provider is enabled for the specified server */
    fun isEnabled(context: Context, serverId: Int): Boolean {
        val settingsDao = AppDatabase.getInstance(context).settingsDao()
        return settingsDao.get(serverId)?.pushProvider == setting
    }

    /** @return Set of server IDs for which this provider is enabled */
    fun getEnabledServers(context: Context): Set<Int> {
        val settingsDao = AppDatabase.getInstance(context).settingsDao()
        val serverManager = serverManager(context)
        return serverManager.defaultServers.filter {
            settingsDao.get(it.id)?.pushProvider == setting
        }.map { it.id }.toSet()
    }

    suspend fun getUrl(): String = BuildConfig.PUSH_URL

    suspend fun getToken(): String

    fun onMessage(context: Context, notificationData: Map<String, String>)

    /**
     * Update the device registration using this push provider
     */
    suspend fun updateRegistration(context: Context, coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            val serverManager = serverManager(context)
            if (serverManager.isRegistered()) {
                Timber.d("Not trying to update registration since we aren't authenticated.")
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
