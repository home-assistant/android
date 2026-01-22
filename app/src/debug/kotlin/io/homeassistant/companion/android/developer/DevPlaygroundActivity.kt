package io.homeassistant.companion.android.developer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.assist.service.AssistVoiceInteractionService
import io.homeassistant.companion.android.barcode.BarcodeScannerActivity
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.developer.catalog.HAComposeCatalogActivity
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.enableEdgeToEdgeCompat
import kotlinx.coroutines.launch

/**
 * This activity is meant to host a playground for development purposes.
 *
 * Like crashing the app on purpose, playing with the application theme.
 *
 * This activity is not meant to be used in production that's why it is only accessible through the debug build type.
 * To avoid any mistakes this activity is only accessible from a shortcut
 */
class DevPlaygroundActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()

        setContent {
            HomeAssistantAppTheme {
                DevPlayGroundScreen(this)
            }
        }
    }
}

private class DummyException : Throwable()

@Composable
private fun DevPlayGroundScreen(context: Context? = null) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(modifier = Modifier.padding(top = 16.dp), onClick = {
                throw DummyException()
            }) {
                Text("Crash the app")
            }
            Button(modifier = Modifier.padding(top = 16.dp), onClick = {
                context?.run { startActivity(Intent(context, DemoExoPlayerActivity::class.java)) }
            }) {
                Text("Demo ExoPlayer")
            }
            Button(modifier = Modifier.padding(top = 16.dp), onClick = {
                context?.run { startActivity(SettingsActivity.newInstance(context)) }
            }) {
                Text("Start Settings")
            }
            Button(modifier = Modifier.padding(top = 16.dp), onClick = {
                context?.run {
                    startActivity(BarcodeScannerActivity.newInstance(this, 0, "Title", "Subtitle", "Action"))
                }
            }) {
                Text("Start barcode")
            }
            Button(modifier = Modifier.padding(top = 16.dp), onClick = {
                FailFast.failWhen(true) {
                    "This should stop the process."
                }
            }) {
                Text("Fail fast")
            }
            Button(modifier = Modifier.padding(top = 16.dp), onClick = {
                context?.run { startActivity(Intent(this, HAComposeCatalogActivity::class.java)) }
            }) {
                Text("Start HA Compose Catalog")
            }
            Button(modifier = Modifier.padding(top = 16.dp), onClick = {
                context?.let { ctx ->
                    val isActive = AssistVoiceInteractionService.isActiveService(ctx)
                    scope.launch {
                        snackbarHostState.showSnackbar("VoiceInteractionService active: $isActive")
                    }
                }
            }) {
                Text("Check VoiceInteractionService")
            }
            Button(modifier = Modifier.padding(top = 16.dp), onClick = {
                context?.let { ctx ->
                    AssistVoiceInteractionService.startListening(ctx)
                    scope.launch {
                        snackbarHostState.showSnackbar("Listening for 'Hey Jarvis'")
                    }
                }
            }) {
                Text("Start Wake Word Detection")
            }
            Button(modifier = Modifier.padding(top = 16.dp), onClick = {
                context?.let { ctx ->
                    AssistVoiceInteractionService.stopListening(ctx)
                    scope.launch {
                        snackbarHostState.showSnackbar("Stopped listening")
                    }
                }
            }) {
                Text("Stop Wake Word Detection")
            }
        }
    }
}

@Preview
@Composable
private fun DevPlayGroundScreenPreview() {
    HomeAssistantAppTheme {
        DevPlayGroundScreen()
    }
}
