package io.homeassistant.companion.android.settings.widgets.views

import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.widgets.button.ButtonWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.camera.CameraWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.entity.EntityWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.media_player_controls.MediaPlayerControlsWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.template.TemplateWidgetConfigureActivity

@Composable
fun ManageWidgetsView(
    viewModel: ManageWidgetsViewModel
) {
    var expandedAddWidget by remember { mutableStateOf(false) }
    Scaffold(floatingActionButton = {
        if (viewModel.supportsAddingWidgets.value) {
            ExtendedFloatingActionButton(
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_widget)) },
                onClick = { expandedAddWidget = true }
            )
        }
    }) {
        if (expandedAddWidget) {
            Dialog(onDismissRequest = { expandedAddWidget = false }) {
                val availableWidgets = mapOf(
                    stringResource(R.string.widget_button_image_description) to Pair("button", CommunityMaterial.Icon2.cmd_gesture_tap),
                    stringResource(R.string.widget_camera_description) to Pair("camera", CommunityMaterial.Icon.cmd_camera),
                    stringResource(R.string.widget_static_image_description) to Pair("state", CommunityMaterial.Icon3.cmd_shape),
                    stringResource(R.string.widget_media_player_description) to Pair("media", CommunityMaterial.Icon3.cmd_play_box_multiple),
                    stringResource(R.string.template_widget) to Pair("template", CommunityMaterial.Icon.cmd_code_braces)
                ).toSortedMap(compareBy { it })
                Box(modifier = Modifier.background(MaterialTheme.colors.surface, RoundedCornerShape(4.dp))) {
                    LazyColumn {
                        item {
                            Text(
                                text = stringResource(R.string.add_widget),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                                style = MaterialTheme.typography.h6
                            )
                        }
                        availableWidgets.forEach {
                            item {
                                PopupWidgetRow(widgetLabel = it.key, widgetIcon = it.value.second, widgetType = it.value.first) {
                                    expandedAddWidget = false
                                }
                            }
                        }
                        item {
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 8.dp)
                            ) {
                                TextButton(onClick = { expandedAddWidget = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        }
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp)
        ) {
            if (viewModel.buttonWidgetList.isNullOrEmpty() && viewModel.staticWidgetList.isNullOrEmpty() &&
                viewModel.mediaWidgetList.isNullOrEmpty() && viewModel.templateWidgetList.isNullOrEmpty()
            ) {
                item {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(top = 64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.no_widgets),
                            style = MaterialTheme.typography.h6,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.7f).padding(top = 8.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.no_widgets_summary),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    }
                }
            }
            if (!viewModel.buttonWidgetList.isNullOrEmpty()) {
                item {
                    Text(stringResource(id = R.string.button_widgets))
                }
                items(viewModel.buttonWidgetList.size) { index ->
                    val item = viewModel.buttonWidgetList[index]
                    val label = if (!item.label.isNullOrEmpty()) item.label else "${item.domain}.${item.service}"
                    WidgetRow(widgetLabel = label.toString(), widgetId = item.id, widgetType = "button")
                }
            }
            if (!viewModel.staticWidgetList.isNullOrEmpty()) {
                item {
                    Text(stringResource(id = R.string.entity_state_widgets))
                }
                items(viewModel.staticWidgetList.size) { index ->
                    val item = viewModel.staticWidgetList[index]
                    val label = if (!item.label.isNullOrEmpty()) item.label else "${item.entityId} ${item.stateSeparator} ${item.attributeIds}"
                    WidgetRow(widgetLabel = label.toString(), widgetId = item.id, widgetType = "state")
                }
            }
            if (!viewModel.mediaWidgetList.isNullOrEmpty()) {
                item {
                    Text(stringResource(id = R.string.media_player_widgets))
                }
                items(viewModel.mediaWidgetList.size) { index ->
                    val item = viewModel.mediaWidgetList[index]
                    val label = if (!item.label.isNullOrEmpty()) item.label else item.entityId
                    WidgetRow(widgetLabel = label.toString(), widgetId = item.id, widgetType = "media")
                }
            }
            if (!viewModel.templateWidgetList.isNullOrEmpty()) {
                item {
                    Text(stringResource(id = R.string.template_widgets))
                }
                items(viewModel.templateWidgetList.size) { index ->
                    val item = viewModel.templateWidgetList[index]
                    WidgetRow(widgetLabel = item.template, widgetId = item.id, widgetType = "template")
                }
            }
        }
    }
}

@Composable
fun PopupWidgetRow(
    widgetLabel: String,
    widgetIcon: IIcon,
    widgetType: String,
    onClickCallback: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.fillMaxWidth().clickable {
            val intent = Intent(
                context,
                when (widgetType) {
                    "button" -> ButtonWidgetConfigureActivity::class.java
                    "camera" -> CameraWidgetConfigureActivity::class.java
                    "media" -> MediaPlayerControlsWidgetConfigureActivity::class.java
                    "state" -> EntityWidgetConfigureActivity::class.java
                    "template" -> TemplateWidgetConfigureActivity::class.java
                    else -> ButtonWidgetConfigureActivity::class.java // We will never reach this
                }
            ).apply {
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
                asset = widgetIcon,
                colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
                contentDescription = widgetLabel
            )
            Text(text = widgetLabel, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

@Composable
fun WidgetRow(
    widgetLabel: String,
    widgetId: Int,
    widgetType: String
) {
    val context = LocalContext.current
    Row {
        Button(onClick = {
            val intent = Intent(
                context,
                when (widgetType) {
                    "button" -> ButtonWidgetConfigureActivity::class.java
                    "media" -> MediaPlayerControlsWidgetConfigureActivity::class.java
                    "state" -> EntityWidgetConfigureActivity::class.java
                    "template" -> TemplateWidgetConfigureActivity::class.java
                    else -> ButtonWidgetConfigureActivity::class.java // We will never reach this
                }
            ).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            context.startActivity(intent)
        }) {
            Text(widgetLabel)
        }
    }
}
