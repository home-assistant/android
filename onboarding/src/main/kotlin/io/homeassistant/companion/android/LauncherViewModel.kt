package io.homeassistant.companion.android

import androidx.lifecycle.ViewModel
import androidx.room.concurrent.AtomicBoolean

class LauncherViewModel : ViewModel() {
    private val shouldShowSplashScreen = AtomicBoolean(false)

    fun shouldShowSplashScreen(): Boolean = shouldShowSplashScreen.get()
}
