package io.homeassistant.companion.android.settings.shortcuts.views

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.mikepenz.iconics.compose.IconicsPainter
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HARadioGroup
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.RadioOption
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.settings.shortcuts.ManageShortcutsSettingsFragment
import io.homeassistant.companion.android.settings.shortcuts.ManageShortcutsViewModel
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
fun ManageShortcutsView(
    viewModel: ManageShortcutsViewModel,
    showIconDialog: (tag: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(all = HADimens.SPACE4) + safeBottomPaddingValues(applyHorizontal = false),
    ) {
        item {
            Text(
                text = stringResource(id = R.string.shortcut_instruction_desc),
                style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                modifier = Modifier.padding(bottom = HADimens.SPACE3),
            )
            HAHorizontalDivider()
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
    val colorScheme = LocalHAColorScheme.current

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
        style = HATextStyle.HeadlineMedium.copy(
            color = colorScheme.colorTextPrimary,
            textAlign = TextAlign.Start,
        ),
        modifier = Modifier.padding(top = HADimens.SPACE5),
    )

    if (index == 5) {
        Text(
            text = stringResource(id = R.string.shortcut5_note),
            style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
        )
    }

    if (index == 6) {
        Text(
            text = stringResource(id = R.string.shortcut_pinned_note),
            style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
            modifier = Modifier.padding(top = HADimens.SPACE3, bottom = HADimens.SPACE3),
        )

        val pinnedShortCutIds = viewModel.pinnedShortcuts.asSequence().map { it.id }.toList()

        if (pinnedShortCutIds.isNotEmpty()) {
            HADropdownMenu(
                items = pinnedShortCutIds.map { item -> HADropdownItem(key = item, label = item) },
                selectedKey = viewModel.shortcuts[i].id.value?.takeIf { it in pinnedShortCutIds },
                onItemSelected = viewModel::setPinnedShortcutData,
                label = stringResource(id = R.string.shortcut_pinned_list),
                modifier = Modifier.padding(bottom = HADimens.SPACE4),
            )
        }
        HATextField(
            value = viewModel.shortcuts[i].id.value ?: "",
            onValueChange = { viewModel.shortcuts[i].id.value = it },
            label = {
                Text(stringResource(id = R.string.shortcut_pinned_id))
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    HAFilledButton(
        text = stringResource(id = R.string.shortcut_icon),
        onClick = {
            showIconDialog(shortcutId)
        },
        variant = ButtonVariant.NEUTRAL,
        size = ButtonSize.LARGE,
        prefix = {
            val icon = viewModel.shortcuts[i].selectedIcon.value
            val painter = if (icon != null) {
                remember(icon) { IconicsPainter(icon) }
            } else {
                painterResource(R.drawable.ic_stat_ic_notification_blue)
            }

            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(HASize.X2L),
                colorFilter = ColorFilter.tint(colorScheme.colorOnNeutralNormal),
            )
        },
        modifier = Modifier.padding(top = HADimens.SPACE3),
    )

    HATextField(
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HADimens.SPACE4),
    )

    HATextField(
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = HADimens.SPACE4),
    )

    if (viewModel.servers.size > 1 || viewModel.servers.none { it.id == shortcut.serverId.value }) {
        HADropdownMenu(
            items = viewModel.servers.map { server ->
                HADropdownItem(key = server.id, label = server.friendlyName)
            },
            selectedKey = shortcut.serverId.value.takeIf { serverId ->
                viewModel.servers.any { it.id == serverId }
            },
            onItemSelected = { viewModel.shortcuts[i].serverId.value = it },
            label = stringResource(id = R.string.server_select),
            modifier = Modifier.padding(bottom = HADimens.SPACE4),
        )
    }

    Text(
        text = stringResource(id = R.string.shortcut_type),
        style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
        modifier = Modifier.padding(top = HADimens.SPACE4),
    )

    ShortcutTypeRadioGroup(viewModel = viewModel, index = i)

    if (viewModel.shortcuts[i].type.value == "lovelace") {
        HATextField(
            value = viewModel.shortcuts[i].path.value,
            onValueChange = { viewModel.shortcuts[i].path.value = it },
            label = { Text(stringResource(id = R.string.lovelace_view_dashboard)) },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = HADimens.SPACE4),
        )
    } else {
        EntityPicker(
            entities = viewModel.entities[shortcut.serverId.value].orEmpty(),
            entityRegistry = viewModel.entityRegistry[shortcut.serverId.value],
            deviceRegistry = viewModel.deviceRegistry[shortcut.serverId.value],
            areaRegistry = viewModel.areaRegistry[shortcut.serverId.value],
            selectedEntityId = viewModel.shortcuts[i].path.value.split(":").getOrNull(1),
            onEntitySelectedId = { entityId ->
                viewModel.shortcuts[i].path.value = "entityId:$entityId"
            },
            onEntityCleared = {
                viewModel.shortcuts[i].path.value = ""
            },
            modifier = Modifier.padding(bottom = HADimens.SPACE4),
        )
    }
    for (item in viewModel.dynamicShortcuts) {
        if (item.id == shortcutId) {
            viewModel.shortcuts[i].delete.value = true
        }
    }
    HAFilledButton(
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
    )

    if (index < 6 && viewModel.shortcuts[i].delete.value) {
        AddDeleteButton(viewModel = viewModel, shortcutId = shortcutId, i)
        HAHorizontalDivider(modifier = Modifier.padding(top = HADimens.SPACE4))
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
private fun ShortcutTypeRadioGroup(viewModel: ManageShortcutsViewModel, index: Int) {
    HARadioGroup(
        options = listOf(
            RadioOption(selectionKey = "lovelace", headline = stringResource(id = R.string.lovelace)),
            RadioOption(selectionKey = "entityId", headline = stringResource(id = R.string.entity)),
        ),
        selectionKey = viewModel.shortcuts[index].type.value,
        onSelect = { option -> viewModel.shortcuts[index].type.value = option.selectionKey },
        spaceBy = HADimens.SPACE3,
        modifier = Modifier.padding(vertical = HADimens.SPACE3),
    )
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
private fun AddDeleteButton(viewModel: ManageShortcutsViewModel, shortcutId: String, index: Int) {
    HAPlainButton(
        text = stringResource(id = R.string.delete_shortcut),
        onClick = {
            viewModel.deleteShortcut(shortcutId)
            viewModel.shortcuts[index].delete.value = false
        },
        variant = ButtonVariant.DANGER,
        modifier = Modifier.padding(top = HADimens.SPACE3),
    )
}
