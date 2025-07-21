package io.homeassistant.companion.android.onboarding.connection

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ConnectionScreen(modifier: Modifier = Modifier, viewModel: ConnectionViewModel = hiltViewModel()) {
    Column {
        Text(viewModel.url)
        Button(onClick = {
        }) {
        }
    }
}
