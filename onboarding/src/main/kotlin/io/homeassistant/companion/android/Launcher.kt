package io.homeassistant.companion.android

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.compose.HAApp
import io.homeassistant.companion.android.onboarding.connection.navigation.ConnectionRoute
import io.homeassistant.companion.android.theme.HATheme
import timber.log.Timber

@AndroidEntryPoint
class Launcher : AppCompatActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    private val currentDestination = mutableStateOf<NavDestination?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // TODO when a new intent arrives it doesn't trigger onCreate
        // But we still need to forward it to the navController
        // This is interesting if we keep the `singleTop` flag for the Launcher
        Timber.i("Intent received $intent")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()

        splashScreen.setKeepOnScreenCondition {
            viewModel.shouldShowSplashScreen()
        }

        intent.data?.let {
            intent.data = patchUri(it)
        }

        viewModel.onLauncherCreated(intent.data)

        enableEdgeToEdge()

        setContent {
            HATheme {
                val navController = rememberNavController()
                LaunchedEffect(navController) {
                    navController.currentBackStackEntryFlow.collect {
                        currentDestination.value = it.destination
                    }
                }
                HAApp(
                    isAutomotive =
                    packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE),
                    navController,
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Timber.e("dispatchKeyEvent $event and currentDestination is ${currentDestination.value}")
        // Workaround to sideload on Android TV and use a remote for basic navigation in WebView
        // https://github.com/home-assistant/android/pull/1358
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
            event.action == KeyEvent.ACTION_DOWN &&
            currentDestination.value?.hasRoute<ConnectionRoute>() == true
        ) {
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
            return true
        }

        return super.dispatchKeyEvent(event)
    }
}

private fun patchUri(data: Uri): Uri {
    if (data.host == "invite2") { // TODO update host
        // Create a new URI with the encoded fragment as the encoded query
        // Very hacky but we should open an issue that when there is an URL in the fragment the uriPattern
        // doesn't work.
        val newUri = data.buildUpon().encodedQuery(data.encodedFragment).fragment(null).build()
        return newUri
    } else {
        return data
    }
}
