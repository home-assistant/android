package io.homeassistant.companion.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.compose.HAApp
import io.homeassistant.companion.android.theme.HATheme

@AndroidEntryPoint
class Launcher : AppCompatActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()

        splashScreen.setKeepOnScreenCondition {
            // We could use this to postpone the loading of the app until the webView is loaded but then we cannot use it
            // for changing server
            viewModel.shouldShowSplashScreen()
        }

        enableEdgeToEdge()

        setContent {
            HATheme {
                HAApp()
            }
        }
    }
}
