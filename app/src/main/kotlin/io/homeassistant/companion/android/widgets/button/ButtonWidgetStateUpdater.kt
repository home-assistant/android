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
    private val isActionError = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    private val isActionSuccess = MutableStateFlow<Map<Int, Boolean>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getButtonEntityFlow(widgetId: Int): Flow<ButtonWidgetState?> {
        val runningFlow = isActionRunning
            .map { it[widgetId] ?: false }
            .distinctUntilChanged()
        val errorFlow = isActionError
            .map { it[widgetId] ?: false }
            .distinctUntilChanged()
        val successFlow = isActionSuccess
            .map { it[widgetId] ?: false }
            .distinctUntilChanged()

        return combine(runningFlow, errorFlow, successFlow, buttonWidgetDao.getFlow(widgetId)) { running, error, success, entity ->
            Quartet(running, error, success, entity)
        }.mapLatest { (running, error, success, entity) ->
            Timber.i("Widget $widgetId is running: $running, error: $error, success: $success")
            if (running) {
                Loading
            } else if (error) {
                Error
            } else if (success) {
                Success
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

    fun updateIsActionError(widgetId: Int, isError: Boolean) {
        isActionError.value = isActionError.value.toMutableMap().apply {
            put(widgetId, isError)
        }
    }

    fun updateIsActionSuccess(widgetId: Int, isSuccess: Boolean) {
        isActionSuccess.value = isActionSuccess.value.toMutableMap().apply {
            put(widgetId, isSuccess)
        }
    }
}
data class Quartet<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
