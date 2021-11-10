package io.homeassistant.companion.android.home

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.homeassistant.companion.android.common.data.integration.Entity
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private lateinit var homePresenter: HomePresenter

    // TODO: This is bad, do this instead: https://stackoverflow.com/questions/46283981/android-viewmodel-additional-arguments
    fun init(homePresenter: HomePresenter) {
        this.homePresenter = homePresenter
        loadEntities()
    }

    var entities = mutableStateListOf<Entity<*>>()
        private set
    var favoriteEntityIds = mutableStateListOf<String>()
        private set

    fun loadEntities() {
        viewModelScope.launch {
            entities.addAll(homePresenter.getEntities())
            favoriteEntityIds.addAll(homePresenter.getWearHomeFavorites())
        }
    }

    fun toggleEntity(entityId: String) {
        viewModelScope.launch {
            homePresenter.onEntityClicked(entityId)
            val updatedEntities = homePresenter.getEntities()
            // This should be better....
            for (i in updatedEntities.indices) {
                entities[i] = updatedEntities[i]
            }
        }
    }

    fun addFavorite(entityId: String) {

        viewModelScope.launch {
            favoriteEntityIds.add(entityId)
            homePresenter.setWearHomeFavorites(favoriteEntityIds)
        }
    }

    fun removeFavorite(entity: String) {

        viewModelScope.launch {
            favoriteEntityIds.remove(entity)
            homePresenter.setWearHomeFavorites(favoriteEntityIds)
        }
    }

    fun clearFavorites() {
        viewModelScope.launch {
            favoriteEntityIds.clear()
            homePresenter.setWearHomeFavorites(favoriteEntityIds)
        }
    }

    fun logout() {
        homePresenter.onLogoutClicked()
    }
}
