package io.homeassistant.companion.android.settings.server

import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.common.data.network.WifiHelper
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class ServerSettingsPresenterImpl @Inject constructor(
    private val serverManager: ServerManager,
    private val wifiHelper: WifiHelper,
) : PreferenceDataStore(),
    ServerSettingsPresenter {

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
                "app_lock_home_bypass" -> serverManager.authenticationRepository(
                    serverId,
                ).setLockHomeBypassEnabled(value)
                else -> throw IllegalArgumentException("No boolean found by this key: $key")
            }
        }
    }

    override fun getString(key: String?, defValue: String?): String? = runBlocking {
        when (key) {
            "server_name" -> serverManager.getServer(serverId)?.nameOverride
            "registration_name" -> serverManager.getServer(serverId)?.deviceName
            "connection_internal" -> (
                serverManager.getServer(serverId)?.connection?.getUrl(isInternal = true, force = true)
                    ?: ""
                ).toString()
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
                                nameOverride = value?.ifBlank { null },
                            ),
                        )
                    }
                    view.updateServerName(serverManager.getServer(serverId)?.friendlyName ?: "")
                }
                "registration_name" -> {
                    serverManager.getServer(serverId)?.let {
                        serverManager.updateServer(
                            it.copy(
                                deviceName = value?.ifBlank { null },
                            ),
                        )
                    }
                }
                "connection_internal" -> {
                    serverManager.getServer(serverId)?.let {
                        serverManager.updateServer(
                            it.copy(
                                connection = it.connection.copy(
                                    internalUrl = value,
                                ),
                            ),
                        )
                    }
                }
                "session_timeout" -> {
                    try {
                        serverManager.integrationRepository(serverId).sessionTimeOut(value.toString().toInt())
                    } catch (e: Exception) {
                        Timber.e(e, "Issue saving session timeout value")
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
                Timber.w(e, "Unable to revoke session for server")
                // Remove server anyway, the user wants to delete and we don't need the server for that
            }
            serverManager.removeServer(serverId)
            view.onRemovedServer(
                success = true,
                hasAnyRemaining = serverManager.defaultServers.any { it.id != serverId },
            )
        } ?: run {
            view.onRemovedServer(success = false, hasAnyRemaining = true)
        }
    }

    override fun onFinish() {
        runBlocking {
            if (serverManager.getServer()?.id != serverId) {
                setAppActive(false)
            }
        }
        mainScope.cancel()
    }

    override fun hasMultipleServers(): Boolean = serverManager.defaultServers.size > 1

    override fun updateServerName() {
        mainScope.launch {
            view.updateServerName(serverManager.getServer(serverId)?.friendlyName ?: "")
        }
    }

    override fun updateUrlStatus() {
        mainScope.launch {
            serverManager.getServer(serverId)?.let {
                view.updateExternalUrl(
                    it.connection.getUrl(false)?.toString() ?: "",
                    it.connection.useCloud && it.connection.canUseCloud(),
                )
            }
        }
        mainScope.launch {
            val connection = serverManager.getServer(serverId)?.connection
            val ssids = connection?.internalSsids.orEmpty()
            view.enableInternalConnection(
                ssids.isNotEmpty() || connection?.internalEthernet == true || connection?.internalVpn == true,
            )
            view.updateHomeNetwork(ssids, connection?.internalEthernet, connection?.internalVpn)
        }
    }

    override fun hasWifi(): Boolean = wifiHelper.hasWifi()

    override fun clearSsids() {
        mainScope.launch {
            serverManager.getServer(serverId)?.let {
                serverManager.updateServer(
                    it.copy(
                        connection = it.connection.copy(
                            internalSsids = emptyList(),
                        ),
                    ),
                )
            }
            updateUrlStatus()
        }
    }

    override fun setAppActive(active: Boolean) = runBlocking {
        try {
            serverManager.integrationRepository(serverId).setAppActive(active)
        } catch (e: IllegalArgumentException) {
            Timber.w("Cannot set app active $active for server $serverId")
            Unit
        }
    }

    override suspend fun serverURL(): String? {
        return serverManager.getServer(serverId)?.connection?.getUrl()?.toString()
    }

    override suspend fun getAllowInsecureConnection(): Boolean? {
        return serverManager.integrationRepository(serverId).getAllowInsecureConnection()
    }
}
