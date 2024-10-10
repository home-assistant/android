package io.homeassistant.companion.android.settings.widgets.views

import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.database.widget.WidgetEntity
import io.homeassistant.companion.android.settings.views.EmptyState
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.compose.MdcAlertDialog
import io.homeassistant.companion.android.widgets.button.ButtonWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.camera.CameraWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.entity.EntityWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.history.HistoryWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.mediaplayer.MediaPlayerControlsWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.template.TemplateWidgetConfigureActivity

enum class WidgetType(val widgetIcon: IIcon) {
    BUTTON(CommunityMaterial.Icon2.cmd_gesture_tap),
    CAMERA(CommunityMaterial.Icon.cmd_camera_image),
    STATE(CommunityMaterial.Icon3.cmd_shape),
    MEDIA(CommunityMaterial.Icon3.cmd_play_box_multiple),
    TEMPLATE(CommunityMaterial.Icon.cmd_code_braces),
    HISTORY(CommunityMaterial.Icon3.cmd_sun_clock);

    fun configureActivity() = when (this) {
        BUTTON -> ButtonWidgetConfigureActivity::class.java
        CAMERA -> CameraWidgetConfigureActivity::class.java
        MEDIA -> MediaPlayerControlsWidgetConfigureActivity::class.java
        STATE -> EntityWidgetConfigureActivity::class.java
        TEMPLATE -> TemplateWidgetConfigureActivity::class.java
        HISTORY -> HistoryWidgetConfigureActivity::class.java
    }
}

@Composable
fun ManageWidgetsView(
    viewModel: ManageWidgetsViewModel
) {
    var expandedAddWidget by remember { mutableStateOf(false) }
    Scaffold(floatingActionButton = {
        if (viewModel.supportsAddingWidgets) {
            ExtendedFloatingActionButton(
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_widget)) },
                onClick = { expandedAddWidget = true }
            )
        }
    }) { contentPadding ->
        if (expandedAddWidget) {
            val availableWidgets = listOf(
                stringResource(R.string.widget_button_image_description) to WidgetType.BUTTON,
                stringResource(R.string.widget_camera_description) to WidgetType.CAMERA,
                stringResource(R.string.widget_history_description) to WidgetType.HISTORY,
                stringResource(R.string.widget_static_image_description) to WidgetType.STATE,
                stringResource(R.string.widget_media_player_description) to WidgetType.MEDIA,
                stringResource(R.string.template_widget) to WidgetType.TEMPLATE
            ).sortedBy { it.first }

            MdcAlertDialog(
                onDismissRequest = { expandedAddWidget = false },
                title = { Text(stringResource(R.string.add_widget)) },
                content = {
                    LazyColumn {
                        items(availableWidgets, key = { (key) -> key }) { (key, widgetType) ->
                            PopupWidgetRow(widgetLabel = key, widgetType = widgetType) {
                                expandedAddWidget = false
                            }
                        }
                    }
                },
                onCancel = { expandedAddWidget = false },
                contentPadding = PaddingValues(all = 0.dp)
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(all = 16.dp),
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxWidth()
        ) {
            if (viewModel.buttonWidgetList.value.isEmpty() && viewModel.staticWidgetList.value.isEmpty() &&
                viewModel.mediaWidgetList.value.isEmpty() && viewModel.templateWidgetList.value.isEmpty() &&
                viewModel.cameraWidgetList.value.isEmpty() && viewModel.historyWidgetList.value.isEmpty()
            ) {
                item {
                    EmptyState(
                        icon = CommunityMaterial.Icon3.cmd_widgets,
                        title = stringResource(R.string.no_widgets),
                        subtitle = stringResource(R.string.no_widgets_summary)
                    )
                }
            }
            widgetItems(
                viewModel.buttonWidgetList.value,
                widgetType = WidgetType.BUTTON,
                title = R.string.button_widgets,
                widgetLabel = { item ->
                    val label = item.label
                    if (!label.isNullOrEmpty()) label else "${item.domain}.${item.service}"
                }
            )
            widgetItems(
                viewModel.cameraWidgetList.value,
                widgetType = WidgetType.CAMERA,
                title = R.string.camera_widgets,
                widgetLabel = { item -> item.entityId }
            )
            widgetItems(
                viewModel.historyWidgetList.value,
                widgetType = WidgetType.HISTORY,
                title = R.string.history_widgets,
                widgetLabel = { item -> item.entityId }
            )
            widgetItems(
                viewModel.staticWidgetList.value,
                widgetType = WidgetType.STATE,
                title = R.string.entity_state_widgets,
                widgetLabel = { item ->
                    val label = item.label
                    if (!label.isNullOrEmpty()) label else "${item.entityId} ${item.stateSeparator} ${item.attributeIds.orEmpty()}"
                }
            )
            widgetItems(
                viewModel.mediaWidgetList.value,
                widgetType = WidgetType.MEDIA,
                title = R.string.media_player_widgets,
                widgetLabel = { item ->
                    val label = item.label
                    if (!label.isNullOrEmpty()) label else item.entityId
                }
            )
            widgetItems(
                viewModel.templateWidgetList.value,
                widgetType = WidgetType.TEMPLATE,
                title = R.string.template_widgets,
                widgetLabel = { item -> item.template }
            )
        }
    }
}

private fun <T : WidgetEntity> LazyListScope.widgetItems(
    widgetList: List<T>,
    @StringRes title: Int,
    widgetLabel: @Composable (T) -> String,
    widgetType: WidgetType
) {
    if (widgetList.isNotEmpty()) {
        item {
            Text(stringResource(id = title))
        }
        items(widgetList, key = { "$widgetType-${it.id}" }) { item ->
            WidgetRow(widgetLabel = widgetLabel(item), widgetId = item.id, widgetType = widgetType)
        }
    }
}

@Composable
private fun PopupWidgetRow(
    widgetLabel: String,
    widgetType: WidgetType,
    onClickCallback: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(context, widgetType.configureActivity()).apply {
                    putExtra(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, true)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
                onClickCallback()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                asset = widgetType.widgetIcon,
                colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
                contentDescription = widgetLabel
            )
            Text(text = widgetLabel, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

@Composable
private fun WidgetRow(
    widgetLabel: String,
    widgetId: Int,
    widgetType: WidgetType
) {
    val context = LocalContext.current
    Row {
        Button(onClick = {
            val intent = Intent(context, widgetType.configureActivity()).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            context.startActivity(intent)
        }) {
            Text(widgetLabel)
        }
    }
}
