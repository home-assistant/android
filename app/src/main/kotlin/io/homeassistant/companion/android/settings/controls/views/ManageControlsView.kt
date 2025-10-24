package io.homeassistant.companion.android.settings.controls.views

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.RadioButton
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.ControlsAuthRequiredSetting
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.util.compose.HaAlertWarning
import io.homeassistant.companion.android.util.compose.ServerExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.getEntityDomainString
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@Composable
fun ManageControlsView(
    panelEnabled: Boolean,
    authSetting: ControlsAuthRequiredSetting,
    authRequiredList: List<String>,
    entitiesLoaded: Boolean,
    entitiesList: Map<Int, List<Entity>>,
    panelSetting: Pair<String?, Int>?,
    serversList: List<Server>,
    structureEnabled: Boolean,
    defaultServer: Int,
    onSetPanelEnabled: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onSelectEntity: (String, Int) -> Unit,
    onSetPanelSetting: (String, Int) -> Unit,
    onSetStructureEnabled: (Boolean) -> Unit,
) {
    var selectedServer by remember(defaultServer) { mutableIntStateOf(defaultServer) }
    val initialPanelEnabled by rememberSaveable { mutableStateOf(panelEnabled) }
    var panelServer by remember(panelSetting?.second) { mutableIntStateOf(panelSetting?.second ?: defaultServer) }
    var panelPath by remember(panelSetting?.first) { mutableStateOf(panelSetting?.first ?: "") }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 16.dp) + safeBottomPaddingValues(applyHorizontal = false),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            item {
                Text(
                    text = stringResource(commonR.string.controls_setting_panel),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 48.dp)
                        .height(IntrinsicSize.Min),
                ) {
                    ManageControlsModeButton(
                        isPanel = false,
                        selected = !panelEnabled,
                        onClick = { onSetPanelEnabled(false) },
                        modifier = Modifier.weight(0.5f),
                    )
                    Divider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp),
                    )
                    ManageControlsModeButton(
                        isPanel = true,
                        selected = panelEnabled,
                        onClick = { onSetPanelEnabled(true) },
                        modifier = Modifier.weight(0.5f),
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || !panelEnabled) {
            if (serversList.size > 1) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 16.dp, bottom = 16.dp, end = 16.dp)
                            .fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(commonR.string.controls_structure_enabled),
                                fontSize = 15.sp,
                            )
                        }
                        Switch(
                            checked = structureEnabled,
                            onCheckedChange = { onSetStructureEnabled(it) },
                            colors = SwitchDefaults.colors(
                                uncheckedThumbColor = colorResource(R.color.colorSwitchUncheckedThumb),
                            ),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(commonR.string.controls_setting_choose_setting),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (entitiesLoaded) {
                if (entitiesList.isNotEmpty()) {
                    item {
                        Row(modifier = Modifier.padding(all = 16.dp)) {
                            OutlinedButton(
                                onClick = onSelectAll,
                                enabled = authSetting !== ControlsAuthRequiredSetting.NONE,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(commonR.string.controls_setting_choose_all))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            OutlinedButton(
                                onClick = onSelectNone,
                                enabled = authSetting !== ControlsAuthRequiredSetting.ALL,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(commonR.string.controls_setting_choose_none))
                            }
                        }
                    }
                    if (serversList.size > 1) {
                        item {
                            ServerExposedDropdownMenu(
                                servers = serversList,
                                current = selectedServer,
                                onSelected = { selectedServer = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            )
                        }
                    }
                    items(entitiesList[selectedServer]?.size ?: 0, key = {
                        "$selectedServer.${entitiesList[selectedServer]?.get(it)?.entityId}"
                    }) { index ->
                        val entity = entitiesList[selectedServer]?.get(index) ?: return@items
                        ManageControlsEntity(
                            entityName = entity.friendlyName,
                            entityDomain = entity.domain,
                            selected = (
                                authSetting == ControlsAuthRequiredSetting.NONE ||
                                    (
                                        authSetting == ControlsAuthRequiredSetting.SELECTION &&
                                            !authRequiredList.contains("$selectedServer.${entity.entityId}")
                                        )
                                ),
                            onClick = { onSelectEntity(entity.entityId, selectedServer) },
                        )
                    }
                } else {
                    item {
                        Text(
                            text = stringResource(commonR.string.controls_setting_choose_empty),
                            modifier = Modifier.padding(all = 16.dp),
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            } else {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        } else {
            if (!initialPanelEnabled) {
                item {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                    ) {
                        HaAlertWarning(
                            message = stringResource(commonR.string.controls_setting_alert),
                            action = null,
                            onActionClicked = {},
                        )
                    }
                }
            }
            item {
                Text(
                    text = stringResource(commonR.string.controls_setting_dashboard_setting),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (serversList.size > 1) {
                item {
                    ServerExposedDropdownMenu(
                        servers = serversList,
                        current = panelServer,
                        onSelected = { panelServer = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp),
                    )
                }
            }
            item {
                TextField(
                    value = panelPath,
                    onValueChange = { panelPath = it },
                    label = { Text(stringResource(id = R.string.lovelace_view_dashboard)) },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 16.dp),
                )
            }
            item {
                Row(
                    modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
                ) {
                    Button(
                        enabled = (
                            (
                                panelPath != panelSetting?.first &&
                                    !(panelPath == "" && panelSetting != null && panelSetting.first == null)
                                ) ||
                                panelServer != panelSetting.second
                            ),
                        onClick = { onSetPanelSetting(panelPath, panelServer) },
                    ) {
                        Text(stringResource(commonR.string.save))
                    }
                }
            }
        }
    }
}

@Composable
fun ManageControlsEntity(entityName: String, entityDomain: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .fillMaxWidth()
            .padding(all = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            modifier = Modifier.padding(end = 16.dp),
            // Handled by parent Row clickable modifier
            onCheckedChange = null,
        )
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(text = entityName, style = MaterialTheme.typography.body1)
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Text(
                    text = getEntityDomainString(entityDomain),
                    style = MaterialTheme.typography.body2,
                )
            }
        }
    }
}

@Composable
fun ManageControlsModeButton(isPanel: Boolean, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(IntrinsicSize.Max)
            .selectable(selected = selected, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(all = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                asset = if (isPanel) {
                    CommunityMaterial.Icon3.cmd_view_dashboard
                } else {
                    CommunityMaterial.Icon.cmd_dip_switch
                },
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                colorFilter = ColorFilter.tint(LocalContentColor.current),
            )
            Text(
                text = stringResource(
                    if (isPanel) commonR.string.lovelace else commonR.string.controls_setting_mode_builtin_title,
                ),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                // Add newline at the end for spacing
                text = "${stringResource(
                    if (isPanel) {
                        commonR.string.controls_setting_mode_panel_info
                    } else {
                        commonR.string.controls_setting_mode_builtin_info
                    },
                )}\n",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            RadioButton(
                selected = selected,
                // Handled by parent
                onClick = null,
            )
        }
    }
}
