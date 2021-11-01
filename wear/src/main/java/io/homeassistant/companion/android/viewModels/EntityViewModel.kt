package io.homeassistant.companion.android.viewModels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

class EntityViewModel : ViewModel() {

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    var entitiesResponse: Array<Entity<Any>> by mutableStateOf(arrayOf())

    companion object {
        private const val TAG = "EntityViewModel"
    }

    fun getEntities(context: Context) {
        DaggerViewModuleComponent.builder()
            .appComponent((context as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
        viewModelScope.launch {
            try {
                val entities = integrationUseCase.getEntities()
                entitiesResponse = entities
            } catch (e: Exception) {
                Log.e(TAG, "Unable to get list of entities", e)
            }
        }
    }
}
