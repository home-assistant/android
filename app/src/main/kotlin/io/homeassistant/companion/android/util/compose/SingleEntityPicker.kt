package io.homeassistant.companion.android.util.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SingleEntityPicker(
    entities: List<Entity>,
    currentEntity: String?,
    onEntityCleared: () -> Unit,
    onEntitySelected: (String) -> Boolean,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit) = { Text(stringResource(commonR.string.select_entity_to_display)) },
) {
    val focusManager = LocalFocusManager.current

    var expanded by remember { mutableStateOf(false) }
    var inputValue by remember(currentEntity, entities.size) {
        mutableStateOf(
            if (currentEntity == null) {
                ""
            } else {
                entities.firstOrNull { it.entityId == currentEntity }?.friendlyName ?: currentEntity
            },
        )
    }

    var list by remember { mutableStateOf(entities) }
    var listTooLarge by remember { mutableStateOf(false) }
    LaunchedEffect(entities.size, inputValue) {
        list = withContext(Dispatchers.IO) {
            val query = inputValue.trim()
            val items = if (inputValue.isBlank() ||
                inputValue == (entities.firstOrNull { it.entityId == currentEntity }?.friendlyName ?: currentEntity)
            ) {
                entities
            } else {
                entities.filter {
                    it.friendlyName.contains(query, ignoreCase = true) ||
                        it.entityId.contains(query.replace(" ", "_"), ignoreCase = true)
                }
            }
            // The amount of items is limited because Compose ExposedDropdownMenu isn't lazy
            listTooLarge = items.size > 75
            items.sortedWith(
                compareBy(
                    { !it.friendlyName.startsWith(query, ignoreCase = true) },
                    { !it.entityId.split(".")[1].startsWith(query.replace(" ", "_"), ignoreCase = true) },
                    { it.friendlyName.lowercase() },
                ),
            ).take(75)
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        TextField(
            value = inputValue,
            onValueChange = {
                expanded = true
                inputValue = it
            },
            label = label,
            trailingIcon =
            {
                Row {
                    if (currentEntity?.isNotBlank() == true) {
                        IconButton(onClick = onEntityCleared, modifier = Modifier.clearAndSetSemantics { }) {
                            Icon(
                                Icons.Filled.Clear,
                                stringResource(commonR.string.search_clear_selection),
                            )
                        }
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (list.isNotEmpty()) {
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                list.forEach {
                    DropdownMenuItem(
                        onClick = {
                            val setInput = onEntitySelected(it.entityId)
                            inputValue = if (setInput) it.friendlyName else ""
                            expanded = false
                            focusManager.clearFocus()
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Column {
                            Text(
                                text = it.friendlyName,
                                style = MaterialTheme.typography.body1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                                Text(
                                    text = it.entityId,
                                    style = MaterialTheme.typography.body2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                if (listTooLarge) {
                    DropdownMenuItem(onClick = { /* No-op */ }, enabled = false) {
                        Text(
                            text = stringResource(commonR.string.search_refine_for_more),
                            style = MaterialTheme.typography.body2,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
        }
    }
}
