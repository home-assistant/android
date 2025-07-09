package io.homeassistant.companion.android.settings.gestures

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.HAGesture
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@HiltViewModel
class GesturesViewModel @Inject constructor(
    private val prefsRepository: PrefsRepository
) : ViewModel() {

    fun getGestureAction(gesture: HAGesture) = runBlocking {
        prefsRepository.getGestureAction(gesture)
    }

    fun setGestureAction(gesture: HAGesture, action: GestureAction) = runBlocking {
        prefsRepository.setGestureAction(gesture, action)
    }
}
