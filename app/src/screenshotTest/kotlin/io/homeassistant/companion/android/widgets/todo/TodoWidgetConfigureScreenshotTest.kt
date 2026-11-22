package io.homeassistant.companion.android.widgets.todo

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.tools.screenshot.PreviewTest
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.ClipboardList
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.HAPreviews
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2

class TodoWidgetConfigureScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `TodoWidgetConfigureContent selected list`() {
        HAThemeForPreview {
            TodoWidgetConfigureContent(
                state = previewConfigureState,
                snackbarHostState = remember { SnackbarHostState() },
                canNavigateBack = false,
                onNavigate = {},
                onServerSelected = {},
                onEntitySelected = {},
                onShowCompletedChanged = {},
                onBackgroundTypeSelected = {},
                onTextColorSelected = {},
                onActionClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `TodoWidgetConfigureContent no selected list`() {
        HAThemeForPreview {
            TodoWidgetConfigureContent(
                state = previewConfigureState.copy(
                    serversDropdownItems = previewConfigureState.serversDropdownItems.take(1),
                    selectedEntityId = null,
                ),
                snackbarHostState = remember { SnackbarHostState() },
                canNavigateBack = false,
                onNavigate = {},
                onServerSelected = {},
                onEntitySelected = {},
                onShowCompletedChanged = {},
                onBackgroundTypeSelected = {},
                onTextColorSelected = {},
                onActionClick = {},
            )
        }
    }
}

private val previewConfigureState = TodoWidgetConfigureState(
    selectedServerId = previewServer1.id,
    serversDropdownItems = listOf(previewServer1, previewServer2).map {
        HADropdownItem(key = it.id, label = it.friendlyName)
    },
    entityDisplayState = EntityDisplayState.Loaded(
        listOf(
            EntityDisplayWithContext(
                item = EntityDisplayWithoutContext(
                    entityId = "todo.shopping_list",
                    name = "Shopping List",
                    icon = Mdi.ClipboardList,
                ),
                areaName = "Kitchen",
            ),
        ),
    ),
    selectedEntityId = "todo.shopping_list",
    selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
    dynamicColorAvailable = true,
    isUpdateWidget = true,
)
