package io.homeassistant.companion.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.compose.HAApp
import io.homeassistant.companion.android.compose.rememberHAAppState
import io.homeassistant.companion.android.onboarding.theme.HATheme

@AndroidEntryPoint
class Launcher : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()
        // splashScreen.setKeepOnScreenCondition { true }

        enableEdgeToEdge()

        setContent {
            val state = rememberHAAppState()

            HATheme {
                HAApp(state)
            }
        }
    }
}
