package io.homeassistant.companion.android.home.views.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.views.ThemeLazyColumn

/**
 * Full-screen Wear dashboard driven by declarative config and cached entity state.
 */
@Composable
fun WearDashboardScreen(
    viewModel: WearDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WearAppTheme {
        when {
            uiState.isLoading -> {
                ThemeLazyColumn {
                    item {
                        Text(
                            stringResource(R.string.wear_dashboard_loading),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            uiState.config == null -> {
                ThemeLazyColumn {
                    item {
                        Text(
                            stringResource(R.string.wear_dashboard_not_found),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            else -> {
                uiState.composeContent?.content?.invoke()
            }
        }
    }
}
