package io.homeassistant.companion.android.widgets.camera.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.database.widget.CameraWidgetTapAction
import io.homeassistant.companion.android.util.compose.ExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.compose.SingleEntityPicker

data class CameraWidgetConfigureScreenUiState(
    val entities: List<Entity<Any>> = emptyList(),
    val serverNames: List<String> = emptyList(),
    val isServerPickerVisible: Boolean = false,
    val selectedEntityId: String? = null,
    val selectedServerPosition: Int? = null,
    val tapAction: CameraWidgetTapAction = CameraWidgetTapAction.UPDATE_IMAGE,
    val isNewWidget: Boolean = false,
    val onEntityChange: (entityId: String?) -> Unit = {},
    val onServerSelect: (position: Int) -> Unit = {},
    val onTapActionSelect: (CameraWidgetTapAction) -> Unit = {},
    val onApplyChangesClick: () -> Unit = {}
)

@Composable
fun CameraWidgetConfigureScreenUi(state: CameraWidgetConfigureScreenUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Title()
        if (state.isServerPickerVisible) {
            Spacer(modifier = Modifier.height(16.dp))
            ServerPickerRow(state.serverNames, state.selectedServerPosition, state.onServerSelect)
        }
        Spacer(modifier = Modifier.height(16.dp))
        EntityPickerRow(state.entities, state.selectedEntityId, state.onEntityChange)

        Spacer(modifier = Modifier.height(16.dp))
        ActionPickerRow(state.tapAction, state.onTapActionSelect)

        val buttonTitle = if (state.isNewWidget) stringResource(id = R.string.add_widget) else stringResource(id = R.string.update_widget)
        Spacer(modifier = Modifier.height(16.dp))
        ApplyChangesButton(title = buttonTitle.uppercase(), state.onApplyChangesClick)
    }
}

@Composable
private fun Title() {
    Text(
        text = stringResource(id = R.string.select_entity_to_display),
        style = MaterialTheme.typography.caption,
        fontWeight = FontWeight.Light,
        fontSize = 20.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    )
}

@Composable
private fun ServerPickerRow(serverNames: List<String>, currentPosition: Int?, onServerSelect: (Int) -> Unit) {
    LabeledRow(labelRes = R.string.widget_spinner_server) {
        ExposedDropdownMenu(
            modifier = Modifier.padding(start = 64.dp),
            label = null,
            keys = serverNames,
            currentIndex = currentPosition,
            onSelected = {
                onServerSelect.invoke(it)
            }
        )
    }
}

@Composable
private fun EntityPickerRow(entities: List<Entity<*>>, selectedEntityId: String?, onEntityChange: (String?) -> Unit) {
    LabeledRow(labelRes = R.string.label_entity_id) {
        SingleEntityPicker(
            modifier = Modifier.padding(start = 48.dp),
            label = { Text(stringResource(R.string.select_entity_to_display), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            entities = entities,
            currentEntity = selectedEntityId,
            onEntityCleared = {
                onEntityChange.invoke(null)
            },
            onEntitySelected = {
                onEntityChange.invoke(it)
                true
            }
        )
    }
}

@Composable
private fun ActionPickerRow(tapAction: CameraWidgetTapAction, onSelect: (CameraWidgetTapAction) -> Unit) {
    LabeledRow(labelRes = R.string.widget_action_on_tap) {
        val actions = CameraWidgetTapAction.entries.map {
            val textResId = when (it) {
                CameraWidgetTapAction.OPEN_CAMERA -> R.string.widget_camera_open_camera_action
                CameraWidgetTapAction.UPDATE_IMAGE -> R.string.widget_camera_update_image_action
            }
            stringResource(textResId)
        }
        ExposedDropdownMenu(
            modifier = Modifier.padding(start = 8.dp),
            label = null,
            keys = actions,
            currentIndex = CameraWidgetTapAction.entries.indexOf(tapAction),
            onSelected = {
                val selectedTapAction = CameraWidgetTapAction.entries[it]
                onSelect.invoke(selectedTapAction)
            }
        )
    }
}

@Composable
private fun ColumnScope.ApplyChangesButton(title: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.Companion
            .align(Alignment.End)
            .padding(top = 8.dp)
    ) {
        Text(text = title)
    }
}

@Composable
private fun LabeledRow(@StringRes labelRes: Int, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = stringResource(id = labelRes))
        content.invoke()
    }
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    val mockState = CameraWidgetConfigureScreenUiState(
        isServerPickerVisible = true
    )

    HomeAssistantAppTheme {
        CameraWidgetConfigureScreenUi(mockState)
    }
}
