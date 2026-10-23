package io.homeassistant.companion.android.settings.gestures

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.GesturePointers
import io.homeassistant.companion.android.common.util.HAGesture
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class GesturesViewModel @Inject constructor(private val prefsRepository: PrefsRepository) : ViewModel() {

    val gestureActions = mutableStateMapOf<HAGesture, GestureAction>()
    var availableGestures by mutableStateOf(HAGesture.entries.filter { it.pointers != GesturePointers.ONE })
        private set

    init {
        viewModelScope.launch {
            HAGesture.entries.forEach {
                gestureActions[it] = prefsRepository.getGestureAction(it)
            }
        }
    }

    /** Updates which gestures can be configured without conflicting with system navigation. */
    fun updateGestureAvailability(leftEdgeReserved: Boolean, rightEdgeReserved: Boolean) {
        availableGestures = HAGesture.entries.filter { gesture ->
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
