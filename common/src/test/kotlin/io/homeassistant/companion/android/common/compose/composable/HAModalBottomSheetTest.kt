@file:OptIn(ExperimentalMaterial3Api::class)

package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.compose.theme.HATheme
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HAModalBottomSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Given dark theme when showing sheet then system bars use dark appearance`() {
        val controller = showSheetAndGetInsetsController(darkTheme = true)

        assertFalse(controller.isAppearanceLightStatusBars)
        assertFalse(controller.isAppearanceLightNavigationBars)
    }

    @Test
    fun `Given light theme when showing sheet then system bars use light appearance`() {
        val controller = showSheetAndGetInsetsController(darkTheme = false)

        assertTrue(controller.isAppearanceLightStatusBars)
        assertTrue(controller.isAppearanceLightNavigationBars)
    }

    private fun showSheetAndGetInsetsController(darkTheme: Boolean): WindowInsetsControllerCompat {
        composeRule.setContent {
            HATheme(darkTheme = darkTheme) {
                HAModalBottomSheet(bottomSheetState = rememberHAModalBottomSheetState()) {}
            }
        }
        composeRule.waitForIdle()
        val window = checkNotNull(ShadowDialog.getLatestDialog()?.window) { "Sheet dialog was not shown" }
        return WindowCompat.getInsetsController(window, window.decorView)
    }
}
