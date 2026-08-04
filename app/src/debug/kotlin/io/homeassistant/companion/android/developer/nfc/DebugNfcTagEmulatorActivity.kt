package io.homeassistant.companion.android.developer.nfc

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.enableEdgeToEdgeCompat

/**
 * Turns this device into a writable NFC tag through [DebugNfcTagEmulatorService], so another
 * device can be tested against it without physical tag hardware.
 *
 * The tag is only exposed while this activity is visible: the service component ships disabled
 * and is enabled/disabled with the activity lifecycle.
 */
class DebugNfcTagEmulatorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()

        // HCE only answers while the screen is on, keep it on while emulating
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            HATheme {
                DebugNfcTagEmulatorScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setTagEmulationEnabled(true)
    }

    override fun onStop() {
        super.onStop()
        setTagEmulationEnabled(false)
    }

    private fun setTagEmulationEnabled(enabled: Boolean) {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, DebugNfcTagEmulatorService::class.java),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }
}

@Composable
private fun DebugNfcTagEmulatorScreen() {
    var tagId by remember { mutableStateOf("") }
    val tagContent by DebugNfcTagEmulatorState.content.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    Scaffold(
        contentWindowInsets = WindowInsets.safeContent,
        topBar = {
            HATopBar(
                onCloseClick = {
                    activity?.finish()
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = HADimens.SPACE4)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            Text(
                "Emulates a writable NFC Type 4 tag while this screen is visible. Keep this " +
                    "device unlocked and tap another phone against it to read or write the tag.",
                style = HATextStyle.Body,
            )
            HATextField(
                value = tagId,
                onValueChange = { tagId = it },
                label = { Text("NFC tag id to emulate") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            tagId = ""
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear tag ID",
                        )
                    }
                },
            )
            HAFilledButton(
                text = "Set emulated NFC tag",
                enabled = tagId.isNotBlank(),
                onClick = { DebugNfcTagEmulatorState.setTagId(tagId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = HADimens.SPACE4),
            )
            RawDataText(
                "Emulated tag content",
                tagContent.summary,
                modifier = Modifier.padding(bottom = HADimens.SPACE4),
            )
            RawDataText("Raw NDEF file", tagContent.rawHex)
        }
    }
}

@Composable
private fun RawDataText(title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        Text(
            title,
            style = HATextStyle.HeadlineMedium.copy(fontSize = HAFontSize.L),
        )
        SelectionContainer {
            Text(
                text,
                fontFamily = FontFamily.Monospace,
                style = HATextStyle.BodyMedium,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Preview
@Composable
private fun DebugNfcTagEmulatorScreenPreview() {
    HAThemeForPreview {
        DebugNfcTagEmulatorScreen()
    }
}
