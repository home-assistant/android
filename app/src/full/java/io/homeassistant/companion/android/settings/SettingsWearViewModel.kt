package io.homeassistant.companion.android.settings

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.launch

class SettingsWearViewModel : ViewModel() {

    private lateinit var integrationUseCase: IntegrationRepository

    var entities = mutableStateMapOf<String, Entity<*>>()
        private set
    var favoriteEntityIds = mutableStateListOf<String>()

    fun init(integrationUseCase: IntegrationRepository) {
        this.integrationUseCase = integrationUseCase
        loadEntities()
    }
    fun loadEntities() {
        viewModelScope.launch {
            integrationUseCase.getEntities().forEach {
                entities[it.entityId] = it
            }
        }
    }
}
