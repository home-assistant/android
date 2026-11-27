package io.homeassistant.companion.android.notifications.alarm

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.BellRing
import io.github.timoptr.mdiicons.generated.ShieldAlert
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews

class FullScreenAlarmScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FullScreenAlarmScreen with actions and missing permission`() {
        HAThemeForPreview {
            FullScreenAlarmScreen(
                uiState = FullScreenAlarmUiState(
                    icon = Mdi.ShieldAlert,
                    title = "Intrusion detected",
                    message = "Motion detected in the living room while the alarm is armed away",
                    canOpen = true,
                    actions = listOf(
                        FullScreenAlarmAction(index = 0, title = "Silence siren"),
                        FullScreenAlarmAction(index = 1, title = "Call security"),
                        FullScreenAlarmAction(index = 2, title = "Turn on lights"),
                    ),
                    isFullScreenIntentAllowed = false,
                ),
                onDismiss = {},
                onOpen = {},
                onActionClick = {},
                onAllowFullScreen = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FullScreenAlarmScreen dismiss only`() {
        HAThemeForPreview {
            FullScreenAlarmScreen(
                uiState = FullScreenAlarmUiState(
                    icon = Mdi.BellRing,
                    title = "Front door opened",
                    message = "",
                    canOpen = false,
                    actions = emptyList(),
                    isFullScreenIntentAllowed = true,
                ),
                onDismiss = {},
                onOpen = {},
                onActionClick = {},
                onAllowFullScreen = {},
            )
        }
    }
}
