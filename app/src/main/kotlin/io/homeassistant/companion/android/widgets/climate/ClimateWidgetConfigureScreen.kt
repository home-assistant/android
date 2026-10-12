package io.homeassistant.companion.android.widgets.climate

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.AirConditioner
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState.Loaded
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import io.homeassistant.companion.android.widgets.WidgetBackgroundTypeDropdown
import io.homeassistant.companion.android.widgets.WidgetTextColor
import io.homeassistant.companion.android.widgets.WidgetTextColorDropdown


@Composable
fun ClimateWidgetConfigureScreen(
    viewModel: ClimateWidgetConfigureViewModel,
    canNavigateBack: Boolean,
    onNavigate: () -> Unit,
    onActionClick: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ClimateWidgetConfigureContent(
        state = state,
        onServerSelected = viewModel::onServerSelected,
        onEntitySelected = viewModel::onChangeEntity,
        onBackgroundTypeSelected = viewModel::onBackgroundTypeSelected,
        onTextColorSelected = viewModel::onTextColorChange,
        canNavigateBack = canNavigateBack,
        onNavigate = onNavigate,
        onActionClick = onActionClick,
    )
}

@Composable
private fun ClimateWidgetConfigureContent(
    state: ClimateWidgetConfigureState,
    onServerSelected: (Int) -> Unit,
    onEntitySelected: (String) -> Unit,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    onTextColorSelected: (String) -> Unit,
    canNavigateBack: Boolean,
    onNavigate: () -> Unit,
    onActionClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            HATopBar(
                title = { androidx.compose.material3.Text(stringResource(commonR.string.widget_climate_label)) },
                onBackClick = onNavigate.takeIf { canNavigateBack },
                onCloseClick = onNavigate.takeIf { !canNavigateBack },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(safeBottomWindowInsets())
                .padding(padding)
                .padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ServerSelector(
                items = state.serversDropdownItems,
                selectedServerId = state.selectedServerId,
                showServerSelector = state.showServerSelector,
                onServerSelected = onServerSelected,
            )

            EntityPickerSection(
                displayEntities = state.entityDisplayState,
                selectedEntityId = state.selectedEntityId,
                onEntitySelected = {
                    it?.let { onEntitySelected(it) }
                }
            )

            AppearanceSection(
                selectedBackgroundType = state.selectedBackgroundType,
                dynamicColorAvailable = state.dynamicColorAvailable,
                textColorHex = null,
                onBackgroundTypeSelected = onBackgroundTypeSelected,
                onTextColorSelected = onTextColorSelected
            )

            HAAccentButton(
                text = stringResource(
                    if (state.isUpdateWidget) commonR.string.update_widget else commonR.string.add_widget,
                ),
                onClick = onActionClick,
                enabled = state.isActionEnabled,
                modifier = Modifier.formControlWidth(),
            )
        }
    }
}

@Composable
private fun ServerSelector(
    items: List<HADropdownItem<Int>>,
    selectedServerId: Int,
    showServerSelector: Boolean,
    onServerSelected: (Int) -> Unit,
) {
    if (!showServerSelector) return

    HADropdownMenu(
        items = items,
        selectedKey = selectedServerId,
        onItemSelected = onServerSelected,
        label = stringResource(commonR.string.server_select),
        placeholder = stringResource(commonR.string.server_select),
        modifier = Modifier.formControlWidth(),
        enabled = items.isNotEmpty(),
    )
}

@Composable
private fun EntityPickerSection(
    displayEntities: EntityDisplayState<EntityDisplayWithContext>,
    selectedEntityId: String?,
    onEntitySelected: (String?) -> Unit,
) {
    EntityPicker(
        displayState = displayEntities,
        selectedEntityId = selectedEntityId,
        onSelectionChanged = onEntitySelected,
        modifier = Modifier.formControlWidth(),
    )
}

@Composable
private fun AppearanceSection(
    selectedBackgroundType: WidgetBackgroundType,
    dynamicColorAvailable: Boolean,
    textColorHex: String?,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    onTextColorSelected: (colorHex: String) -> Unit,
) {
    WidgetBackgroundTypeDropdown(
        selected = selectedBackgroundType,
        dynamicColorAvailable = dynamicColorAvailable,
        onSelected = onBackgroundTypeSelected,
        modifier = Modifier.formControlWidth(),
    )

    if (selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
        // Widgets persist the resolved hex, so the Context needed to convert stays in the UI layer.
        val context = LocalContext.current
        val selected = remember(context, textColorHex) { WidgetTextColor.fromHex(context, textColorHex) }

        WidgetTextColorDropdown(
            selected = selected,
            onSelected = { onTextColorSelected(it.resolve(context)) },
            modifier = Modifier.formControlWidth(),
        )
    }
}
private fun Modifier.formControlWidth(): Modifier = this
    .widthIn(max = MaxButtonWidth)
    .fillMaxWidth()

@Preview()
@Composable
private fun ClimateWidgetConfigureContentPreviewLight() {
    HAThemeForPreview {
        ClimateWidgetConfigureContent(
            state = ClimateWidgetConfigureState(
                selectedServerId = previewServer1.id,
                entityDisplayState = Loaded(previewDisplayEntities),
                selectedEntityId = previewEntity1.entityId,
                textColor = "blue",
                selectedBackgroundType = WidgetBackgroundType.DAYNIGHT,
                dynamicColorAvailable = true,
                isUpdateWidget = false,
                serversDropdownItems = listOf(previewServer1, previewServer2).map {
                    HADropdownItem(key = it.id, label = it.friendlyName)
                },
            ),
            onServerSelected = {},
            onEntitySelected = {},
            onBackgroundTypeSelected = {},
            onTextColorSelected = {},
            onActionClick = {},
            onNavigate = {},
            canNavigateBack = true,
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ClimateWidgetConfigureContentPreviewDark() {
    HAThemeForPreview {
        ClimateWidgetConfigureContent(
            state = ClimateWidgetConfigureState(
                selectedServerId = previewServer1.id,
                entityDisplayState = Loaded(previewDisplayEntities),
                selectedEntityId = previewEntity1.entityId,
                textColor = "blue",
                selectedBackgroundType = WidgetBackgroundType.DAYNIGHT,
                dynamicColorAvailable = true,
                isUpdateWidget = false,
                serversDropdownItems = listOf(previewServer1, previewServer2).map {
                    HADropdownItem(key = it.id, label = it.friendlyName)
                },
            ),
            onServerSelected = {},
            onEntitySelected = {},
            onBackgroundTypeSelected = {},
            onTextColorSelected = {},
            onActionClick = {},
            onNavigate = {},
            canNavigateBack = true,
        )
    }
}

private val previewDisplayEntities = listOf(
    EntityDisplayWithContext(
        item = EntityDisplayWithoutContext(
            entityId = "climate.air1",
            name = "Samsung HVAC",
            icon = Mdi.AirConditioner
        ),
        areaName = "Kitchen",
    ),
    EntityDisplayWithContext(
        item = EntityDisplayWithoutContext(
            entityId = "climate.air2",
            name = "BGH HVAC",
            icon = Mdi.AirConditioner,
        ),
    ),
)
