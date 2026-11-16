package io.homeassistant.companion.android.settings.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.REQUEST_TIMEOUT_OPTIONS
import io.homeassistant.companion.android.common.data.RequestTimeoutRepository
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

internal data class RequestTimeoutUiState(
    val timeout: Duration? = null,
    val isSaving: Boolean = false,
    val hasError: Boolean = false,
)

@HiltViewModel
internal class RequestTimeoutViewModel @Inject constructor(private val repository: RequestTimeoutRepository) :
    ViewModel() {
    private val state = MutableStateFlow(RequestTimeoutUiState())
    val uiState = state.asStateFlow()

    init {
        load()
    }

    /** Loads the saved selection, allowing a failed storage read to be retried. */
    fun load() {
        viewModelScope.launch {
            state.update { it.copy(hasError = false) }
            try {
                repository.load()
                state.update { it.copy(timeout = repository.timeout.value) }
            } catch (e: IOException) {
                Timber.e(e, "Unable to load request timeout")
                state.update { it.copy(hasError = true) }
            }
        }
    }

    /** Saves a supported selection while preventing overlapping writes. */
    fun selectTimeout(timeout: Duration) {
        if (state.value.timeout == null || state.value.isSaving || timeout !in REQUEST_TIMEOUT_OPTIONS) return
        state.update { it.copy(isSaving = true, hasError = false) }
        viewModelScope.launch {
            try {
                repository.setTimeout(timeout)
                state.update { it.copy(timeout = timeout) }
            } catch (e: IOException) {
                Timber.e(e, "Unable to save request timeout")
                state.update { it.copy(hasError = true) }
            } finally {
                state.update { it.copy(isSaving = false) }
            }
        }
    }
}
