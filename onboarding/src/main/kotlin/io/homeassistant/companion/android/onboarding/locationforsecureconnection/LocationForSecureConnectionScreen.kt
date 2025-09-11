package io.homeassistant.companion.android.onboarding.locationforsecureconnection

import androidx.compose.runtime.Composable
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.compose.HAPreviews

@Composable
internal fun LocationForSecureConnectionScreen(onHelpClick: () -> Unit) {
}

@HAPreviews
@Composable
private fun LocationForSecureConnectionScreenPreview() {
    HAThemeForPreview {
        LocationForSecureConnectionScreen(
            onHelpClick = {},
        )
    }
}
