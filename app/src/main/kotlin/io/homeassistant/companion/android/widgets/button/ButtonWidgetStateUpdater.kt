package io.homeassistant.companion.android.widgets.button

import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import timber.log.Timber

@Singleton
class ButtonWidgetStateUpdater @Inject constructor(
    val buttonWidgetDao: ButtonWidgetDao,
) {

    private val isActionRunning = MutableStateFlow<Map<Int, Boolean>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getButtonEntityFlow(widgetId: Int): Flow<ButtonWidgetState?> {
        val runningFlow = isActionRunning
            .map { it[widgetId] ?: false }
            .distinctUntilChanged()

        return combine(runningFlow, buttonWidgetDao.getFlow(widgetId)) { running, entity ->
            running to entity
        }.mapLatest { (running, entity) ->
            Timber.i("Widget $widgetId is running: $running")
            if (running) {
                Loading
            } else if (entity != null) {
                ButtonStateWithData.from(entity)
            } else {
                null
            }
        }
    }

    fun updateIsActionRunning(widgetId: Int, isRunning: Boolean) {
        isActionRunning.value = isActionRunning.value.toMutableMap().apply {
            put(widgetId, isRunning)
        }
    }
}
