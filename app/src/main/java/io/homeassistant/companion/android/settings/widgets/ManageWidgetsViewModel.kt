package io.homeassistant.companion.android.settings.widgets

import android.app.Application
import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.RemoteException
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.homeassistant.companion.android.HomeAssistantApplication
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
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
    private fun buttonWidgetFlow(): Flow<List<ButtonWidgetEntity>>? = buttonWidgetDao.getAllFlow()
    private val cameraWidgetDao = AppDatabase.getInstance(application).cameraWidgetDao()
    private fun cameraWidgetFlow(): Flow<List<CameraWidgetEntity>>? = cameraWidgetDao.getAllFlow()
    private val staticWidgetDao = AppDatabase.getInstance(application).staticWidgetDao()
    private fun staticWidgetFlow(): Flow<List<StaticWidgetEntity>>? = staticWidgetDao.getAllFlow()
    private val mediaPlayerControlsWidgetDao = AppDatabase.getInstance(application).mediaPlayCtrlWidgetDao()
    private fun mediaWidgetFlow(): Flow<List<MediaPlayerControlsWidgetEntity>>? = mediaPlayerControlsWidgetDao.getAllFlow()
    private val templateWidgetDao = AppDatabase.getInstance(application).templateWidgetDao()
    private fun templateWidgetFlow(): Flow<List<TemplateWidgetEntity>>? = templateWidgetDao.getAllFlow()

    var buttonWidgetList = mutableStateListOf<ButtonWidgetEntity>()
        private set
    var cameraWidgetList = mutableStateListOf<CameraWidgetEntity>()
        private set
    var staticWidgetList = mutableStateListOf<StaticWidgetEntity>()
        private set
    var mediaWidgetList = mutableStateListOf<MediaPlayerControlsWidgetEntity>()
        private set
    var templateWidgetList = mutableStateListOf<TemplateWidgetEntity>()
        private set
    var supportsAddingWidgets = mutableStateOf(false)
        private set

    init {
        buttonWidgetList()
        cameraWidgetList()
        staticWidgetList()
        mediaWidgetList()
        templateWidgetList()
        checkSupportsAddingWidgets()
    }

    private fun buttonWidgetList() {
        viewModelScope.launch {
            buttonWidgetFlow()?.collect {
                buttonWidgetList.clear()
                it.forEach { widget ->
                    buttonWidgetList.add(widget)
                }
            }
        }
    }
    private fun cameraWidgetList() {
        viewModelScope.launch {
            cameraWidgetFlow()?.collect {
                cameraWidgetList.clear()
                it.forEach { widget ->
                    cameraWidgetList.add(widget)
                }
            }
        }
    }
    private fun staticWidgetList() {
        viewModelScope.launch {
            staticWidgetFlow()?.collect {
                staticWidgetList.clear()
                it.forEach { widget ->
                    staticWidgetList.add(widget)
                }
            }
        }
    }
    private fun mediaWidgetList() {
        viewModelScope.launch {
            mediaWidgetFlow()?.collect {
                mediaWidgetList.clear()
                it.forEach { widget ->
                    mediaWidgetList.add(widget)
                }
            }
        }
    }
    private fun templateWidgetList() {
        viewModelScope.launch {
            templateWidgetFlow()?.collect {
                templateWidgetList.clear()
                it.forEach { widget ->
                    templateWidgetList.add(widget)
                }
            }
        }
    }

    private fun checkSupportsAddingWidgets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = getApplication<HomeAssistantApplication>().getSystemService<AppWidgetManager>()
            supportsAddingWidgets.value = try {
                appWidgetManager?.isRequestPinAppWidgetSupported ?: false
            } catch (e: RemoteException) {
                Log.e(TAG, "Unable to read isRequestPinAppWidgetSupported", e)
                false
            }
        }
    }
}
