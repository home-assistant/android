package io.homeassistant.companion.android.frontend

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.homeassistant.companion.android.common.compose.theme.HATextStyle

/**
 * Dummy screen that represent the HAFrontend. Used for implementing and testing
 * navigation it would be the equivalent of [WebViewActivity].
 */
@Composable
fun FrontendScreen(modifier: Modifier = Modifier) {
    Text(
        modifier = Modifier.testTag("frontend_placeholder"),
        text = "Connected to Home Assistant Frontend",
        style = HATextStyle.Headline,
    )
}
