package io.homeassistant.companion.android.settings.gestures

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.HAGesture
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class GesturesViewModel @Inject constructor(private val prefsRepository: PrefsRepository) : ViewModel() {

    val gestureActions = mutableStateMapOf<HAGesture, GestureAction>()
    private val _availableGestures = MutableStateFlow<List<HAGesture>?>(null)
    val availableGestures: StateFlow<List<HAGesture>?> = _availableGestures.asStateFlow()

    init {
        viewModelScope.launch {
            HAGesture.entries.forEach {
                gestureActions[it] = prefsRepository.getGestureAction(it)
            }
        }
    }

    /** Updates which gestures can be configured without conflicting with system navigation. */
    fun updateGestureAvailability(leftEdgeReserved: Boolean, rightEdgeReserved: Boolean) {
        _availableGestures.value = HAGesture.entries.filter { gesture ->
            when (gesture) {
                HAGesture.SWIPE_RIGHT_ONE_EDGE -> !leftEdgeReserved
                HAGesture.SWIPE_LEFT_ONE_EDGE -> !rightEdgeReserved
                else -> true
            }
        }
    }

    fun setGestureAction(gesture: HAGesture, action: GestureAction) {
        viewModelScope.launch {
            prefsRepository.setGestureAction(gesture, action)
            gestureActions[gesture] = action
        }
    }
}
