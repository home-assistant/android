package io.homeassistant.companion.android.common.data.servers

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepositoryFactory
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.IntegrationRepositoryFactory
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager.Companion.SERVER_ID_ACTIVE
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepositoryFactory
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerDao
import io.homeassistant.companion.android.database.server.TemporaryServer
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.di.qualifiers.NamedSessionStorage
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

private const val PREF_ACTIVE_SERVER = "active_server"

/**
 * A thread-safe map that associates server IDs with lazily-created values.
 *
 * This class provides concurrent access protection using a [Mutex] and supports
 * lazy initialization of values via the [creator] function when using [getOrCreate].
 *
 * @param T The type of values stored in the map.
 * @param creator A suspend function that creates a new value for a given server ID
 *                when the ID is not yet present in the map.
 */
private class ServerMap<T>(private val creator: suspend (Int) -> T) {
    private val internalMap = mutableMapOf<Int, T>()
    private val mutex = Mutex()

    suspend operator fun set(serverId: Int, value: T) {
        mutex.withLock {
            internalMap[serverId] = value
        }
    }

    suspend operator fun get(serverId: Int): T? {
        return mutex.withLock {
            internalMap[serverId]
        }
    }

    suspend fun getOrCreate(serverId: Int): T {
        return mutex.withLock {
            internalMap.getOrPut(serverId) {
                creator(serverId)
            }
        }
    }

    suspend fun remove(serverId: Int) {
        mutex.withLock {
            internalMap.remove(serverId)
        }
    }
}

internal class ServerManagerImpl @Inject constructor(
    private val authenticationRepositoryFactory: AuthenticationRepositoryFactory,
    private val integrationRepositoryFactory: IntegrationRepositoryFactory,
    private val webSocketRepositoryFactory: WebSocketRepositoryFactory,
    private val serverConnectionStateProviderFactory: ServerConnectionStateProviderFactory,
    private val prefsRepository: PrefsRepository,
    private val serverDao: ServerDao,
    private val sensorDao: SensorDao,
    private val settingsDao: SettingsDao,
    @NamedSessionStorage private val localStorage: LocalStorage,
) : ServerManager {

    private val authenticationRepos = ServerMap<AuthenticationRepository>(authenticationRepositoryFactory::create)
    private val integrationRepos = ServerMap<IntegrationRepository>(integrationRepositoryFactory::create)
    private val webSocketRepos = ServerMap(webSocketRepositoryFactory::create)
    private val connectionStateProviders =
        ServerMap<ServerConnectionStateProvider>(serverConnectionStateProviderFactory::create)

    override suspend fun servers(): List<Server> {
        return serverDao.getAll()
    }

    override val serversFlow: Flow<List<Server>>
        get() = serverDao.getAllFlow()

    override suspend fun isRegistered(): Boolean {
        return serverDao.getAll().any {
            it.connection.isRegistered &&
                FailFast.failOnCatchSuspend(
                    message = {
                        """Failed to get authenticationRepository for ${it.id}."""
                    },
                    fallback = false,
                ) { authenticationRepository(it.id).getSessionState() == SessionState.CONNECTED }
        }
    }

    override suspend fun addServer(server: TemporaryServer): Int {
        return serverDao.add(Server.fromTemporaryServer(server)).toInt()
    }

    override suspend fun getServer(id: Int): Server? {
        val serverId = getServerIdSanitize(id)
        return serverId?.let {
            serverDao.get(it)
        }
    }

    override suspend fun getServer(webhookId: String): Server? {
        return serverDao.get(webhookId)
    }

    override suspend fun activateServer(id: Int) {
        if (id != SERVER_ID_ACTIVE) {
            localStorage.putInt(PREF_ACTIVE_SERVER, id)
        } else {
            Timber.w("Activate with SERVER_ID_ACTIVE is not doing anything")
        }
    }

    override suspend fun updateServer(server: Server) {
        serverDao.update(server)
    }

    override suspend fun removeServer(id: Int) {
        authenticationRepository(id).deletePreferences()
        integrationRepository(id).deletePreferences()
        prefsRepository.removeServer(id)
        authenticationRepos.remove(id)
        integrationRepos.remove(id)
        webSocketRepos[id]?.shutdown()
        webSocketRepos.remove(id)
        connectionStateProviders.remove(id)

        if (localStorage.getInt(PREF_ACTIVE_SERVER) == id) localStorage.remove(PREF_ACTIVE_SERVER)
        settingsDao.delete(id)
        sensorDao.removeServer(id)
        serverDao.delete(id)
    }

    override suspend fun authenticationRepository(serverId: Int): AuthenticationRepository {
        val id = validateServerId(serverId)
        return authenticationRepos.getOrCreate(id)
    }

    override suspend fun integrationRepository(serverId: Int): IntegrationRepository {
        val id = validateServerId(serverId)
        return integrationRepos.getOrCreate(id)
    }

    override suspend fun webSocketRepository(serverId: Int): WebSocketRepository {
        val id = validateServerId(serverId)
        return webSocketRepos.getOrCreate(id)
    }

    override suspend fun connectionStateProvider(serverId: Int): ServerConnectionStateProvider {
        val id = validateServerId(serverId)
        return connectionStateProviders.getOrCreate(id)
    }

    private suspend fun getServerIdSanitize(serverId: Int): Int? {
        return if (serverId == SERVER_ID_ACTIVE) {
            localStorage.getInt(PREF_ACTIVE_SERVER)
                ?: serverDao.getLastServerId()
        } else {
            serverId
        }
    }

    private suspend fun validateServerId(serverId: Int): Int {
        val id = checkNotNull(getServerIdSanitize(serverId)) { "Impossible to determine the serverID from $serverId" }
        checkNotNull(serverDao.get(id)) { "No server for ID ($id)" }
        return id
    }
}
