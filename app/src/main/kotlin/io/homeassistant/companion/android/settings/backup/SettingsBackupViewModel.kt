package io.homeassistant.companion.android.settings.backup

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.backup.BackupSections
import io.homeassistant.companion.android.common.data.backup.InvalidSettingsBackupException
import io.homeassistant.companion.android.common.data.backup.select
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
internal class SettingsBackupViewModel @Inject constructor(
    private val handler: SettingsBackupHandler,
    private val documents: BackupDocumentRepository,
    private val savedState: SavedStateHandle,
    private val runtime: BackupRuntimeManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SettingsBackupUiState>(SettingsBackupUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val eventChannel = Channel<BackupEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = SettingsBackupUiState.Loading
        viewModelScope.launch {
            try {
                val sections = savedSections(savedState)
                _uiState.value =
                    SettingsBackupUiState.Content(handler.destinations(), sections, sectionSelections(sections))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to load settings backup destinations")
                _uiState.value = SettingsBackupUiState.Error
            }
        }
    }

    fun select(section: BackupSection, selected: Boolean) {
        val state = _uiState.value as? SettingsBackupUiState.Content ?: return
        if (state.busy) return
        val sections = when (section) {
            BackupSection.Favorites -> state.sections.copy(favorites = selected)
            BackupSection.Sensors -> state.sections.copy(sensors = selected)
            BackupSection.SensorOptions -> state.sections.copy(sensorOptions = selected)
            BackupSection.Connection -> state.sections.copy(connection = selected)
            BackupSection.Frequency -> state.sections.copy(frequency = selected)
        }
        saveSections(savedState, sections)
        _uiState.value = state.copy(
            sections = sections,
            sectionRows = sectionSelections(sections, state.restore?.backup),
            restore = state.restore?.copy(plan = null),
        )
    }

    fun export(document: String) = perform {
        documents.write(document, handler.export(it.sections))
        eventChannel.send(BackupEvent.Message(R.string.backup_export_success))
    }

    fun importBackup(document: String) = perform { state ->
        val backup = documents.read(document)
        _uiState.value = state.copy(
            servers = handler.destinations(),
            restore = RestoreDraft(backup),
            lastRestore = null,
            sectionRows = sectionSelections(state.sections, backup),
            busy = true,
        )
        eventChannel.send(BackupEvent.ShowRestore)
    }

    fun mapServer(reference: String, destination: Int?) {
        val state = _uiState.value as? SettingsBackupUiState.Content ?: return
        val restore = state.restore?.takeUnless { state.busy } ?: return
        val mapping = if (destination ==
            null
        ) {
            restore.mapping - reference
        } else {
            restore.mapping + (reference to destination)
        }
        _uiState.value = state.copy(restore = restore.copy(mapping = mapping, plan = null))
    }

    fun review() = perform { state ->
        val restore = state.restore?.takeIf { state.canReview } ?: return@perform
        val plan = handler.prepare(restore.backup.select(state.sections), restore.mapping)
        _uiState.value = state.copy(restore = restore.copy(plan = plan), busy = true)
    }

    fun restore() = perform { state ->
        val restore = state.restore ?: return@perform
        val plan = restore.plan ?: return@perform
        if (plan.changeCount == 0) return@perform
        handler.restore(restore.backup.select(state.sections), restore.mapping, plan)
        // Consume the plan before starting services: a refresh error must never invite a second apply.
        _uiState.value =
            state.copy(restore = null, lastRestore = plan, sectionRows = sectionSelections(state.sections), busy = true)
        eventChannel.send(BackupEvent.Restored)
        try {
            runtime.refresh()
            eventChannel.send(BackupEvent.Message(R.string.backup_restore_success))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to refresh services after restoring settings")
            eventChannel.send(BackupEvent.Message(R.string.backup_restore_restart))
        }
    }

    fun dismissRestore() {
        val state = _uiState.value as? SettingsBackupUiState.Content ?: return
        if (!state.busy) _uiState.value = state.copy(restore = null, sectionRows = sectionSelections(state.sections))
    }

    private fun perform(block: suspend (SettingsBackupUiState.Content) -> Unit) {
        viewModelScope.launch {
            // A picker result can arrive while the ViewModel is loading after process recreation.
            val state = _uiState.first { it !is SettingsBackupUiState.Loading } as? SettingsBackupUiState.Content
            if (state == null || state.busy) return@launch
            _uiState.value = state.copy(busy = true)
            try {
                block(state)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: InvalidSettingsBackupException) {
                reportFailure(exception, R.string.backup_invalid_file)
            } catch (exception: Exception) {
                // Parser and document-provider exceptions can contain file contents or private URIs.
                reportFailure(exception, R.string.backup_operation_failed)
            } finally {
                (_uiState.value as? SettingsBackupUiState.Content)?.let { _uiState.value = it.copy(busy = false) }
            }
        }
    }

    private suspend fun reportFailure(exception: Exception, @StringRes message: Int) {
        Timber.e("Failed to complete settings backup operation (%s)", exception.javaClass.simpleName)
        (_uiState.value as? SettingsBackupUiState.Content)?.let { state ->
            _uiState.value = state.copy(restore = state.restore?.copy(plan = null))
        }
        eventChannel.send(BackupEvent.Message(message))
    }
}

private fun savedSections(savedState: SavedStateHandle) = BackupSections(
    favorites = savedState[FAVORITES] ?: true,
    sensors = savedState[SENSORS] ?: true,
    sensorOptions = savedState[SENSOR_OPTIONS] ?: true,
    connection = savedState[CONNECTION] ?: true,
    frequency = savedState[FREQUENCY] ?: true,
)

private fun saveSections(savedState: SavedStateHandle, sections: BackupSections) {
    savedState[FAVORITES] = sections.favorites
    savedState[SENSORS] = sections.sensors
    savedState[SENSOR_OPTIONS] = sections.sensorOptions
    savedState[CONNECTION] = sections.connection
    savedState[FREQUENCY] = sections.frequency
}

private const val FAVORITES = "favorites"
private const val SENSORS = "sensors"
private const val SENSOR_OPTIONS = "sensorOptions"
private const val CONNECTION = "connection"
private const val FREQUENCY = "frequency"
