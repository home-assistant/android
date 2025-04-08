package io.homeassistant.companion.android.developer

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

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

        setContent {
            HomeAssistantAppTheme {
                DevPlayGroundScreen()
            }
        }
    }
}

private class DummyException : Throwable()

@Composable
private fun DevPlayGroundScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(modifier = Modifier.padding(top = 16.dp), onClick = {
            throw DummyException()
        }) {
            Text("Crash the app")
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
