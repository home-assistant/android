package io.homeassistant.companion.android.util.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.rememberNavController
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

class HAAppScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `HAApp no start destination shows loading screen`() {
        HAThemeForPreview {
            HAApp(
                navController = rememberNavController(),
                startDestination = null,
                snackbarHostState = SnackbarHostState(),
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Show snackbar with big content`() {
        HAThemeForPreview(Modifier.fillMaxSize()) {
            Snackbar(
                PreviewSnackbarData(
                    stringResource(
                        commonR.string.error_ssl_subresource_host,
                        "a-very-long-subresource-hostname.analytics.eu-central-1.example-company.com",
                    ),
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(HADimens.SPACE4),
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Show snackbar with action`() {
        HAThemeForPreview(Modifier.fillMaxSize()) {
            Snackbar(
                PreviewSnackbarData(
                    message = "A small message",
                    actionLabel = stringResource(commonR.string.learn_more),
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(HADimens.SPACE4),
            )
        }
    }
}

private class PreviewSnackbarData(message: String, actionLabel: String? = null) : SnackbarData {
    override val visuals = object : SnackbarVisuals {
        override val message = message
        override val actionLabel: String? = actionLabel
        override val withDismissAction = false
        override val duration = SnackbarDuration.Short
    }

    override fun performAction() = Unit

    override fun dismiss() = Unit
}
