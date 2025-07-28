package io.homeassistant.companion.android.onboarding.connection

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun ConnectionScreen(viewModel: ConnectionViewModel, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(viewModel.url)
        Button(onClick = {
        }) {
        }
    }
}
