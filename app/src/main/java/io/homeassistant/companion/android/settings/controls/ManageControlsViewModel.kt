package io.homeassistant.companion.android.settings.controls

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

    val entitiesList = mutableStateListOf<Entity<*>>()

    init {
        viewModelScope.launch {
            authRequired = prefsRepository.getControlsAuthRequired()
            authRequiredList.addAll(prefsRepository.getControlsAuthEntities())

            val entities = serverManager.integrationRepository().getEntities()
                ?.filter { it.domain in HaControlsProviderService.getSupportedDomains() }
                ?.sortedWith(
                    compareBy(String.CASE_INSENSITIVE_ORDER) {
                        (it.attributes as Map<String, Any>)["friendly_name"].toString()
                    }
                )
            if (entities != null) {
                entitiesList.addAll(entities)
            }
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

    fun toggleAuthForEntity(entityId: String) {
        viewModelScope.launch {
            var newAuthRequired = ControlsAuthRequiredSetting.SELECTION

            if (authRequired == ControlsAuthRequiredSetting.ALL) {
                // User wants this accessible, so add everything except selected
                authRequiredList.addAll(
                    entitiesList.map { it.entityId }.filter { it != entityId }
                )
            } else if (authRequiredList.contains(entityId)) {
                authRequiredList.remove(entityId)
            } else {
                authRequiredList.add(entityId)
            }

            // If none or all are selected, clean up
            if (authRequiredList.isEmpty()) {
                newAuthRequired = ControlsAuthRequiredSetting.NONE
            } else if (entitiesList.all { authRequiredList.contains(it.entityId) }) {
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
