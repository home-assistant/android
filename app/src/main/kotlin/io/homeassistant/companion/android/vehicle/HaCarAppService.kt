package io.homeassistant.companion.android.vehicle

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import java.util.Collections
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.O)
@AndroidEntryPoint
class HaCarAppService : CarAppService() {

    companion object {
        var carInfo: CarInfo? = null
            private set
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var prefsRepository: PrefsRepository

    private val serverId = MutableStateFlow(0)
    private val allEntities = MutableStateFlow<Map<String, Entity>>(emptyMap())
    private var allEntitiesJob: Job? = null

    override fun createHostValidator(): HostValidator {
        return if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(R.array.hosts_allowlist)
                .build()
        }
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return object : Session() {
            init {
                lifecycleScope.launch {
                    serverManager.getServer()?.let {
                        loadEntities(this, it.id)
                    }
                }
            }

            val serverIdFlow = serverId.asStateFlow()
            val entityFlow = allEntities.shareIn(
                lifecycleScope,
                SharingStarted.WhileSubscribed(10_000),
                1,
            )

            override fun onCreateScreen(intent: Intent): Screen {
                carInfo = carContext.getCarService(CarHardwareManager::class.java).carInfo

                if (intent.getBooleanExtra("TRANSITION_LAUNCH", false)) {
                    carContext
                        .getCarService(ScreenManager::class.java).run {
                            push(
                                MainVehicleScreen(
                                    carContext,
                                    serverManager,
                                    serverIdFlow,
                                    entityFlow,
                                    prefsRepository,
                                    { loadEntities(lifecycleScope, it) },
                                    { loadEntities(lifecycleScope, serverId.value) },
                                ),
                            )

                            push(
                                LoginScreen(
                                    carContext,
                                    serverManager,
                                ),
                            )
                        }
                    return SwitchToDrivingOptimizedScreen(carContext)
                } else {
                    carContext
                        .getCarService(ScreenManager::class.java).run {
                            push(
                                MainVehicleScreen(
                                    carContext,
                                    serverManager,
                                    serverIdFlow,
                                    entityFlow,
                                    prefsRepository,
                                    { loadEntities(lifecycleScope, it) },
                                    { loadEntities(lifecycleScope, serverId.value) },
                                ),
                            )
                        }
                    return LoginScreen(
                        carContext,
                        serverManager,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        carInfo = null
    }

    private fun loadEntities(scope: CoroutineScope, id: Int) {
        allEntitiesJob?.cancel()
        allEntitiesJob = scope.launch {
            serverId.value = id
            val entities: MutableMap<String, Entity>? =
                if (serverManager.getServer(id) != null) {
                    serverManager.integrationRepository(id).getEntities()
                        ?.associate { it.entityId to it }
                        ?.toMutableMap()
                } else {
                    null
                }
            if (entities != null) {
                allEntities.emit(entities.toImmutableMap())
                serverManager.integrationRepository(id).getEntityUpdates()?.collect { entity ->
                    entities[entity.entityId] = entity
                    allEntities.emit(entities.toImmutableMap())
                }
            } else {
                Timber.w("No entities found?")
                allEntities.emit(emptyMap())
            }
        }
    }

    /** Returns an immutable copy of this. */
    private fun <K, V> Map<K, V>.toImmutableMap(): Map<K, V> {
        return if (isEmpty()) {
            emptyMap()
        } else {
            Collections.unmodifiableMap(LinkedHashMap(this))
        }
    }
}
