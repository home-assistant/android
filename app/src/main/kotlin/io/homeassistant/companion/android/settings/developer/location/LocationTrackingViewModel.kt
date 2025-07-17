package io.homeassistant.companion.android.settings.developer.location

import android.app.Application
import androidx.annotation.IdRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.database.location.LocationHistoryDao
import io.homeassistant.companion.android.database.location.LocationHistoryItemResult
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class LocationTrackingViewModel @Inject constructor(
    private val locationHistoryDao: LocationHistoryDao,
    private val prefsRepository: PrefsRepository,
    application: Application,
) : AndroidViewModel(application) {

    companion object {
        private const val PAGE_SIZE = 25
    }

    enum class HistoryFilter(@IdRes val menuItemId: Int) {
        ALL(R.id.history_all),
        SENT(R.id.history_sent),
        SKIPPED(R.id.history_skipped),
        FAILED(R.id.history_failed),
        ;

        companion object {
            val menuItemIdToFilter = values().associateBy { it.menuItemId }
        }
    }

    var historyEnabled by mutableStateOf(false)
        private set

    private val historyResultFilter = MutableStateFlow(HistoryFilter.ALL)

    @OptIn(ExperimentalCoroutinesApi::class)
    val historyPagerFlow = historyResultFilter.flatMapLatest { filter ->
        Pager(PagingConfig(pageSize = PAGE_SIZE, maxSize = PAGE_SIZE * 6)) {
            Timber.d("Returning PagingSource for history filter: $filter")
            when (filter) {
                HistoryFilter.SENT ->
                    locationHistoryDao.getAll(listOf(LocationHistoryItemResult.SENT.name))
                HistoryFilter.SKIPPED -> locationHistoryDao.getAll(
                    (
                        LocationHistoryItemResult.values().toMutableList() - LocationHistoryItemResult.SENT -
                            LocationHistoryItemResult.FAILED_SEND
                        )
                        .map { it.name },
                )
                HistoryFilter.FAILED ->
                    locationHistoryDao.getAll(listOf(LocationHistoryItemResult.FAILED_SEND.name))
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

    fun setHistoryFilter(@IdRes filterMenuItemId: Int) {
        historyResultFilter.value = HistoryFilter.menuItemIdToFilter.getValue(filterMenuItemId)
    }
}
