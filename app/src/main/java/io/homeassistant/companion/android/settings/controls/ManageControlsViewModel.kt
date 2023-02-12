package io.homeassistant.companion.android.settings.controls

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.ControlsAuthRequiredSetting
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.controls.HaControlsProviderService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@HiltViewModel
class ManageControlsViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val prefsRepository: PrefsRepository,
    application: Application
) : AndroidViewModel(application) {

    var authRequired by mutableStateOf(ControlsAuthRequiredSetting.NONE)
        private set

    val authRequiredList = mutableStateListOf<String>()

    var entitiesLoaded by mutableStateOf(false)
        private set

    val entitiesList = mutableStateMapOf<Int, List<Entity<*>>>()

    init {
        viewModelScope.launch {
            authRequired = prefsRepository.getControlsAuthRequired()
            authRequiredList.addAll(prefsRepository.getControlsAuthEntities())

            serverManager.defaultServers.map { server ->
                async {
                    val entities = serverManager.integrationRepository(server.id).getEntities()
                        ?.filter { it.domain in HaControlsProviderService.getSupportedDomains() }
                        ?.sortedWith(
                            compareBy(String.CASE_INSENSITIVE_ORDER) {
                                (it.attributes as Map<String, Any>)["friendly_name"].toString()
                            }
                        )
                    if (entities != null) {
                        entitiesList[server.id] = entities
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
                            .map { "$server.${it.entityId}" }
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
}
