package io.homeassistant.companion.android.common.data.servers

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepositoryFactory
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.IntegrationRepositoryFactory
import io.homeassistant.companion.android.common.data.network.NetworkHelper
import io.homeassistant.companion.android.common.data.network.WifiHelper
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager.Companion.SERVER_ID_ACTIVE
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepositoryFactory
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerDao
import io.homeassistant.companion.android.database.server.ServerType
import io.homeassistant.companion.android.database.settings.SettingsDao
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ServerManagerImpl @Inject constructor(
    private val authenticationRepositoryFactory: AuthenticationRepositoryFactory,
    private val integrationRepositoryFactory: IntegrationRepositoryFactory,
    private val webSocketRepositoryFactory: WebSocketRepositoryFactory,
    private val prefsRepository: PrefsRepository,
    private val serverDao: ServerDao,
    private val sensorDao: SensorDao,
    private val settingsDao: SettingsDao,
    private val wifiHelper: WifiHelper,
    private val networkHelper: NetworkHelper,
    @Named("session") private val localStorage: LocalStorage,
) : ServerManager {

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())
    private val initializationMutex = Mutex()

    private val mutableServers = mutableMapOf<Int, Server>()
    private val mutableDefaultServersFlow = MutableStateFlow<List<Server>>(emptyList())

    private val authenticationRepos = mutableMapOf<Int, AuthenticationRepository>()
    private val integrationRepos = mutableMapOf<Int, IntegrationRepository>()
    private val webSocketRepos = mutableMapOf<Int, WebSocketRepository>()

    // Cache for active server ID to avoid repeated localStorage calls
    private var cachedActiveServerId: Int? = null
    private var isInitialized = false

    companion object {
        private const val PREF_ACTIVE_SERVER = "active_server"
    }

    override val defaultServers: List<Server>
        get() = mutableServers.values.filter { it.type == ServerType.DEFAULT }.toList()

    override val defaultServersFlow: StateFlow<List<Server>>
        get() = mutableDefaultServersFlow.asStateFlow()

    init {
        // Non-blocking initialization
        ioScope.launch {
            initializationMutex.withLock {
                if (!isInitialized) {
                    // Load servers from database
                    serverDao.getAll().forEach {
                        mutableServers[it.id] = it.apply {
                            connection.wifiHelper = wifiHelper
                            connection.networkHelper = networkHelper
                        }
                    }

                    // Load active server ID from localStorage
                    cachedActiveServerId = localStorage.getInt(PREF_ACTIVE_SERVER)

                    isInitialized = true
                    mutableDefaultServersFlow.emit(defaultServers)
                }
            }

            // Listen for updates and update flow
            serverDao.getAllFlow().collect { servers ->
                mutableServers
                    .filter {
                        it.value.type == ServerType.DEFAULT &&
                            it.key !in servers.map { server -> server.id }
                    }
                    .forEach {
                        removeServerFromManager(it.key)
                    }
                servers.forEach {
                    mutableServers[it.id] = it.apply {
                        connection.wifiHelper = wifiHelper
                        connection.networkHelper = networkHelper
                    }
                }
                mutableDefaultServersFlow.emit(defaultServers)
            }
        }
    }

    override suspend fun isRegistered(): Boolean {
        // Ensure initialization is complete
        initializationMutex.withLock {
            if (!isInitialized) return false
        }

        return mutableServers.values.any {
            it.type == ServerType.DEFAULT &&
                it.connection.isRegistered() &&
                FailFast.failOnCatchSuspend(
                    message = {
                        """Failed to get authenticationRepository for ${it.id}. Current repository ids: ${authenticationRepos.keys}."""
                    },
                    fallback = false,
                ) { authenticationRepository(it.id).getSessionState() == SessionState.CONNECTED }
        }
    }

    override suspend fun addServer(server: Server): Int {
        val newServer = server.copy(
            id = when (server.type) {
                ServerType.TEMPORARY -> min(-2, (mutableServers.keys.minOrNull() ?: 0) - 1)
                else -> 0 // Use autogenerated ID
            },
        )
        return if (server.type == ServerType.DEFAULT) {
            serverDao.add(newServer).toInt()
        } else {
            mutableServers[newServer.id] = newServer.apply {
                connection.wifiHelper = wifiHelper
                connection.networkHelper = networkHelper
            }
            newServer.id
        }
    }

    private suspend fun activeServerId(): Int? {
        // Return cached value if available, otherwise load from storage
        return cachedActiveServerId?.let { cached ->
            if (mutableServers[cached] != null) cached else null
        } ?: run {
            val pref = localStorage.getInt(PREF_ACTIVE_SERVER)
            cachedActiveServerId = pref
            if (pref != null && mutableServers[pref] != null) {
                pref
            } else {
                mutableServers.keys.maxOfOrNull { it }
            }
        }
    }

    override suspend fun getServer(id: Int): Server? {
        val serverId = if (id == SERVER_ID_ACTIVE) activeServerId() else id
        return serverId?.let {
            mutableServers[serverId]
                ?: serverDao.get(serverId)?.apply {
                    connection.wifiHelper = wifiHelper
                    connection.networkHelper = networkHelper
                }
        }
    }

    override suspend fun getServer(webhookId: String): Server? =
        mutableServers.values.firstOrNull { it.connection.webhookId == webhookId }
            ?: serverDao.get(webhookId)?.apply {
                connection.wifiHelper = wifiHelper
                connection.networkHelper = networkHelper
            }

    override fun activateServer(id: Int) {
        if (id != SERVER_ID_ACTIVE && mutableServers[id] != null && mutableServers[id]?.type == ServerType.DEFAULT) {
            ioScope.launch {
                localStorage.putInt(PREF_ACTIVE_SERVER, id)
                cachedActiveServerId = id // Update cache
            }
        }
    }

    override fun updateServer(server: Server) {
        mutableServers[server.id] = server.apply {
            connection.wifiHelper = wifiHelper
            connection.networkHelper = networkHelper
        }
        if (server.type == ServerType.DEFAULT) {
            ioScope.launch { serverDao.update(server) }
        }
    }

    override suspend fun convertTemporaryServer(id: Int): Int? {
        return mutableServers[id]?.let { server ->
            if (server.type != ServerType.TEMPORARY) return null

            val newServer = server.copy(id = 0) // Use autogenerated ID
            removeServer(id)
            serverDao.add(newServer).toInt()
        }
    }

    override suspend fun removeServer(id: Int) {
        authenticationRepository(id).deletePreferences()
        integrationRepository(id).deletePreferences()
        prefsRepository.removeServer(id)
        removeServerFromManager(id)
        if (localStorage.getInt(PREF_ACTIVE_SERVER) == id) {
            localStorage.remove(PREF_ACTIVE_SERVER)
            cachedActiveServerId = null // Clear cache
        }
        settingsDao.delete(id)
        sensorDao.removeServer(id)
        serverDao.delete(id)
    }

    private suspend fun removeServerFromManager(id: Int) {
        if (mutableServers[id]?.type == ServerType.TEMPORARY) {
            authenticationRepository(id).deletePreferences()
            integrationRepository(id).deletePreferences()
            prefsRepository.removeServer(id)
        } // else handled in removeServer
        authenticationRepos.remove(id)
        integrationRepos.remove(id)
        webSocketRepos[id]?.shutdown()
        webSocketRepos.remove(id)
        mutableServers.remove(id)
    }

    override fun authenticationRepository(serverId: Int): AuthenticationRepository {
        val id = if (serverId == SERVER_ID_ACTIVE) {
            // For synchronous access, use cached value or best guess
            cachedActiveServerId ?: mutableServers.keys.maxOfOrNull { it }
        } else serverId

        return authenticationRepos[id] ?: run {
            if (id == null || mutableServers[id] == null) throw IllegalArgumentException("No server for ID")
            authenticationRepos[id] = authenticationRepositoryFactory.create(id)
            authenticationRepos[id]!!
        }
    }

    override fun integrationRepository(serverId: Int): IntegrationRepository {
        val id = if (serverId == SERVER_ID_ACTIVE) {
            // For synchronous access, use cached value or best guess
            cachedActiveServerId ?: mutableServers.keys.maxOfOrNull { it }
        } else serverId

        return integrationRepos[id] ?: run {
            if (id == null || mutableServers[id] == null) throw IllegalArgumentException("No server for ID")
            integrationRepos[id] = integrationRepositoryFactory.create(id)
            integrationRepos[id]!!
        }
    }

    override fun webSocketRepository(serverId: Int): WebSocketRepository {
        val id = if (serverId == SERVER_ID_ACTIVE) {
            // For synchronous access, use cached value or best guess
            cachedActiveServerId ?: mutableServers.keys.maxOfOrNull { it }
        } else serverId

        return webSocketRepos[id] ?: run {
            if (id == null || mutableServers[id] == null) throw IllegalArgumentException("No server for ID")
            webSocketRepos[id] = webSocketRepositoryFactory.create(id)
            webSocketRepos[id]!!
        }
    }
}