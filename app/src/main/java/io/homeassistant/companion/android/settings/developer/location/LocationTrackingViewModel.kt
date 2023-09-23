package io.homeassistant.companion.android.settings.developer.location

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.database.location.LocationHistoryDao
import io.homeassistant.companion.android.database.location.LocationHistoryItemResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationTrackingViewModel @Inject constructor(
    private val locationHistoryDao: LocationHistoryDao,
    private val prefsRepository: PrefsRepository,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "LocationTrackingViewMod"

        private const val PAGE_SIZE = 25
    }

    var historyEnabled by mutableStateOf(false)
        private set

    private val historyResultFilter = MutableStateFlow<Boolean?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val historyPagerFlow = historyResultFilter.flatMapLatest { filter ->
        Pager(PagingConfig(pageSize = PAGE_SIZE, maxSize = PAGE_SIZE * 6)) {
            Log.d(TAG, "Returning PagingSource for filter sent only: $filter")
            when (filter) {
                true -> locationHistoryDao.getAll(listOf(LocationHistoryItemResult.SENT.name))
                false -> locationHistoryDao.getAll(
                    (LocationHistoryItemResult.values().toMutableList() - LocationHistoryItemResult.SENT).map { it.name }
                )
                else -> locationHistoryDao.getAll()
            }
        }.flow
    }

    init {
        viewModelScope.launch {
            historyEnabled = prefsRepository.isLocationHistoryEnabled()
        }
    }

    fun enableHistory(enabled: Boolean) {
        if (enabled == historyEnabled) return
        historyEnabled = enabled
        viewModelScope.launch {
            prefsRepository.setLocationHistoryEnabled(enabled)
            if (!enabled) locationHistoryDao.deleteAll()
        }
    }

    fun setHistoryFilter(sentOnly: Boolean?) {
        historyResultFilter.value = sentOnly
    }
}
