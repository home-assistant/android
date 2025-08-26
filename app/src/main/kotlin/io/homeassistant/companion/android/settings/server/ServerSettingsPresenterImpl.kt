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
import timber.log.Timber

class ServerSettingsPresenterImpl @Inject constructor(
    private val serverManager: ServerManager,
    private val wifiHelper: WifiHelper,
) : PreferenceDataStore(), ServerSettingsPresenter {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var view: ServerSettingsView
    private var serverId = -1

    // Local caches to support synchronous access
    private var trustServer: Boolean = false
    private var appLock: Boolean = false
    private var appLockHomeBypass: Boolean = false

    private var serverName: String? = null
    private var registrationName: String? = null
    private var connectionInternal: String? = null
    private var sessionTimeout: String? = null

    override fun init(view: ServerSettingsView, serverId: Int) {
        this.view = view
        this.serverId = serverId
        preloadPreferences()
    }

    private fun preloadPreferences() {
        mainScope.launch {
            try {
                val integrationRepo = serverManager.integrationRepository(serverId)
                val authRepo = serverManager.authenticationRepository(serverId)
                val server = serverManager.getServer(serverId)

                trustServer = integrationRepo.isTrusted()
                appLock = authRepo.isLockEnabledRaw()
                appLockHomeBypass = authRepo.isLockHomeBypassEnabled()

                serverName = server?.nameOverride
                registrationName = server?.deviceName
                connectionInternal = server?.connection?.getUrl(isInternal = true, force = true)?.toString()
                sessionTimeout = integrationRepo.getSessionTimeOut().toString()
            } catch (e: Exception) {
                Timber.e(e, "Error preloading preferences")
            }
        }
    }

    override fun getPreferenceDataStore(): PreferenceDataStore = this

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return when (key) {
            "trust_server" -> trustServer
            "app_lock" -> appLock
            "app_lock_home_bypass" -> appLockHomeBypass
            else -> {
                Timber.e("No boolean found by this key: $key")
                defValue
            }
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        mainScope.launch {
            when (key) {
                "trust_server" -> {
                    serverManager.integrationRepository(serverId).setTrusted(value)
                    trustServer = value
                }
                "app_lock" -> {
                    serverManager.authenticationRepository(serverId).setLockEnabled(value)
                    appLock = value
                }
                "app_lock_home_bypass" -> {
                    serverManager.authenticationRepository(serverId).setLockHomeBypassEnabled(value)
                    appLockHomeBypass = value
                }
                else -> throw IllegalArgumentException("No boolean found by this key: $key")
            }
        }
    }

    override fun getString(key: String?, defValue: String?): String? {
        return when (key) {
            "server_name" -> serverName ?: defValue
            "registration_name" -> registrationName ?: defValue
            "connection_internal" -> connectionInternal ?: defValue
            "session_timeout" -> sessionTimeout ?: defValue
            else -> {
                Timber.e("No string found by this key: $key")
                defValue
            }
        }
    }

    override fun putString(key: String?, value: String?) {
        mainScope.launch {
            when (key) {
                "server_name" -> {
                    serverManager.getServer(serverId)?.let {
                        serverManager.updateServer(
                            it.copy(nameOverride = value?.ifBlank { null })
                        )
                        serverName = value
                    }
                    view.updateServerName(serverManager.getServer(serverId)?.friendlyName ?: "")
                }

                "registration_name" -> {
                    serverManager.getServer(serverId)?.let {
                        serverManager.updateServer(
                            it.copy(deviceName = value?.ifBlank { null })
                        )
                        registrationName = value
                    }
                }

                "connection_internal" -> {
                    serverManager.getServer(serverId)?.let {
                        serverManager.updateServer(
                            it.copy(connection = it.connection.copy(internalUrl = value))
                        )
                        connectionInternal = value
                    }
                }

                "session_timeout" -> {
                    try {
                        val timeout = value?.toIntOrNull() ?: return@launch
                        serverManager.integrationRepository(serverId).sessionTimeOut(timeout)
                        sessionTimeout = timeout.toString()
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
        mainScope.launch {
            if (serverManager.getServer()?.id != serverId) {
                setAppActive(false)
            }
            mainScope.cancel()
        }
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
                ssids.isNotEmpty() || connection?.internalEthernet == true || connection?.internalVpn == true
            )
            view.updateHomeNetwork(ssids, connection?.internalEthernet, connection?.internalVpn)
        }
    }

    override fun hasWifi(): Boolean = wifiHelper.hasWifi()

    override fun clearSsids() {
        mainScope.launch {
            serverManager.getServer(serverId)?.let {
                serverManager.updateServer(
                    it.copy(connection = it.connection.copy(internalSsids = emptyList()))
                )
            }
            updateUrlStatus()
        }
    }

    override fun setAppActive(active: Boolean) {
        mainScope.launch {
            try {
                serverManager.integrationRepository(serverId).setAppActive(active)
            } catch (e: IllegalArgumentException) {
                Timber.w("Cannot set app active $active for server $serverId")
            }
        }
    }

    override suspend fun serverURL(): String? {
        return serverManager.getServer(serverId)?.connection?.getUrl()?.toString()
    }
}