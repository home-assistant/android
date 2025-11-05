package io.homeassistant.companion.android.developer

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.developer.catalog.catalogButtonsAndIndicatorsSection
import io.homeassistant.companion.android.developer.catalog.catalogTextAndBannersSection
import io.homeassistant.companion.android.developer.catalog.catalogUserInputSection

class HAComposeCatalogScreenshotTest {

    @ScreenPreview
    @PreviewTest
    @Composable
    fun HAButtonsAndIndicatorsScreen() {
        HAThemeForPreview {
            LazyColumn {
                catalogButtonsAndIndicatorsSection(ButtonVariant.PRIMARY)
            }
        }
    }

    @ScreenPreview
    @PreviewTest
    @Composable
    fun HAUserInputScreen() {
        HAThemeForPreview {
            LazyColumn {
                catalogUserInputSection()
            }
        }
    }

    @ScreenPreview
    @PreviewTest
    @Composable
    fun HATextAndBannersScreen() {
        HAThemeForPreview {
            LazyColumn {
                catalogTextAndBannersSection()
            }
        }
    }
}
