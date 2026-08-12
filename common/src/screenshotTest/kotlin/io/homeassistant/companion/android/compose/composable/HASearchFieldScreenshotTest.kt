package io.homeassistant.companion.android.compose.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HASearchField
import io.homeassistant.companion.android.common.compose.composable.SearchFieldState
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

class HASearchFieldScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HASearchField`() {
        HAThemeForPreview {
            Column {
                // Empty: shows the search label and no clear icon
                HASearchFieldForTest(SearchFieldState())
                // Filled: shows the text and the trailing clear icon
                HASearchFieldForTest(SearchFieldState(initialQuery = "Living room"))
            }
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HASearchField with leading icon`() {
        HAThemeForPreview {
            Column {
                HASearchFieldForTest(SearchFieldState()) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                }
                HASearchFieldForTest(SearchFieldState(initialQuery = "Living room")) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                }
            }
        }
    }

    @Composable
    private fun HASearchFieldForTest(state: SearchFieldState, leadingIcon: @Composable (() -> Unit)? = null) {
        HASearchField(
            // For some reason in test we need to fix the height and to avoid weird cut we fix it to a big value
            modifier = Modifier.height(120.dp),
            state = state,
            leadingIcon = leadingIcon,
        )
    }
}
