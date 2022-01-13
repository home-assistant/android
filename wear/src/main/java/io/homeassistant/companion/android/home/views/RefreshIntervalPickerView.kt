package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberPickerState
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.theme.wearColorPalette
import io.homeassistant.companion.android.util.IntervalToString
import io.homeassistant.companion.android.common.R as R

@Composable
fun RefreshIntervalPickerView(
    currentInterval: Int,
    onSelectInterval: (Int) -> Unit
) {
    val options = listOf(0, 60, 2 * 60, 5 * 60, 10 * 60, 15 * 60, 30 * 60, 60 * 60, 5 * 60 * 60, 10 * 60 * 60, 24 * 60 * 60)
    val state = rememberPickerState()

    // TODO use currentInterval. PickerState currently doesn't support changing the selectedOption

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ListHeader(R.string.refresh_interval)
        Picker(
            options.size,
            modifier = Modifier
                .weight(1f)
                .padding(all = 8.dp),
            state = state,
            repeatItems = true // Desire to set to false, but current implementation is faulty
        ) {
            Text(
                IntervalToString(LocalContext.current, options[it]),
                fontSize = 24.sp,
                color = if (it != this.selectedOption) wearColorPalette.onBackground else wearColorPalette.primary
            )
        }
        Button(
            onClick = { onSelectInterval(options[state.selectedOption]) },
            colors = ButtonDefaults.primaryButtonColors(),
            modifier = Modifier
        ) {
            Image(
                CommunityMaterial.Icon.cmd_check
            )
        }
    }
}
