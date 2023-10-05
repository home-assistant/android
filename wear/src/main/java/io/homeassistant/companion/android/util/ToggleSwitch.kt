package io.homeassistant.companion.android.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.wear.compose.material3.Switch
import io.homeassistant.companion.android.theme.getSwitchColors

@Composable
fun getToggleSwitch(isChecked: Boolean, description: String) = Switch(
    checked = isChecked,
    modifier = Modifier.semantics {
        this.contentDescription = description
    },
    colors = getSwitchColors()
)
