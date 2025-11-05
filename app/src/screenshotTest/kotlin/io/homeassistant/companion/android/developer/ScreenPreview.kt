package io.homeassistant.companion.android.developer

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Light", heightDp = 2000, widthDp = 1500)
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    heightDp = 2000,
    widthDp = 1500,
)
annotation class ScreenPreview
