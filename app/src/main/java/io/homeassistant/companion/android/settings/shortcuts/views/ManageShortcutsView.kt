package io.homeassistant.companion.android.settings.shortcuts.views

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentManager
import com.maltaisn.icondialog.IconDialog
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.settings.shortcuts.ManageShortcutsSettingsFragment
import io.homeassistant.companion.android.settings.shortcuts.ManageShortcutsViewModel

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
fun ManageShortcutsView(
    viewModel: ManageShortcutsViewModel,
    iconDialog: IconDialog,
    childFragment: FragmentManager
) {
    val context = LocalContext.current
    var expandedEntity by remember { mutableStateOf(false) }
    var expandedPinnedShortcuts by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.padding(20.dp)) {
        item {
            Text(
                text = stringResource(id = R.string.shortcut_instruction_desc),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Divider()
        }

        val shortcutCount = if (viewModel.canPinShortcuts)
            ManageShortcutsSettingsFragment.MAX_SHORTCUTS + 1
        else
            ManageShortcutsSettingsFragment.MAX_SHORTCUTS

        items(shortcutCount) { i ->
            val index = i + 1
            val shortcutId = ManageShortcutsSettingsFragment.SHORTCUT_PREFIX + "_" + index
            val existingShortcut = try {
                if (shortcutCount < 6)
                    viewModel.dynamicShortcuts.value[index]
                else {
                    // No op
                }
            } catch(e: Exception) {
                null
            }
            if (existingShortcut != null)
                viewModel.setDynamicShortcutData(shortcutId, index)
            Text(
                text = if (index < 6) stringResource(id = R.string.shortcut) + " $index" else stringResource(
                    id = R.string.shortcut_pinned
                ),
                fontSize = 20.sp,
                color = colorResource(id = io.homeassistant.companion.android.R.color.colorAccent),
                modifier = Modifier.padding(top = 20.dp)
            )

            if (index == 5) {
                Text(
                    text = stringResource(id = R.string.shortcut5_note),
                    fontSize = 14.sp
                )
            }

            if (index == 6) {
                Text(
                    text = stringResource(id = R.string.shortcut_pinned_note),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
                )

                val pinnedShortCutIds = viewModel.pinnedShortcuts.value.asSequence().map { it.id }.toList()

                if (pinnedShortCutIds.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(id = R.string.shortcut_pinned_list),
                            fontSize = 15.sp,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Box {
                            OutlinedButton(onClick = { expandedPinnedShortcuts = true }) {
                                Text(if (viewModel.shortcutIdPinned.value in pinnedShortCutIds) viewModel.shortcutIdPinned.value else "")
                            }

                            DropdownMenu(expanded = expandedPinnedShortcuts, onDismissRequest = { expandedPinnedShortcuts = false }) {
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
                    value = viewModel.shortcutIdPinned.value,
                    onValueChange = { viewModel.shortcutIdPinned.value = it },
                    label = {
                        Text(stringResource(id = R.string.shortcut_pinned_id))
                    }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(id = R.string.shortcut_icon),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(end = 10.dp)
                )
                OutlinedButton(onClick = {
                    iconDialog.show(childFragment, shortcutId)
                }) {
                    val icon = when (index) {
                        1 -> viewModel.drawableIcon1.value?.let { DrawableCompat.wrap(it) }
                        2 -> viewModel.drawableIcon2.value?.let { DrawableCompat.wrap(it) }
                        3 -> viewModel.drawableIcon3.value?.let { DrawableCompat.wrap(it) }
                        4 -> viewModel.drawableIcon4.value?.let { DrawableCompat.wrap(it) }
                        5 -> viewModel.drawableIcon5.value?.let { DrawableCompat.wrap(it) }
                        6 -> viewModel.drawableIconPinned.value?.let { DrawableCompat.wrap(it) }
                        else -> viewModel.drawableIcon1.value?.let { DrawableCompat.wrap(it) }
                    }
                    icon?.toBitmap()?.asImageBitmap()
                        ?.let {
                            Image(
                                it,
                                contentDescription = stringResource(id = R.string.shortcut_icon),
                                colorFilter = ColorFilter.tint(colorResource(io.homeassistant.companion.android.R.color.colorAccent))
                            )
                        }
                }
            }

            TextField(
                value = when (index) {
                    1 -> viewModel.shortcutLabel1.value
                    2 -> viewModel.shortcutLabel2.value
                    3 -> viewModel.shortcutLabel3.value
                    4 -> viewModel.shortcutLabel4.value
                    5 -> viewModel.shortcutLabel5.value
                    6 -> viewModel.shortcutLabelPinned.value
                    else -> viewModel.shortcutLabel1.value
                },
                onValueChange = { when (index) {
                    1 -> viewModel.shortcutLabel1.value = it
                    2 -> viewModel.shortcutLabel2.value = it
                    3 -> viewModel.shortcutLabel3.value = it
                    4 -> viewModel.shortcutLabel4.value = it
                    5 -> viewModel.shortcutLabel5.value = it
                    6 -> viewModel.shortcutLabelPinned.value = it
                    else -> viewModel.shortcutLabel1.value = it
                }},
                label = { Text(
                    if (index < 6)
                        "${stringResource(id = R.string.shortcut)} $index ${stringResource(id = R.string.label)}"
                    else
                        stringResource(id = R.string.shortcut_pinned_label)
                )},
                modifier = Modifier.padding(top = 16.dp)
            )

            TextField(
                value = when (index) {
                    1 -> viewModel.shortcutDesc1.value
                    2 -> viewModel.shortcutDesc2.value
                    3 -> viewModel.shortcutDesc3.value
                    4 -> viewModel.shortcutDesc4.value
                    5 -> viewModel.shortcutDesc5.value
                    6 -> viewModel.shortcutDescPinned.value
                    else -> viewModel.shortcutDesc1.value
                },
                onValueChange = { when (index) {
                    1 -> viewModel.shortcutDesc1.value = it
                    2 -> viewModel.shortcutDesc2.value = it
                    3 -> viewModel.shortcutDesc3.value = it
                    4 -> viewModel.shortcutDesc4.value = it
                    5 -> viewModel.shortcutDesc5.value = it
                    6 -> viewModel.shortcutDescPinned.value = it
                    else -> viewModel.shortcutDesc1.value = it
                } },
                label = { Text(
                    if (index < 6)
                        "${stringResource(id = R.string.shortcut)} $index ${stringResource(id = R.string.description)}"
                    else
                        stringResource(id = R.string.shortcut_pinned_desc)
                )},
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
            )

            Text(stringResource(id = R.string.shortcut_type))

            Row {
                ShortcutRadioButtonRow(viewModel = viewModel, type = "lovelace", index = index)
                ShortcutRadioButtonRow(viewModel = viewModel, type = "entityId", index = index)
            }

            if (
                when (index) {
                    1 -> viewModel.shortcutType1.value == "lovelace"
                    2 -> viewModel.shortcutType2.value == "lovelace"
                    3 -> viewModel.shortcutType3.value == "lovelace"
                    4 -> viewModel.shortcutType4.value == "lovelace"
                    5 -> viewModel.shortcutType5.value == "lovelace"
                    6 -> viewModel.shortcutTypePinned.value == "lovelace"
                    else -> viewModel.shortcutType1.value == "lovelace"
                }) {
                TextField(
                    value = when (index) {
                        1 -> viewModel.shortcutPath1.value
                        2 -> viewModel.shortcutPath2.value
                        3 -> viewModel.shortcutPath3.value
                        4 -> viewModel.shortcutPath4.value
                        5 -> viewModel.shortcutPath5.value
                        6 -> viewModel.shortcutPathPinned.value
                        else -> viewModel.shortcutPath1.value
                    },
                    onValueChange = { when (index) {
                        1 -> viewModel.shortcutPath1.value = it
                        2 -> viewModel.shortcutPath2.value = it
                        3 -> viewModel.shortcutPath3.value = it
                        4 -> viewModel.shortcutPath4.value = it
                        5 -> viewModel.shortcutPath5.value = it
                        6 -> viewModel.shortcutPathPinned.value = it
                    } },
                    label = { Text(stringResource(id = R.string.lovelace_view_dashboard)) },
                    modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                )
            } else {
                Text(
                    text = stringResource(id = R.string.entity_id),
                    fontSize = 15.sp
                )
                OutlinedButton(onClick = { expandedEntity = true }) {
                    Text(text = when (index) {
                        1 -> viewModel.shortcutPath1.value
                        2 -> viewModel.shortcutPath2.value
                        3 -> viewModel.shortcutPath3.value
                        4 -> viewModel.shortcutPath4.value
                        5 -> viewModel.shortcutPath5.value
                        6 -> viewModel.shortcutPathPinned.value
                        else -> viewModel.shortcutPath1.value
                    })
                }

                DropdownMenu(expanded = expandedEntity, onDismissRequest = { expandedEntity = false }) {
                    val sortedEntities = viewModel.entities.values.sortedBy { it.entityId }
                    for (item in sortedEntities) {
                        DropdownMenuItem(onClick = {
                            when (index) {
                                1 -> viewModel.shortcutPath1.value = "entityId:${item.entityId}"
                                2 -> viewModel.shortcutPath2.value = "entityId:${item.entityId}"
                                3 -> viewModel.shortcutPath3.value = "entityId:${item.entityId}"
                                4 -> viewModel.shortcutPath4.value = "entityId:${item.entityId}"
                                5 -> viewModel.shortcutPath5.value = "entityId:${item.entityId}"
                                6 -> viewModel.shortcutPathPinned.value = "entityId:${item.entityId}"
                            }
                            expandedEntity = false
                        }) {
                            Text(text = item.entityId, fontSize = 15.sp)
                        }
                    }
                }
            }
            for (item in viewModel.dynamicShortcuts.value) {
                if (item.id == shortcutId) {
                    when (index) {
                        1 -> viewModel.deleteShortcut1.value = true
                        2 -> viewModel.deleteShortcut2.value = true
                        3 -> viewModel.deleteShortcut3.value = true
                        4 -> viewModel.deleteShortcut4.value = true
                        5 -> viewModel.deleteShortcut5.value = true
                    }
                }
            }
            Button(
                onClick = {
                          when (index) {
                              1 -> {
                                  if (viewModel.deleteShortcut1.value)
                                      Toast.makeText(context, R.string.shortcut_updated, Toast.LENGTH_SHORT).show()
                                  viewModel.deleteShortcut1.value = true
                                  viewModel.createShortcut(shortcutId, viewModel.shortcutLabel1.value, viewModel.shortcutDesc1.value, viewModel.shortcutPath1.value, viewModel.drawableIcon1.value?.toBitmap(), viewModel.selectedIcon1.value)
                              }
                              2 -> {
                                  viewModel.deleteShortcut2.value = true
                                  viewModel.createShortcut(shortcutId, viewModel.shortcutLabel2.value, viewModel.shortcutDesc2.value, viewModel.shortcutPath2.value, viewModel.drawableIcon2.value?.toBitmap(), viewModel.selectedIcon2.value)
                              }
                              3 -> {
                                  viewModel.deleteShortcut3.value = true
                                  viewModel.createShortcut(shortcutId, viewModel.shortcutLabel3.value, viewModel.shortcutDesc3.value, viewModel.shortcutPath3.value, viewModel.drawableIcon3.value?.toBitmap(), viewModel.selectedIcon3.value)
                              }
                              4 -> {
                                  viewModel.deleteShortcut4.value = true
                                  viewModel.createShortcut(shortcutId, viewModel.shortcutLabel4.value, viewModel.shortcutDesc4.value, viewModel.shortcutPath4.value, viewModel.drawableIcon4.value?.toBitmap(), viewModel.selectedIcon4.value)
                              }
                              5 -> {
                                  viewModel.deleteShortcut5.value = true
                                  viewModel.createShortcut(shortcutId, viewModel.shortcutLabel5.value, viewModel.shortcutDesc5.value, viewModel.shortcutPath5.value, viewModel.drawableIcon5.value?.toBitmap(), viewModel.selectedIcon5.value)
                              }
                              6 -> viewModel.createShortcut(viewModel.shortcutIdPinned.value, viewModel.shortcutLabelPinned.value, viewModel.shortcutDescPinned.value, viewModel.shortcutPathPinned.value, viewModel.drawableIconPinned.value?.toBitmap(), viewModel.selectedIconPinned.value)
                          }
                },
                enabled = when (index) {
                    1 -> viewModel.shortcutLabel1.value.isNotEmpty() && viewModel.shortcutDesc1.value.isNotEmpty() && viewModel.shortcutPath1.value.isNotEmpty()
                    2 -> viewModel.shortcutLabel2.value.isNotEmpty() && viewModel.shortcutDesc2.value.isNotEmpty() && viewModel.shortcutPath2.value.isNotEmpty()
                    3 -> viewModel.shortcutLabel3.value.isNotEmpty() && viewModel.shortcutDesc3.value.isNotEmpty() && viewModel.shortcutPath3.value.isNotEmpty()
                    4 -> viewModel.shortcutLabel4.value.isNotEmpty() && viewModel.shortcutDesc4.value.isNotEmpty() && viewModel.shortcutPath4.value.isNotEmpty()
                    5 -> viewModel.shortcutLabel5.value.isNotEmpty() && viewModel.shortcutDesc5.value.isNotEmpty() && viewModel.shortcutPath5.value.isNotEmpty()
                    6 -> viewModel.shortcutIdPinned.value.isNotEmpty() && viewModel.shortcutLabelPinned.value.isNotEmpty() && viewModel.shortcutDescPinned.value.isNotEmpty() && viewModel.shortcutPathPinned.value.isNotEmpty()
                    else -> viewModel.shortcutLabel1.value.isNotEmpty() && viewModel.shortcutDesc1.value.isNotEmpty() && viewModel.shortcutPath1.value.isNotEmpty()
                }
            ) {
                Text(text = stringResource(id = if (when (index) {
                        1 -> viewModel.deleteShortcut1.value
                        2 -> viewModel.deleteShortcut2.value
                        3 -> viewModel.deleteShortcut3.value
                        4 -> viewModel.deleteShortcut4.value
                        5 -> viewModel.deleteShortcut5.value
                        6 -> {
                            var isCurrentPinned = false
                            if (viewModel.pinnedShortcuts.value.isEmpty())
                                isCurrentPinned = false
                            else {
                                for (item in viewModel.pinnedShortcuts.value) {
                                    isCurrentPinned = when (item.id) {
                                        viewModel.shortcutIdPinned.value -> true
                                        else -> false
                                    }
                                }
                            }
                            isCurrentPinned
                        }
                        else -> viewModel.deleteShortcut1.value
                    }) R.string.update_shortcut else R.string.add_shortcut)
                )
            }

            when (index) {
                1 -> if (viewModel.deleteShortcut1.value) AddDeleteButton(viewModel = viewModel, shortcutId = shortcutId, index)
                2 -> if (viewModel.deleteShortcut2.value) AddDeleteButton(viewModel = viewModel, shortcutId = shortcutId, index)
                3 -> if (viewModel.deleteShortcut3.value) AddDeleteButton(viewModel = viewModel, shortcutId = shortcutId, index)
                4 -> if (viewModel.deleteShortcut4.value) AddDeleteButton(viewModel = viewModel, shortcutId = shortcutId, index)
                5 -> if (viewModel.deleteShortcut5.value) AddDeleteButton(viewModel = viewModel, shortcutId = shortcutId, index)
            }
            if (index < 6)
                Divider()
        }
    }
}

@Composable
private fun ShortcutRadioButtonRow(viewModel: ManageShortcutsViewModel, type: String, index: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = when (index) {
                1 -> viewModel.shortcutType1.value == type
                2 -> viewModel.shortcutType2.value == type
                3 -> viewModel.shortcutType3.value == type
                4 -> viewModel.shortcutType4.value == type
                5 -> viewModel.shortcutType5.value == type
                else -> viewModel.shortcutType1.value == type
            },
            onClick = { when (index) {
                1 -> viewModel.shortcutType1.value = type
                2 -> viewModel.shortcutType2.value = type
                3 -> viewModel.shortcutType3.value = type
                4 -> viewModel.shortcutType4.value = type
                5 -> viewModel.shortcutType5.value = type
            } })
        Text(stringResource(id = if (type == "lovelace") R.string.lovelace else R.string.entity_id))
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
private fun AddDeleteButton(viewModel: ManageShortcutsViewModel, shortcutId: String, index: Int) {
    Button(
        onClick = {
            viewModel.deleteShortcut(shortcutId)
            when (index) {
                1 -> viewModel.deleteShortcut1.value = false
                2 -> viewModel.deleteShortcut2.value = false
                3 -> viewModel.deleteShortcut3.value = false
                4 -> viewModel.deleteShortcut4.value = false
                5 -> viewModel.deleteShortcut5.value = false
            }
            }
    ) {
        Text(stringResource(id = R.string.delete_shortcut))
    }
}
