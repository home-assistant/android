package io.homeassistant.companion.android.developer

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.developer.catalog.HAComposeCatalogScreen

class HAComposeCatalogScreenshotTest {

    // Use static heightDp to see the whole content of the screen
    @Preview(name = "Light", heightDp = 2000)
    @Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL, heightDp = 2000)
    @PreviewTest
    @Composable
    fun `HAComposeCatalog default screen`() {
        HAComposeCatalogScreen()
    }
}
