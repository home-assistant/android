package io.homeassistant.companion.android.settings.widgets

import android.app.Application
import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.RemoteException
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.homeassistant.companion.android.database.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ManageWidgetsViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(
    application
) {
    companion object {
        private const val TAG = "ManageWidgetsViewModel"

        const val CONFIGURE_REQUEST_LAUNCHER =
            "io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER"
    }

    private val buttonWidgetDao = AppDatabase.getInstance(application).buttonWidgetDao()
    private val cameraWidgetDao = AppDatabase.getInstance(application).cameraWidgetDao()
    private val staticWidgetDao = AppDatabase.getInstance(application).staticWidgetDao()
    private val mediaPlayerControlsWidgetDao = AppDatabase.getInstance(application).mediaPlayCtrlWidgetDao()
    private val templateWidgetDao = AppDatabase.getInstance(application).templateWidgetDao()

    val buttonWidgetList = buttonWidgetDao.getAllFlow().collectAsState()
    val cameraWidgetList = cameraWidgetDao.getAllFlow().collectAsState()
    val staticWidgetList = staticWidgetDao.getAllFlow().collectAsState()
    val mediaWidgetList = mediaPlayerControlsWidgetDao.getAllFlow().collectAsState()
    val templateWidgetList = templateWidgetDao.getAllFlow().collectAsState()
    var supportsAddingWidgets = mutableStateOf(false)
        private set

    init {
        supportsAddingWidgets.value = checkSupportsAddingWidgets()
    }

    private fun checkSupportsAddingWidgets(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = getApplication<Application>().getSystemService<AppWidgetManager>()
            try {
                return appWidgetManager?.isRequestPinAppWidgetSupported ?: false
            } catch (e: RemoteException) {
                Log.e(TAG, "Unable to read isRequestPinAppWidgetSupported", e)
            }
        }
        return false
    }

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
