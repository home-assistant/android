package io.homeassistant.companion.android.settings.wear.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import io.homeassistant.companion.android.settings.views.SettingsRow
import io.homeassistant.companion.android.settings.wear.SettingsWearDashboardTemplates
import io.homeassistant.companion.android.settings.wear.SettingsWearViewModel
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@Composable
fun SettingsWearDashboardListView(
    settingsWearViewModel: SettingsWearViewModel,
    dashboardTemplates: SettingsWearDashboardTemplates,
    onDashboardClicked: (String) -> Unit,
    onAddFromTemplate: (String) -> Unit,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dashboards = settingsWearViewModel.wearDashboards
    var dashboardToDelete by remember { mutableStateOf<WearDashboardConfig?>(null) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    val templateDefinitions = remember { dashboardTemplates.getTemplateDefinitions() }

    dashboardToDelete?.let { dashboard ->
        AlertDialog(
            onDismissRequest = { dashboardToDelete = null },
            title = { Text(stringResource(commonR.string.wear_dashboard_delete)) },
            text = {
                Text(
                    stringResource(
                        commonR.string.wear_dashboard_delete_confirm,
                        dashboard.title ?: dashboard.id,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsWearViewModel.deleteDashboard(dashboard.id)
                        dashboardToDelete = null
                    },
                ) {
                    Text(stringResource(commonR.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { dashboardToDelete = null }) {
                    Text(stringResource(commonR.string.cancel))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            SettingsWearTopAppBar(
                title = { Text(stringResource(commonR.string.wear_dashboards)) },
                onBackClicked = onBackClicked,
                docsLink = WEAR_DOCS_LINK,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(safeBottomPaddingValues())
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { addMenuExpanded = true }) {
                    Text(stringResource(commonR.string.wear_dashboard_add_from_template))
                }
                DropdownMenu(
                    expanded = addMenuExpanded,
                    onDismissRequest = { addMenuExpanded = false },
                ) {
                    templateDefinitions.forEach { template ->
                        DropdownMenuItem(
                            onClick = {
                                addMenuExpanded = false
                                onAddFromTemplate(template.templateId)
                            },
                        ) {
                            Text(stringResource(template.titleRes))
                        }
                    }
                }
            }

            if (dashboards.isEmpty()) {
                Text(
                    text = stringResource(commonR.string.wear_dashboard_no_dashboards_yet),
                    modifier = Modifier.padding(all = 16.dp),
                )
            } else {
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .padding(start = 72.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(commonR.string.wear_dashboard_configure),
                        style = MaterialTheme.typography.body2,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary,
                    )
                }

                dashboards.values.sortedBy { it.title ?: it.id }.forEach { dashboard ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsRow(
                            primaryText = dashboard.title ?: dashboard.id,
                            secondaryText = dashboard.id,
                            mdiIcon = CommunityMaterial.Icon3.cmd_view_dashboard,
                            enabled = true,
                            onClicked = { onDashboardClicked(dashboard.id) },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { dashboardToDelete = dashboard }) {
                            Image(
                                asset = CommunityMaterial.Icon.cmd_delete,
                                colorFilter = ColorFilter.tint(colorResource(commonR.color.colorOnBackground)),
                            )
                        }
                    }
                }
            }
        }
    }
}
