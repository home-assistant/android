package io.homeassistant.companion.android.settings.server

import android.util.Log
import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.wifi.WifiHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class ServerSettingsPresenterImpl @Inject constructor(
    private val serverManager: ServerManager,
    private val wifiHelper: WifiHelper
) : ServerSettingsPresenter, PreferenceDataStore() {

    companion object {
        private const val TAG = "ServerSettingsPresImpl"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var view: ServerSettingsView
    private var serverId = -1

    override fun init(view: ServerSettingsView, serverId: Int) {
        this.view = view
        this.serverId = serverId
    }

    override fun getPreferenceDataStore(): PreferenceDataStore = this

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = runBlocking {
        when (key) {
            "trust_server" -> serverManager.integrationRepository(serverId).isTrusted()
            "app_lock" -> serverManager.authenticationRepository(serverId).isLockEnabledRaw()
            "app_lock_home_bypass" -> serverManager.authenticationRepository(serverId).isLockHomeBypassEnabled()
            else -> throw IllegalArgumentException("No boolean found by this key: $key")
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        mainScope.launch {
            when (key) {
                "trust_server" -> serverManager.integrationRepository(serverId).setTrusted(value)
                "app_lock" -> serverManager.authenticationRepository(serverId).setLockEnabled(value)
                "app_lock_home_bypass" -> serverManager.authenticationRepository(serverId).setLockHomeBypassEnabled(value)
                else -> throw IllegalArgumentException("No boolean found by this key: $key")
            }
        }
    }

    override fun getString(key: String?, defValue: String?): String? = runBlocking {
        when (key) {
            "server_name" -> serverManager.getServer(serverId)?.nameOverride
            "registration_name" -> serverManager.getServer(serverId)?.deviceName
            "connection_internal" -> (serverManager.getServer(serverId)?.connection?.getUrl(isInternal = true, force = true) ?: "").toString()
            "session_timeout" -> serverManager.integrationRepository(serverId).getSessionTimeOut().toString()
            else -> throw IllegalArgumentException("No string found by this key: $key")
        }
    }

    override fun putString(key: String?, value: String?) {
        mainScope.launch {
            when (key) {
                "server_name" -> {
                    serverManager.getServer(serverId)?.let {
                        serverManager.updateServer(
                            it.copy(
                                nameOverride = value?.ifBlank { null }
                            )
                        )
                    }
                    view.updateServerName(serverManager.getServer(serverId)?.friendlyName ?: "")
                }
                "registration_name" -> {
                    serverManager.getServer(serverId)?.let {
                        serverManager.updateServer(
                            it.copy(
                                deviceName = value?.ifBlank { null }
                            )
                        )
                    }
                }
                "connection_internal" -> {
                    serverManager.getServer(serverId)?.let {
                        serverManager.updateServer(
                            it.copy(
                                connection = it.connection.copy(
                                    internalUrl = value
                                )
                            )
                        )
                    }
                }
                "session_timeout" -> {
                    try {
                        serverManager.integrationRepository(serverId).sessionTimeOut(value.toString().toInt())
                    } catch (e: Exception) {
                        Log.e(TAG, "Issue saving session timeout value", e)
                    }
                }
                else -> throw IllegalArgumentException("No string found by this key: $key")
            }
        }
    }

    override suspend fun deleteServer() {
        serverManager.getServer(serverId)?.let {
            try {
                serverManager.authenticationRepository(serverId).revokeSession()
            } catch (e: Exception) {
                Log.w(TAG, "Unable to revoke session for server", e)
                // Remove server anyway, the user wants to delete and we don't need the server for that
            }
            serverManager.removeServer(serverId)
            view.onRemovedServer(
                success = true,
                hasAnyRemaining = serverManager.defaultServers.any { it.id != serverId }
            )
        } ?: run {
            view.onRemovedServer(success = false, hasAnyRemaining = true)
        }
    }

    override fun onFinish() {
        if (serverManager.getServer()?.id != serverId) {
            setAppActive(false)
        }
        mainScope.cancel()
    }

    override fun hasMultipleServers(): Boolean = serverManager.defaultServers.size > 1

    override fun updateServerName() =
        view.updateServerName(serverManager.getServer(serverId)?.friendlyName ?: "")

    override fun updateUrlStatus() {
        mainScope.launch {
            serverManager.getServer(serverId)?.let {
                view.updateExternalUrl(
                    it.connection.getUrl(false)?.toString() ?: "",
                    it.connection.useCloud && it.connection.canUseCloud()
                )
            }
        }
        mainScope.launch {
            val ssids = serverManager.getServer(serverId)?.connection?.internalSsids.orEmpty()
            if (ssids.isEmpty()) {
                serverManager.getServer(serverId)?.let {
                    serverManager.updateServer(
                        it.copy(
                            connection = it.connection.copy(
                                internalUrl = null
                            )
                        )
                    )
                }
            }

            view.enableInternalConnection(ssids.isNotEmpty())
            view.updateSsids(ssids)
        }
    }

    override fun hasWifi(): Boolean = wifiHelper.hasWifi()

    override fun isSsidUsed(): Boolean = runBlocking {
        serverManager.getServer(serverId)?.connection?.internalSsids?.isNotEmpty() == true
    }

    override fun clearSsids() {
        mainScope.launch {
            serverManager.getServer(serverId)?.let {
                serverManager.updateServer(
                    it.copy(
                        connection = it.connection.copy(
                            internalSsids = emptyList()
                        )
                    )
                )
            }
            updateUrlStatus()
        }
    }

    override fun setAppActive(active: Boolean) = runBlocking {
        try {
            serverManager.integrationRepository(serverId).setAppActive(active)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Cannot set app active $active for server $serverId")
            Unit
        }
    }
}
