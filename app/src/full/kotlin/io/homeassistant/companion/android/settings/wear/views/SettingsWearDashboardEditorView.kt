package io.homeassistant.companion.android.settings.wear.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import io.homeassistant.companion.android.common.data.wear.dashboard.validation.ValidationResult
import io.homeassistant.companion.android.settings.wear.SettingsWearDashboardTemplates
import io.homeassistant.companion.android.settings.wear.SettingsWearViewModel
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsWearDashboardEditorView(
    settingsWearViewModel: SettingsWearViewModel,
    dashboardTemplates: SettingsWearDashboardTemplates,
    dashboardId: String?,
    templateId: String?,
    onSaved: () -> Unit,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val existingDashboard = dashboardId?.let { settingsWearViewModel.wearDashboards[it] }
    val templateDefinition = templateId?.let { dashboardTemplates.getTemplateDefinition(it) }
    var title by remember(dashboardId, templateId) {
        mutableStateOf(existingDashboard?.title.orEmpty())
    }
    var config by remember(dashboardId, templateId) {
        mutableStateOf(
            existingDashboard
                ?: templateId?.let { dashboardTemplates.loadTemplateConfig(it) },
        )
    }
    val entityAssignments = remember(templateId) { mutableStateMapOf<String, String>() }
    var jsonText by remember(dashboardId, templateId) {
        mutableStateOf(config?.let { settingsWearViewModel.encodeDashboardJson(it) }.orEmpty())
    }
    var validationResult by remember { mutableStateOf<ValidationResult?>(null) }
    var advancedExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(templateDefinition) {
        if (existingDashboard == null && title.isBlank()) {
            templateDefinition?.titleRes?.let { titleRes ->
                title = context.getString(titleRes)
            }
        }
    }

    LaunchedEffect(entityAssignments.entries.toList()) {
        if (templateId != null && entityAssignments.isNotEmpty()) {
            config = dashboardTemplates.instantiateTemplate(templateId, entityAssignments.toMap())
            config?.let { jsonText = settingsWearViewModel.encodeDashboardJson(it) }
        }
    }

    val screenTitle = existingDashboard?.title
        ?: templateDefinition?.let { stringResource(it.titleRes) }
        ?: stringResource(commonR.string.wear_dashboard)

    Scaffold(
        modifier = modifier,
        topBar = {
            SettingsWearTopAppBar(
                title = { Text(screenTitle) },
                onBackClicked = onBackClicked,
                docsLink = WEAR_DOCS_LINK,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(safeBottomPaddingValues())
                .padding(padding)
                .padding(all = 16.dp),
        ) {
            TextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(commonR.string.wear_dashboard_title)) },
                modifier = Modifier.fillMaxWidth(),
            )

            templateDefinition?.entitySlots?.takeIf { it.isNotEmpty() }?.let { slots ->
                Text(
                    text = stringResource(commonR.string.wear_dashboard_entities),
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                slots.forEach { slot ->
                    val selectedEntityId = entityAssignments[slot.slotId]
                    val domainFilter = slot.placeholderEntityId.substringBefore('.')
                    var availableEntities by remember { mutableStateOf<List<Entity>>(emptyList()) }
                    LaunchedEffect(settingsWearViewModel.entities.size, domainFilter) {
                        availableEntities = withContext(Dispatchers.IO) {
                            settingsWearViewModel.entities.values.filter {
                                it.entityId.startsWith("$domainFilter.")
                            }
                        }
                    }
                    Text(
                        text = stringResource(slot.labelRes),
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    HATheme {
                        EntityPicker(
                            entities = availableEntities,
                            selectedEntityId = selectedEntityId,
                            onEntityCleared = { entityAssignments.remove(slot.slotId) },
                            onEntitySelectedId = { entityAssignments[slot.slotId] = it },
                            addButtonText = stringResource(commonR.string.wear_dashboard_assign_entity),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            config?.let { currentConfig ->
                val result = settingsWearViewModel.validateDashboard(currentConfig)
                validationResult = result
                ValidationSummary(validationResult = result)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { advancedExpanded = !advancedExpanded }) {
                    Text(stringResource(commonR.string.wear_dashboard_advanced))
                }
            }

            if (advancedExpanded) {
                TextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    label = { Text(stringResource(commonR.string.wear_dashboard_json)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    minLines = 8,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            settingsWearViewModel.parseDashboardJson(jsonText)?.let { parsed ->
                                config = parsed
                                validationResult = settingsWearViewModel.validateDashboard(parsed)
                            }
                        },
                    ) {
                        Text(stringResource(commonR.string.wear_dashboard_json_import))
                    }
                    OutlinedButton(
                        onClick = {
                            config?.let { currentConfig ->
                                val encoded = settingsWearViewModel.encodeDashboardJson(currentConfig)
                                jsonText = encoded
                                clipboardManager.setText(AnnotatedString(encoded))
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(stringResource(commonR.string.wear_dashboard_json_export))
                    }
                }
                config?.let { currentConfig ->
                    ValidationSummary(
                        validationResult = settingsWearViewModel.validateDashboard(currentConfig),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            Button(
                onClick = {
                    val dashboardConfig = config ?: return@Button
                    var updatedConfig = dashboardConfig.copy(
                        title = title.ifBlank { dashboardConfig.title },
                    )
                    if (dashboardId == null && settingsWearViewModel.wearDashboards.containsKey(updatedConfig.id)) {
                        updatedConfig = updatedConfig.copy(
                            id = "${updatedConfig.id}_${settingsWearViewModel.wearDashboards.size + 1}",
                        )
                    }
                    settingsWearViewModel.saveDashboard(updatedConfig)
                    onSaved()
                },
                enabled = config != null && validationResult?.isValid == true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(stringResource(commonR.string.save))
            }
        }
    }
}

@Composable
private fun ValidationSummary(
    validationResult: ValidationResult?,
    modifier: Modifier = Modifier,
) {
    validationResult ?: return
    val message = if (validationResult.isValid) {
        stringResource(commonR.string.wear_dashboard_validation_valid)
    } else {
        validationResult.errors.joinToString("\n") { "${it.path}: ${it.message}" }
    }
    Text(
        text = message,
        style = MaterialTheme.typography.body2,
        color = if (validationResult.isValid) {
            MaterialTheme.colors.primary
        } else {
            MaterialTheme.colors.error
        },
        modifier = modifier.padding(top = 8.dp),
    )
}
