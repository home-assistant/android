package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.getFilledTonalButtonColors
import io.homeassistant.companion.android.theme.wearColorScheme
import io.homeassistant.companion.android.util.intervalToString
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn

@Composable
fun TemplateTileSettingsView(templateContent: String, refreshInterval: Int, onClickRefreshInterval: () -> Unit) {
    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(id = R.string.template_tile)
            }
            item {
                Button(
                    modifier = Modifier
                        .fillMaxWidth(),
                    icon = {
                        Image(
                            asset = CommunityMaterial.Icon3.cmd_timer_cog,
                            colorFilter = ColorFilter.tint(wearColorScheme.onSurface),
                        )
                    },
                    colors = getFilledTonalButtonColors(),
                    label = { Text(stringResource(R.string.refresh_interval)) },
                    secondaryLabel = { Text(intervalToString(LocalContext.current, refreshInterval)) },
                    onClick = onClickRefreshInterval,
                )
            }
            item {
                ListHeader(R.string.template_tile_content)
            }
            item {
                Text(stringResource(R.string.template_tile_change_message))
            }
            item {
                Text(
                    templateContent,
                    color = Color.DarkGray,
                )
            }
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewTemplateTileSettingView() {
    CompositionLocalProvider {
        TemplateTileSettingsView(templateContent = "test", refreshInterval = 1) {}
    }
}
