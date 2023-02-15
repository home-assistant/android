package io.homeassistant.companion.android.settings.controls.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.data.integration.ControlsAuthRequiredSetting
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.util.compose.ServerExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.getEntityDomainString
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun ManageControlsView(
    authSetting: ControlsAuthRequiredSetting,
    authRequiredList: List<String>,
    entitiesLoaded: Boolean,
    entitiesList: Map<Int, List<Entity<*>>>,
    serversList: List<Server>,
    defaultServer: Int,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onSelectEntity: (String, Int) -> Unit
) {
    var selectedServer by remember { mutableStateOf(defaultServer) }
    LazyColumn(contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Text(
                text = stringResource(commonR.string.controls_setting_choose_setting),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (entitiesLoaded) {
            if (entitiesList.isNotEmpty()) {
                item {
                    Row(modifier = Modifier.padding(all = 16.dp)) {
                        OutlinedButton(
                            onClick = onSelectAll,
                            enabled = authSetting !== ControlsAuthRequiredSetting.NONE,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(commonR.string.controls_setting_choose_all))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        OutlinedButton(
                            onClick = onSelectNone,
                            enabled = authSetting !== ControlsAuthRequiredSetting.ALL,
                            modifier = Modifier.weight(1f)
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
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        )
                    }
                }
                items(entitiesList[selectedServer]!!.size, key = { "$selectedServer.${entitiesList[selectedServer]?.get(it)?.entityId}" }) { index ->
                    val entity = entitiesList[selectedServer]?.get(index) as Entity<Map<String, Any>>
                    ManageControlsEntity(
                        entityName = (
                            entity.attributes["friendly_name"]
                                ?: entity.entityId
                            ) as String,
                        entityDomain = entity.domain,
                        selected = (
                            authSetting == ControlsAuthRequiredSetting.NONE ||
                                (
                                    authSetting == ControlsAuthRequiredSetting.SELECTION &&
                                        !authRequiredList.contains("$selectedServer.${entity.entityId}")
                                    )
                            ),
                        onClick = { onSelectEntity(entity.entityId, selectedServer) }
                    )
                }
            } else {
                item {
                    Text(
                        text = stringResource(commonR.string.controls_setting_choose_empty),
                        modifier = Modifier.padding(all = 16.dp),
                        fontStyle = FontStyle.Italic
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
    }
}

@Composable
fun ManageControlsEntity(
    entityName: String,
    entityDomain: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .fillMaxWidth()
            .padding(all = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            modifier = Modifier.padding(end = 16.dp),
            onCheckedChange = null // Handled by parent Row clickable modifier
        )
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(text = entityName, style = MaterialTheme.typography.body1)
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Text(
                    text = getEntityDomainString(entityDomain),
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }
}
