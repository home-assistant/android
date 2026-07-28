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
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    @Inject
    lateinit var entitiesForDisplayManager: EntitiesForDisplayManager

    private val serverId = MutableStateFlow(0)
    private val entitiesState = MutableStateFlow<EntityDisplayState<EntityDisplay>>(EntityDisplayState.Loading)
    private var observeJob: Job? = null

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
                        loadEntities(lifecycleScope, it.id)
                    }
                }
            }

            val serverIdFlow = serverId.asStateFlow()
            val entitiesStateFlow = entitiesState.asStateFlow()

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
                                    entitiesStateFlow,
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
                                    entitiesStateFlow,
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

    /**
     * Observes the displayed entities of the server [id] into [entitiesState], cancelling any
     * previous observation so a refresh of the same server restarts it.
     */
    private fun loadEntities(scope: CoroutineScope, id: Int) {
        serverId.value = id
        observeJob?.cancel()
        entitiesState.value = EntityDisplayState.Loading
        observeJob = scope.launch {
            entitiesForDisplayManager.observe(id).collect { entitiesState.value = it }
        }
    }
}
