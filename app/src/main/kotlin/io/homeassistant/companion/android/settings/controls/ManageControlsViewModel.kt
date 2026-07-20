package io.homeassistant.companion.android.settings.controls

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.ControlsAuthRequiredSetting
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.GetEntitiesForDisplayUseCase
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.controls.HaControlsPanelActivity
import io.homeassistant.companion.android.controls.HaControlsProviderService
import io.homeassistant.companion.android.database.server.Server
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@HiltViewModel
class ManageControlsViewModel @VisibleForTesting constructor(
    private val serverManager: ServerManager,
    private val prefsRepository: PrefsRepository,
    private val getEntitiesForDisplay: GetEntitiesForDisplayUseCase,
    private val application: Application,
    private val backgroundDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    @Inject
    constructor(
        serverManager: ServerManager,
        prefsRepository: PrefsRepository,
        getEntitiesForDisplay: GetEntitiesForDisplayUseCase,
        application: Application,
    ) : this(serverManager, prefsRepository, getEntitiesForDisplay, application, Dispatchers.Default)

    var panelEnabled by mutableStateOf(false)
        private set

    var authRequired by mutableStateOf(ControlsAuthRequiredSetting.NONE)
        private set

    val authRequiredList = mutableStateListOf<String>()

    var entitiesLoaded by mutableStateOf(false)
        private set

    val entitiesList = mutableStateMapOf<Int, List<EntityDisplayItem>>()

    var panelSetting by mutableStateOf<Pair<String?, Int>?>(null)
        private set

    var structureEnabled by mutableStateOf(false)
        private set

    var servers by mutableStateOf<List<Server>>(emptyList())
        private set

    var defaultServerId by mutableIntStateOf(0)

    init {
        viewModelScope.launch {
            servers = serverManager.servers()
            if (SdkVersion.isAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
                panelEnabled =
                    application.packageManager.getComponentEnabledSetting(
                        ComponentName(application, HaControlsPanelActivity::class.java),
                    ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

                val panelServer = prefsRepository.getControlsPanelServer()
                val panelPath = prefsRepository.getControlsPanelPath()
                panelSetting = if (panelServer != null) {
                    Pair(panelPath, panelServer)
                } else {
                    null
                }
            }

            authRequired = prefsRepository.getControlsAuthRequired()
            authRequiredList.addAll(prefsRepository.getControlsAuthEntities())

            structureEnabled = prefsRepository.getControlsEnableStructure()

            defaultServerId = serverManager.getServer()?.id ?: 0

            val supportedDomains = HaControlsProviderService.getSupportedDomains()
            servers.map { server ->
                async {
                    // The flow completes with a terminal state after Loading, failures surface as Error
                    // and leave the server out of the list to not block configuration of other server's entities
                    val displayState = getEntitiesForDisplay(server.id) { it.domain in supportedDomains }.last()
                    if (displayState is EntityDisplayState.Loaded) {
                        entitiesList[server.id] = withContext(backgroundDispatcher) {
                            displayState.entities
                                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                        }
                    }
                }
            }.awaitAll()
            entitiesLoaded = true
        }
    }

    fun setAuthSetting(setting: ControlsAuthRequiredSetting) {
        viewModelScope.launch {
            authRequired = setting
            if (authRequired != ControlsAuthRequiredSetting.SELECTION) authRequiredList.clear()

            prefsRepository.setControlsAuthRequired(setting)
            prefsRepository.setControlsAuthEntities(authRequiredList.toList())
        }
    }

    fun toggleAuthForEntity(entityId: String, serverId: Int) {
        viewModelScope.launch {
            var newAuthRequired = ControlsAuthRequiredSetting.SELECTION
            val settingId = "$serverId.$entityId"

            if (authRequired == ControlsAuthRequiredSetting.ALL) {
                // User wants this accessible, so add everything except selected
                entitiesList.forEach { (server, entities) ->
                    authRequiredList.addAll(
                        entities.filter { server != serverId || it.entityId != entityId }
                            .map { "$server.${it.entityId}" },
                    )
                }
            } else if (authRequiredList.contains(settingId)) {
                authRequiredList.remove(settingId)
            } else {
                authRequiredList.add(settingId)
            }

            // If list contains entities for servers that no longer exist, clean up
            authRequiredList.groupBy { it.split(".")[0].toIntOrNull() }
                .forEach {
                    if (it.key == null || serverManager.getServer(it.key!!) == null) {
                        authRequiredList.removeAll(it.value)
                    }
                }

            // If none or all are selected, clean up
            if (authRequiredList.isEmpty()) {
                newAuthRequired = ControlsAuthRequiredSetting.NONE
            } else if (
                entitiesList.all { (server, entities) ->
                    entities.all { authRequiredList.contains("$server.${it.entityId}") }
                }
            ) {
                newAuthRequired = ControlsAuthRequiredSetting.ALL
            }

            // Set values for update
            authRequired = newAuthRequired
            if (newAuthRequired != ControlsAuthRequiredSetting.SELECTION) authRequiredList.clear()
            prefsRepository.setControlsAuthRequired(newAuthRequired)
            prefsRepository.setControlsAuthEntities(authRequiredList.toList())
        }
    }

    fun enablePanelForControls(enabled: Boolean) {
        if (!SdkVersion.isAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) return

        application.packageManager.setComponentEnabledSetting(
            ComponentName(application, HaControlsPanelActivity::class.java),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT // Default is disabled
            },
            PackageManager.DONT_KILL_APP,
        )
        panelEnabled = enabled
        if (panelSetting?.second == null) {
            viewModelScope.launch {
                serverManager.getServer()?.id?.let { setPanelConfig("", it) }
            }
        }
    }

    fun setPanelConfig(path: String, serverId: Int) = viewModelScope.launch {
        val cleanedPath = path.trim().takeIf { it.isNotBlank() }
        prefsRepository.setControlsPanelServer(serverId)
        prefsRepository.setControlsPanelPath(cleanedPath)
        panelSetting = Pair(cleanedPath, serverId)
    }

    fun setStructureEnable(enabled: Boolean) {
        structureEnabled = enabled

        viewModelScope.launch {
            prefsRepository.setControlsEnableStructure(enabled)
        }
    }
}
