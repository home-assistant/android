package io.homeassistant.companion.android.developer

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices.TABLET
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Light", device = TABLET)
@Preview(
    name = "Dark",
    device = TABLET,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class CatalogScreenPreview
