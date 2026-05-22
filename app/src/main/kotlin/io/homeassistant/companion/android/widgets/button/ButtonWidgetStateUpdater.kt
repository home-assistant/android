package io.homeassistant.companion.android.widgets.button

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import io.homeassistant.companion.android.widgets.todo.EmptyTodoState
import io.homeassistant.companion.android.widgets.todo.TodoState
import io.homeassistant.companion.android.widgets.todo.TodoStateWithData
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import timber.log.Timber

class ButtonWidgetStateUpdater @Inject constructor(
    val buttonWidgetDao: ButtonWidgetDao,
) {

    fun getButtonEntityFlow(widgetId: Int): Flow<ButtonWidgetEntity?> {
        return buttonWidgetDao.getFlow(widgetId)
    }
}
