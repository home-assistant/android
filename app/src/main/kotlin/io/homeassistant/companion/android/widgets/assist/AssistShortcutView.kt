package io.homeassistant.companion.android.widgets.assist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.assist.AssistViewModelBase
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineListResponse
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.util.compose.ExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.ServerExposedDropdownMenu
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import io.homeassistant.companion.android.util.safeTopWindowInsets

@Composable
fun AssistShortcutView(
    selectedServerId: Int,
    servers: List<Server>,
    supported: Boolean?,
    pipelines: AssistPipelineListResponse?,
    onSetServer: (Int) -> Unit,
    onSubmit: (String, Int, String?, Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(commonR.string.assist_shortcut)) },
                backgroundColor = colorResource(commonR.color.colorBackground),
                contentColor = colorResource(commonR.color.colorOnBackground),
                windowInsets = safeTopWindowInsets(),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(safeBottomPaddingValues())
                .padding(padding),
        ) {
            Column(modifier = Modifier.padding(all = 16.dp)) {
                val assist = stringResource(commonR.string.assist)
                var name by rememberSaveable { mutableStateOf(assist) }
                var startListening by rememberSaveable { mutableStateOf(true) }
                var pipelineId by rememberSaveable(selectedServerId) { mutableStateOf<String?>(null) }

                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(commonR.string.widget_text_hint_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
                if (servers.size > 1) {
                    ServerExposedDropdownMenu(
                        servers = servers,
                        current = selectedServerId,
                        onSelected = onSetServer,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
                if (supported == true) {
                    if (pipelines != null && pipelines.pipelines.isNotEmpty()) {
                        ExposedDropdownMenu(
                            label = stringResource(commonR.string.assist_pipeline),
                            keys = listOf(
                                stringResource(commonR.string.assist_last_used_pipeline),
                                stringResource(
                                    commonR.string.assist_preferred_pipeline,
                                    pipelines.pipelines.first { it.id == pipelines.preferredPipeline }.name,
                                ),
                            ) +
                                pipelines.pipelines.map { it.name },
                            currentIndex = when {
                                pipelineId == AssistViewModelBase.PIPELINE_LAST_USED -> 0
                                pipelineId == AssistViewModelBase.PIPELINE_PREFERRED -> 1
                                pipelineId != null -> 2 + pipelines.pipelines.indexOfFirst { it.id == pipelineId }
                                else -> 0
                            },
                            onSelected = {
                                pipelineId = when (it) {
                                    0 -> AssistViewModelBase.PIPELINE_LAST_USED
                                    1 -> AssistViewModelBase.PIPELINE_PREFERRED
                                    else -> pipelines.pipelines[it - 2].id
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Row(
                        modifier = Modifier.clickable { startListening = !startListening },
                    ) {
                        Text(
                            text = stringResource(commonR.string.assist_start_listening),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                        )
                        Switch(
                            checked = startListening,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                uncheckedThumbColor = colorResource(commonR.color.colorSwitchUncheckedThumb),
                            ),
                        )
                    }
                } else if (supported == false) {
                    Text(
                        stringResource(
                            commonR.string.no_assist_support,
                            "2023.5",
                            stringResource(commonR.string.no_assist_support_assist_pipeline),
                        ),
                    )
                }

                Button(
                    onClick = {
                        onSubmit(
                            name.ifBlank { assist },
                            selectedServerId,
                            pipelineId,
                            startListening,
                        )
                    },
                    enabled = supported == true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                ) {
                    Text(stringResource(commonR.string.add_shortcut))
                }
            }
        }
    }
}
