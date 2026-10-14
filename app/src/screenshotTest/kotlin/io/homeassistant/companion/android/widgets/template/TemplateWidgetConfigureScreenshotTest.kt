package io.homeassistant.companion.android.widgets.template

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.dashboards.Dashboard
import io.homeassistant.companion.android.common.data.dashboards.DashboardTitle
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
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
                onTapActionSelected = {},
                onDashboardSelected = {},
                onRetryLoadDashboards = {},
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
                state = previewTemplateWidgetConfigureState.copy(
                    template = "",
                    preview = TemplatePreview.Empty,
                    serversDropdownItems = listOf(
                        HADropdownItem(key = previewServer1.id, label = previewServer1.friendlyName),
                    ),
                ),
                snackbarHostState = remember { SnackbarHostState() },
                canNavigateBack = false,
                onNavigate = {},
                onServerSelected = {},
                onTemplateChanged = {},
                onTextSizeChanged = {},
                onBackgroundTypeSelected = {},
                onTextColorSelected = {},
                onTapActionSelected = {},
                onDashboardSelected = {},
                onRetryLoadDashboards = {},
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
                onTapActionSelected = {},
                onDashboardSelected = {},
                onRetryLoadDashboards = {},
                onActionClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `TemplateWidgetConfigureContent open a dashboard`() {
        HAThemeForPreview {
            TemplateWidgetConfigureContent(
                state = previewTemplateWidgetConfigureState.copy(
                    selectedTapAction = WidgetTapAction.OPEN_DASHBOARD,
                    selectedDashboardUrlPath = "energy",
                    dashboards = DashboardsState.Loaded(
                        listOf(
                            Dashboard("home", DashboardTitle.Resource(commonR.string.dashboard_overview)),
                            Dashboard("energy", DashboardTitle.Resource(commonR.string.dashboard_energy)),
                            Dashboard("garden", DashboardTitle.Text("Garden")),
                        ),
                    ),
                ),
                snackbarHostState = remember { SnackbarHostState() },
                canNavigateBack = false,
                onNavigate = {},
                onServerSelected = {},
                onTemplateChanged = {},
                onTextSizeChanged = {},
                onBackgroundTypeSelected = {},
                onTextColorSelected = {},
                onTapActionSelected = {},
                onDashboardSelected = {},
                onRetryLoadDashboards = {},
                onActionClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `TemplateWidgetConfigureContent dashboards error`() {
        HAThemeForPreview {
            TemplateWidgetConfigureContent(
                state = previewTemplateWidgetConfigureState.copy(
                    selectedTapAction = WidgetTapAction.OPEN_DASHBOARD,
                    dashboards = DashboardsState.Error,
                ),
                snackbarHostState = remember { SnackbarHostState() },
                canNavigateBack = false,
                onNavigate = {},
                onServerSelected = {},
                onTemplateChanged = {},
                onTextSizeChanged = {},
                onBackgroundTypeSelected = {},
                onTextColorSelected = {},
                onTapActionSelected = {},
                onDashboardSelected = {},
                onRetryLoadDashboards = {},
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
    template = "{{ states('sensor.temperature') }}\n{{ states('sensor.humidity') }}",
    preview = TemplatePreview.Rendered("<font color=\"#4CAF50\">22.5°C</font>\nHumidity: <b>45%</b>"),
    selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
    dynamicColorAvailable = true,
)
