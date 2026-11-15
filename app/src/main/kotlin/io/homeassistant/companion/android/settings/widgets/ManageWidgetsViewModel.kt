package io.homeassistant.companion.android.settings.widgets

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.CameraWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.StaticWidgetDao
import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.util.isWidgetPinningSupported
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@HiltViewModel
class ManageWidgetsViewModel @Inject constructor(
    buttonWidgetDao: ButtonWidgetDao,
    cameraWidgetDao: CameraWidgetDao,
    staticWidgetDao: StaticWidgetDao,
    todoWidgetDao: TodoWidgetDao,
    mediaPlayerControlsWidgetDao: MediaPlayerControlsWidgetDao,
    templateWidgetDao: TemplateWidgetDao,
    application: Application,
) : AndroidViewModel(application) {
    companion object {
        const val CONFIGURE_REQUEST_LAUNCHER =
            "io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER"
    }

    val buttonWidgetList = buttonWidgetDao.getAllFlow().collectAsState()
    val cameraWidgetList = cameraWidgetDao.getAllFlow().collectAsState()
    val staticWidgetList = staticWidgetDao.getAllFlow().collectAsState()
    val mediaWidgetList = mediaPlayerControlsWidgetDao.getAllFlow().collectAsState()
    val templateWidgetList = templateWidgetDao.getAllFlow().collectAsState()
    val todoWidgetList = todoWidgetDao.getAllFlow().collectAsState()
    val supportsAddingWidgets: Boolean = application.isWidgetPinningSupported()

    /**
     * Convert a Flow into a State object that updates until the view model is cleared.
     */
    private fun <T> Flow<List<T>>.collectAsState(): State<List<T>> {
        val state = mutableStateOf(emptyList<T>())
        viewModelScope.launch {
            collect { state.value = it }
        }
        return state
    }
}
