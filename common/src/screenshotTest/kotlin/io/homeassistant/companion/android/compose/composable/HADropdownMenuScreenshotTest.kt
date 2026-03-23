package io.homeassistant.companion.android.compose.composable

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenuInternal
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

@Preview(name = "phoneLTR", device = "spec:width=411.4dp,height=923.4dp", group = "phone")
@Preview(name = "phoneRTL", device = "spec:width=411.4dp,height=923.4dp", group = "phone", locale = "ar")
@Preview(
    name = "tablet",
    device = "spec:width=1280dp,height=800dp,dpi=320,orientation=portrait",
    group = "tablet",
    uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
)
private annotation class DropdownMenuPreviews

class HADropdownMenuScreenshotTest {

    @PreviewTest
    @DropdownMenuPreviews
    @Composable
    fun `HADropdownMenu collapsed`() {
        HAThemeForPreview {
            Column(
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
                modifier = Modifier.padding(HADimens.SPACE4),
            ) {
                HADropdownMenu(
                    items = testItems,
                    selectedKey = null,
                    onItemSelected = {},
                    label = "Server",
                )
                HADropdownMenu(
                    items = testItems,
                    selectedKey = TestServer.OFFICE,
                    onItemSelected = {},
                    label = "Server",
                )
                HADropdownMenu(
                    items = testItems,
                    selectedKey = TestServer.HOME,
                    onItemSelected = {},
                    label = "Server",
                    enabled = false,
                )
                HADropdownMenu(
                    items = testItems,
                    selectedKey = null,
                    onItemSelected = {},
                )
            }
        }
    }

    // DropdownMenu renders in a Popup window, which is not captured by screenshot tests.
    // Kept as a placeholder in case the framework adds popup capture support.
    @PreviewTest
    @DropdownMenuPreviews
    @Composable
    fun `HADropdownMenu expanded`() {
        HAThemeForPreview {
            HADropdownMenuInternal(
                items = testItems,
                selectedKey = null,
                onItemSelected = {},
                initiallyExpanded = true,
            )
        }
    }
}

private enum class TestServer {
    HOME,
    OFFICE,
    VACATION_HOUSE,
}

private val testItems = listOf(
    HADropdownItem(key = TestServer.HOME, label = "Home"),
    HADropdownItem(key = TestServer.OFFICE, label = "Office"),
    HADropdownItem(key = TestServer.VACATION_HOUSE, label = "Vacation House"),
)
