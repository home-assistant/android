package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.theme.HABorderWidth
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

@Composable
fun HAHorizontalDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = LocalHAColorScheme.current.colorBorderNeutralQuiet,
        thickness = HABorderWidth.S,
    )
}
