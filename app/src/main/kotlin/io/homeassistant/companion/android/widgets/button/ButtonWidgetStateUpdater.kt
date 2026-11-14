package io.homeassistant.companion.android.widgets.button

import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class ButtonWidgetStateUpdater @Inject constructor(private val buttonWidgetDao: ButtonWidgetDao) {
    private val actionRunningState = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    private val actionErrorState = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    private val actionSuccessState = MutableStateFlow<Map<Int, Boolean>>(emptyMap())

    fun updateIsActionRunning(appWidgetId: Int, isRunning: Boolean) {
        actionRunningState.value = actionRunningState.value.toMutableMap().apply {
            put(appWidgetId, isRunning)
        }
    }

    fun updateIsActionError(appWidgetId: Int, isError: Boolean) {
        actionErrorState.value = actionErrorState.value.toMutableMap().apply {
            put(appWidgetId, isError)
        }
    }

    fun updateIsActionSuccess(appWidgetId: Int, isSuccess: Boolean) {
        actionSuccessState.value = actionSuccessState.value.toMutableMap().apply {
            put(appWidgetId, isSuccess)
        }
    }

    fun getButtonEntityFlow(appWidgetId: Int): Flow<ButtonWidgetState> {
        return combine(
            buttonWidgetDao.getFlow(appWidgetId).map { buttonEntity ->
                buttonEntity?.let { ButtonStateWithData.from(it) } ?: Error
            },
            actionRunningState.map { it[appWidgetId] ?: false },
            actionErrorState.map { it[appWidgetId] ?: false },
            actionSuccessState.map { it[appWidgetId] ?: false },
        ) { entityState, isRunning, isError, isSuccess ->
            when {
                isRunning -> Loading
                isError -> Error
                isSuccess -> Success
                else -> entityState
            }
        }
    }
}
