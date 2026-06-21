package io.homeassistant.companion.android.widgets.entity

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
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
    fun `EntityWidgetConfigureView`() {
        HAThemeForPreview {
            EntityWidgetConfigureView(
                servers = listOf(previewServer1, previewServer2),
                viewState = EntityWidgetConfigureViewState(
                    selectedServerId = previewServer1.id,
                    selectedEntityId = previewEntity1.entityId,
                    appendAttributes = true,
                    selectedAttributeIds = listOf("brightness"),
                    label = "Office light",
                    textSize = "30",
                    stateSeparator = " - ",
                    attributeSeparator = ", ",
                    selectedTapAction = WidgetTapAction.TOGGLE,
                    selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
                    selectedTextColor = EntityWidgetTextColor.WHITE,
                ),
                onServerSelected = {},
                entities = listOf(previewEntity1),
                onEntitySelected = {},
                availableAttributes = listOf("brightness", "friendly_name"),
                onAppendAttributesChanged = {},
                onAttributeAdded = {},
                onAttributeRemoved = {},
                onCustomAttributeChanged = {},
                onCustomAttributesAdded = {},
                onLabelChanged = {},
                onTextSizeChanged = {},
                onStateSeparatorChanged = {},
                onAttributeSeparatorChanged = {},
                isToggleable = true,
                onTapActionSelected = {},
                onBackgroundTypeSelected = {},
                dynamicColorAvailable = true,
                onTextColorSelected = {},
                onErrorShown = {},
                onActionClick = {},
            )
        }
    }
}
