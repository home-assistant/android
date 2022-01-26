package io.homeassistant.companion.android.settings.widgets.views

import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.widgets.button.ButtonWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.entity.EntityWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.media_player_controls.MediaPlayerControlsWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.template.TemplateWidgetConfigureActivity

@Composable
fun ManageWidgetsView(
    viewModel: ManageWidgetsViewModel
) {
    LazyColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp)
    ) {
        if (viewModel.buttonWidgetList.isNullOrEmpty() && viewModel.staticWidgetList.isNullOrEmpty() &&
            viewModel.mediaWidgetList.isNullOrEmpty() && viewModel.templateWidgetList.isNullOrEmpty()
        ) {
            item { Text(stringResource(id = R.string.no_widgets)) }
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
