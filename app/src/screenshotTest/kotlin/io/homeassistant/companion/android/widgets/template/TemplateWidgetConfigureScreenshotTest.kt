package io.homeassistant.companion.android.widgets.template

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.HAPreviews
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2

class TemplateWidgetConfigureScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `TemplateWidgetConfigureContent rendered template`() {
        HAThemeForPreview {
            TemplateWidgetConfigureContent(
                state = previewTemplateWidgetConfigureState,
                snackbarHostState = remember { SnackbarHostState() },
                canNavigateBack = false,
                onNavigate = {},
                onServerSelected = {},
                onTemplateChanged = {},
                onTextSizeChanged = {},
                onBackgroundTypeSelected = {},
                onTextColorSelected = {},
                onActionClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `TemplateWidgetConfigureContent empty template`() {
        HAThemeForPreview {
            TemplateWidgetConfigureContent(
                state = previewTemplateWidgetConfigureState.copy(template = "", preview = TemplatePreview.Empty),
                snackbarHostState = remember { SnackbarHostState() },
                canNavigateBack = false,
                onNavigate = {},
                onServerSelected = {},
                onTemplateChanged = {},
                onTextSizeChanged = {},
                onBackgroundTypeSelected = {},
                onTextColorSelected = {},
                onActionClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `TemplateWidgetConfigureContent template error`() {
        HAThemeForPreview {
            TemplateWidgetConfigureContent(
                state = previewTemplateWidgetConfigureState.copy(
                    preview = TemplatePreview.Error(commonR.string.template_render_error),
                ),
                snackbarHostState = remember { SnackbarHostState() },
                canNavigateBack = false,
                onNavigate = {},
                onServerSelected = {},
                onTemplateChanged = {},
                onTextSizeChanged = {},
                onBackgroundTypeSelected = {},
                onTextColorSelected = {},
                onActionClick = {},
            )
        }
    }
}

private val previewTemplateWidgetConfigureState = TemplateWidgetConfigureState(
    selectedServerId = previewServer1.id,
    serversDropdownItems = listOf(previewServer1, previewServer2).map {
        HADropdownItem(key = it.id, label = it.friendlyName)
    },
    template = "{{ states('sensor.example') }}",
    preview = TemplatePreview.Rendered("42"),
    selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
    dynamicColorAvailable = true,
)
