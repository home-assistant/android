package io.homeassistant.companion.android.developer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.homeassistant.companion.android.assist.service.AssistVoiceInteractionService
import io.homeassistant.companion.android.barcode.BarcodeScannerActivity
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.developer.catalog.HAComposeCatalogActivity
import io.homeassistant.companion.android.settings.SettingsActivity
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
            HATheme {
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

    HATheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeContent,
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HAFilledButton(
                    text = "Crash the app",
                    onClick = { throw DummyException() },
                    variant = ButtonVariant.DANGER,
                    modifier = Modifier.padding(top = 16.dp),
                )
                HAFilledButton(
                    text = "Demo ExoPlayer",
                    onClick = {
                        context?.run { startActivity(Intent(context, DemoExoPlayerActivity::class.java)) }
                    },
                )
                HAFilledButton(
                    text = "Start Settings",
                    onClick = {
                        context?.run { startActivity(SettingsActivity.newInstance(context)) }
                    },
                )
                HAFilledButton(
                    text = "Start barcode",
                    onClick = {
                        context?.run {
                            startActivity(BarcodeScannerActivity.newInstance(this, 0, "Title", "Subtitle", "Action"))
                        }
                    },
                )
                HAFilledButton(
                    text = "Fail fast",
                    onClick = {
                        FailFast.failWhen(true) {
                            "This should stop the process."
                        }
                    },
                    variant = ButtonVariant.DANGER,
                )
                HAFilledButton(
                    text = "Start HA Compose Catalog",
                    onClick = {
                        context?.run { startActivity(Intent(this, HAComposeCatalogActivity::class.java)) }
                    },
                )
                HAFilledButton(
                    text = "Check VoiceInteractionService",
                    onClick = {
                        context?.let { ctx ->
                            val isActive = AssistVoiceInteractionService.isActiveService(ctx)
                            scope.launch {
                                snackbarHostState.showSnackbar("VoiceInteractionService active: $isActive")
                            }
                        }
                    },
                )
                HAFilledButton(
                    text = "Start Wake Word Detection",
                    onClick = {
                        context?.let { ctx ->
                            val hasPermission = ContextCompat.checkSelfPermission(
                                ctx,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                AssistVoiceInteractionService.startListening(ctx)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Listening for wake word")
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Microphone permission not granted")
                                }
                            }
                        }
                    },
                )
                HAFilledButton(
                    text = "Stop Wake Word Detection",
                    onClick = {
                        context?.let { ctx ->
                            AssistVoiceInteractionService.stopListening(ctx)
                            scope.launch {
                                snackbarHostState.showSnackbar("Stopped listening")
                            }
                        }
                    },
                    variant = ButtonVariant.WARNING,
                )
            }
        }
    }
}

@Preview
@Composable
private fun DevPlayGroundScreenPreview() {
    HAThemeForPreview {
        DevPlayGroundScreen()
    }
}
