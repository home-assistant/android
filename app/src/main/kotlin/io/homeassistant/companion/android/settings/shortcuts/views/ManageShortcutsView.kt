package io.homeassistant.companion.android.settings.shortcuts.views

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedButton
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.iconics.compose.IconicsPainter
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.settings.shortcuts.ManageShortcutsSettingsFragment
import io.homeassistant.companion.android.settings.shortcuts.ManageShortcutsViewModel
import io.homeassistant.companion.android.util.compose.ServerExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.SingleEntityPicker
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
fun ManageShortcutsView(viewModel: ManageShortcutsViewModel, showIconDialog: (tag: String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(all = 16.dp) + safeBottomPaddingValues(applyHorizontal = false),
    ) {
        item {
            Text(
                text = stringResource(id = R.string.shortcut_instruction_desc),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Divider()
        }

        val shortcutCount = if (viewModel.canPinShortcuts) {
            ManageShortcutsSettingsFragment.MAX_SHORTCUTS + 1
        } else {
            ManageShortcutsSettingsFragment.MAX_SHORTCUTS
        }

        items(shortcutCount) { i ->
            CreateShortcutView(
                i = i,
                viewModel = viewModel,
                showIconDialog = showIconDialog,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
private fun CreateShortcutView(i: Int, viewModel: ManageShortcutsViewModel, showIconDialog: (tag: String) -> Unit) {
    val context = LocalContext.current
    var expandedPinnedShortcuts by remember { mutableStateOf(false) }

    val index = i + 1
    val shortcut = viewModel.shortcuts[i]
    val shortcutId = ManageShortcutsSettingsFragment.SHORTCUT_PREFIX + "_" + index

    Text(
        text = if (index < 6) {
            stringResource(id = R.string.shortcut) + " $index"
        } else {
            stringResource(
                id = R.string.shortcut_pinned,
            )
        },
        fontSize = 20.sp,
        color = colorResource(id = R.color.colorAccent),
        modifier = Modifier.padding(top = 20.dp),
    )

    if (index == 5) {
        Text(
            text = stringResource(id = R.string.shortcut5_note),
            fontSize = 14.sp,
        )
    }

    if (index == 6) {
        Text(
            text = stringResource(id = R.string.shortcut_pinned_note),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
        )

        val pinnedShortCutIds = viewModel.pinnedShortcuts.asSequence().map { it.id }.toList()

        if (pinnedShortCutIds.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(id = R.string.shortcut_pinned_list),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Box {
                    OutlinedButton(onClick = { expandedPinnedShortcuts = true }) {
                        Text(
                            if (viewModel.shortcuts[i].id.value in
                                pinnedShortCutIds
                            ) {
                                viewModel.shortcuts[i].id.value ?: ""
                            } else {
                                ""
                            },
                        )
                    }

                    DropdownMenu(expanded = expandedPinnedShortcuts, onDismissRequest = {
                        expandedPinnedShortcuts =
                            false
                    }) {
                        for (item in pinnedShortCutIds) {
                            DropdownMenuItem(onClick = {
                                viewModel.setPinnedShortcutData(item)
                                expandedPinnedShortcuts = false
                            }) {
                                Text(item)
                            }
                        }
                    }
                }
            }
        }
        TextField(
            value = viewModel.shortcuts[i].id.value ?: "",
            onValueChange = { viewModel.shortcuts[i].id.value = it },
            label = {
                Text(stringResource(id = R.string.shortcut_pinned_id))
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(id = R.string.shortcut_icon),
            fontSize = 15.sp,
            modifier = Modifier.padding(end = 10.dp),
        )
        OutlinedButton(onClick = {
            showIconDialog(shortcutId)
        }) {
            val icon = viewModel.shortcuts[i].selectedIcon.value
            val painter = if (icon != null) {
                remember(icon) { IconicsPainter(icon) }
            } else {
                painterResource(R.drawable.ic_stat_ic_notification_blue)
            }

            Image(
                painter = painter,
                contentDescription = stringResource(id = R.string.shortcut_icon),
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(colorResource(R.color.colorAccent)),
            )
        }
    }

    TextField(
        value = viewModel.shortcuts[i].label.value,
        onValueChange = { viewModel.shortcuts[i].label.value = it },
        label = {
            Text(
                if (index < 6) {
                    "${stringResource(id = R.string.shortcut)} $index ${stringResource(id = R.string.label)}"
                } else {
                    stringResource(id = R.string.shortcut_pinned_label)
                },
            )
        },
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )

    TextField(
        value = viewModel.shortcuts[i].desc.value,
        onValueChange = { viewModel.shortcuts[i].desc.value = it },
        label = {
            Text(
                if (index < 6) {
                    "${stringResource(id = R.string.shortcut)} $index ${stringResource(id = R.string.description)}"
                } else {
                    stringResource(id = R.string.shortcut_pinned_desc)
                },
            )
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    )

    if (viewModel.servers.size > 1 || viewModel.servers.none { it.id == shortcut.serverId.value }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServerExposedDropdownMenu(
                servers = viewModel.servers,
                current = shortcut.serverId.value,
                onSelected = { viewModel.shortcuts[i].serverId.value = it },
            )
        }
    }

    Text(
        text = stringResource(id = R.string.shortcut_type),
        modifier = Modifier.padding(top = 16.dp),
    )

    Row {
        ShortcutRadioButtonRow(viewModel = viewModel, type = "lovelace", index = i)
        ShortcutRadioButtonRow(viewModel = viewModel, type = "entityId", index = i)
    }

    if (viewModel.shortcuts[i].type.value == "lovelace") {
        TextField(
            value = viewModel.shortcuts[i].path.value,
            onValueChange = { viewModel.shortcuts[i].path.value = it },
            label = { Text(stringResource(id = R.string.lovelace_view_dashboard)) },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
    } else {
        SingleEntityPicker(
            entities = viewModel.entities[shortcut.serverId.value].orEmpty(),
            currentEntity = viewModel.shortcuts[i].path.value.split(":").getOrNull(1),
            onEntityCleared = {
                viewModel.shortcuts[i].path.value = ""
            },
            onEntitySelected = {
                viewModel.shortcuts[i].path.value = "entityId:$it"
                return@SingleEntityPicker true
            },
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
    for (item in viewModel.dynamicShortcuts) {
        if (item.id == shortcutId) {
            viewModel.shortcuts[i].delete.value = true
        }
    }
    Button(
        onClick = {
            if (index < 6) {
                if (viewModel.shortcuts[i].delete.value) {
                    Toast.makeText(context, R.string.shortcut_updated, Toast.LENGTH_SHORT).show()
                }
                viewModel.shortcuts[i].delete.value = true
            }
            viewModel.createShortcut(
                if (index < 6) shortcutId else shortcut.id.value!!,
                shortcut.serverId.value,
                shortcut.label.value,
                shortcut.desc.value,
                shortcut.path.value,
                shortcut.selectedIcon.value,
            )
        },
        enabled =
        (index < 6 || !shortcut.id.value.isNullOrEmpty()) &&
            shortcut.label.value.isNotEmpty() &&
            shortcut.desc.value.isNotEmpty() &&
            shortcut.path.value.isNotEmpty() &&
            viewModel.servers.any { it.id == shortcut.serverId.value },
    ) {
        Text(
            text = stringResource(
                id =
                if (
                    if (index < 6) {
                        viewModel.shortcuts[i].delete.value
                    } else {
                        var isCurrentPinned = false
                        if (viewModel.pinnedShortcuts.isEmpty()) {
                            isCurrentPinned = false
                        } else {
                            for (item in viewModel.pinnedShortcuts) {
                                isCurrentPinned = when (item.id) {
                                    viewModel.shortcuts.last().id.value -> true
                                    else -> false
                                }
                            }
                        }
                        isCurrentPinned
                    }
                ) {
                    R.string.update_shortcut
                } else {
                    R.string.add_shortcut
                },
            ),
        )
    }

    if (index < 6 && viewModel.shortcuts[i].delete.value) {
        AddDeleteButton(viewModel = viewModel, shortcutId = shortcutId, i)
        Divider()
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
private fun ShortcutRadioButtonRow(viewModel: ManageShortcutsViewModel, type: String, index: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = viewModel.shortcuts[index].type.value == type,
            onClick = { viewModel.shortcuts[index].type.value = type },
        )
        Text(stringResource(id = if (type == "lovelace") R.string.lovelace else R.string.entity))
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
private fun AddDeleteButton(viewModel: ManageShortcutsViewModel, shortcutId: String, index: Int) {
    Button(
        onClick = {
            viewModel.deleteShortcut(shortcutId)
            viewModel.shortcuts[index].delete.value = false
        },
    ) {
        Text(stringResource(id = R.string.delete_shortcut))
    }
}
