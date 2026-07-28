package io.homeassistant.companion.android.widgets.entity

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.util.compose.HAPreviews
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2

class EntityWidgetConfigureScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `EntityWidgetConfigureContent selected entity`() {
        HAThemeForPreview {
            EntityWidgetConfigureContent(
                state = previewEntityWidgetConfigureState,
                snackbarHostState = remember { SnackbarHostState() },
                canNavigateBack = false,
                onNavigate = {},
                onServerSelected = {},
                onEntitySelected = {},
                onAttributeAdded = {},
                onAttributeRemoved = {},
                onCustomAttributeChanged = {},
                onCustomAttributesAdded = {},
                onLabelChanged = {},
                onTextSizeChanged = {},
                onStateSeparatorChanged = {},
                onAttributeSeparatorChanged = {},
                onTapActionSelected = {},
                onBackgroundTypeSelected = {},
                onTextColorSelected = {},
                onActionClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `EntityWidgetConfigureContent no selected entity`() {
        HAThemeForPreview {
            EntityWidgetConfigureContent(
                state = previewEntityWidgetConfigureState.copy(selectedEntityId = null),
                snackbarHostState = remember { SnackbarHostState() },
                canNavigateBack = false,
                onNavigate = {},
                onServerSelected = {},
                onEntitySelected = {},
                onAttributeAdded = {},
                onAttributeRemoved = {},
                onCustomAttributeChanged = {},
                onCustomAttributesAdded = {},
                onLabelChanged = {},
                onTextSizeChanged = {},
                onStateSeparatorChanged = {},
                onAttributeSeparatorChanged = {},
                onTapActionSelected = {},
                onBackgroundTypeSelected = {},
                onTextColorSelected = {},
                onActionClick = {},
            )
        }
    }
}

private val previewEntityWidgetConfigureState = EntityWidgetConfigureState(
    selectedServerId = previewServer1.id,
    serversDropdownItems = listOf(previewServer1, previewServer2).map {
        HADropdownItem(key = it.id, label = it.friendlyName)
    },
    entityDisplayState = EntityDisplayState.Loaded(listOf(EntityDisplayItem(previewEntity1))),
    selectedEntityId = previewEntity1.entityId,
    availableAttributes = listOf("brightness", "friendly_name"),
    selectedAttributeIds = listOf("brightness"),
    label = "Office light",
    textSize = "30",
    stateSeparator = " - ",
    attributeSeparator = ", ",
    selectedTapAction = WidgetTapAction.TOGGLE,
    selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
    dynamicColorAvailable = true,
)
