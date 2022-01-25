package io.homeassistant.companion.android.settings.widgets

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
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
    private val buttonWidgetDao = AppDatabase.getInstance(application).buttonWidgetDao()
    private fun buttonWidgetFlow(): Flow<List<ButtonWidgetEntity>>? = buttonWidgetDao.getAllFlow()
    private val staticWidgetDao = AppDatabase.getInstance(application).staticWidgetDao()
    private fun staticWidgetFlow(): Flow<List<StaticWidgetEntity>>? = staticWidgetDao.getAllFlow()
    private val mediaPlayerControlsWidgetDao = AppDatabase.getInstance(application).mediaPlayCtrlWidgetDao()
    private fun mediaWidgetFlow(): Flow<List<MediaPlayerControlsWidgetEntity>>? = mediaPlayerControlsWidgetDao.getAllFlow()
    private val templateWidgetDao = AppDatabase.getInstance(application).templateWidgetDao()
    private fun templateWidgetFlow(): Flow<List<TemplateWidgetEntity>>? = templateWidgetDao.getAllFlow()

    var buttonWidgetList = mutableStateListOf<ButtonWidgetEntity>()
        private set
    var staticWidgetList = mutableStateListOf<StaticWidgetEntity>()
        private set
    var mediaWidgetList = mutableStateListOf<MediaPlayerControlsWidgetEntity>()
        private set
    var templateWidgetList = mutableStateListOf<TemplateWidgetEntity>()
        private set

    init {
        buttonWidgetList()
        staticWidgetList()
        mediaWidgetList()
        templateWidgetList()
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
}
