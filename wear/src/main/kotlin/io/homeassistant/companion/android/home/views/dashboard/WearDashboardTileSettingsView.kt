package io.homeassistant.companion.android.home.views.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import io.homeassistant.companion.android.common.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn

/**
 * Read-only preview of a dashboard tile assignment with guidance to edit on the phone.
 */
@Composable
fun WearDashboardTileSettingsView(
    uiState: WearDashboardTileSettingsUiState,
) {
    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(id = R.string.wear_dashboard_tile)
            }
            item {
                Text(stringResource(R.string.wear_dashboard_tile_change_message))
            }
            item {
                ListHeader(R.string.wear_dashboard_preview)
            }
            item {
                Text(
                    uiState.previewText.ifEmpty {
                        uiState.dashboard?.title ?: stringResource(R.string.wear_dashboard_tile_empty)
                    },
                    color = Color.DarkGray,
                )
            }
        }
    }
}

@Composable
fun WearDashboardTileSettingsRoute(
    viewModel: WearDashboardTileSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WearDashboardTileSettingsView(uiState = uiState)
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun WearDashboardTileSettingsPreview() {
    WearDashboardTileSettingsView(
        uiState = WearDashboardTileSettingsUiState(previewText = "72%"),
    )
}
