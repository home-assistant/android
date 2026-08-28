package io.homeassistant.companion.android.developer

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Light", device = TALL_TABLET)
@Preview(
    name = "Dark",
    device = TALL_TABLET,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class CatalogScreenPreview

private const val TALL_TABLET = "spec:width=1280dp,height=2600dp,dpi=320"
