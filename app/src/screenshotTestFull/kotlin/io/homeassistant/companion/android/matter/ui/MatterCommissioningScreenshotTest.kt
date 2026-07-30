package io.homeassistant.companion.android.matter.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.matter.MatterCommissioningViewModel.CommissioningFlowStep
import io.homeassistant.companion.android.settings.server.ServerChooserItem
import io.homeassistant.companion.android.util.compose.HAPreviews

class MatterCommissioningScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `MatterCommissioningScreen steps`(
        @PreviewParameter(MatterCommissioningScreenPreviewStates::class) step: CommissioningFlowStep,
    ) {
        HAThemeForPreview {
            MatterCommissioningScreen(
                step = step,
                deviceName = "Manufacturer Matter Light",
                serverChooserItems = listOf(
                    ServerChooserItem(serverId = 1, userName = "Alice Smith", serverName = "Home", isActive = true),
                    ServerChooserItem(serverId = 2, userName = "Bob", serverName = "Friends home"),
                ),
                onSelectServer = { },
                onConfirmCommissioning = { },
                onClose = { },
                onContinue = { },
            )
        }
    }
}
