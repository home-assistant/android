package io.homeassistant.companion.android.common.data.integration.display

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import dagger.hilt.android.testing.HiltTestApplication
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Bookmark
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class EntityDisplayWithContextTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val item = EntityDisplayWithContext(
        item = EntityDisplayWithoutContext(
            entityId = "light.bed",
            name = "Bed",
            icon = Mdi.Bookmark,
        ),
        areaName = "Bedroom",
        deviceName = "Hub",
    )

    @Test
    fun `Given a layout direction in the composition when reading the subtitle then it follows that direction`() {
        var ltrSubtitle: String? = null
        var rtlSubtitle: String? = null

        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                ltrSubtitle = item.subtitle()
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                rtlSubtitle = item.subtitle()
            }
        }

        assertEquals("Bedroom ▸ Hub", ltrSubtitle)
        assertEquals("Bedroom ◂ Hub", rtlSubtitle)
    }
}
