package io.homeassistant.companion.android.matter.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.matter.MatterCommissioningViewModel.CommissioningFlowStep
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.compose.STEP_SCREEN_MAX_WIDTH_DP
import io.homeassistant.companion.android.util.compose.screenWidth
import kotlin.math.min

@Composable
fun MatterCommissioningView(
    step: CommissioningFlowStep,
    deviceName: String?,
    servers: List<Server>,
    onSelectServer: (Int) -> Unit,
    onConfirmCommissioning: () -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit,
) {
    if (step == CommissioningFlowStep.NotStarted) return

    val screenWidth = screenWidth()
    val loadingSteps = listOf(
        CommissioningFlowStep.NotStarted,
        CommissioningFlowStep.CheckingCore,
        CommissioningFlowStep.Working,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp)
                .width(min(screenWidth.value, STEP_SCREEN_MAX_WIDTH_DP).dp)
                .align(Alignment.Center),
        ) {
            MatterCommissioningViewHeader()

            ProvideTextStyle(MaterialTheme.typography.body1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally),
                ) {
                    if (step in loadingSteps) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            if (step is CommissioningFlowStep.Working) {
                                Text(
                                    text = stringResource(commonR.string.matter_shared_status_working),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth(0.67f)
                                        .padding(top = 16.dp),
                                )
                            }
                        }
                    } else {
                        Text(
                            text = when (step) {
                                CommissioningFlowStep.NotRegistered -> stringResource(
                                    commonR.string.matter_shared_status_not_registered,
                                )
                                CommissioningFlowStep.SelectServer -> stringResource(
                                    commonR.string.matter_shared_status_select_server,
                                )
                                CommissioningFlowStep.NotSupported -> stringResource(
                                    commonR.string.matter_shared_status_not_supported,
                                )
                                CommissioningFlowStep.Confirmation -> {
                                    if (deviceName?.isNotBlank() == true) {
                                        stringResource(
                                            commonR.string.matter_shared_status_confirmation_named,
                                            deviceName,
                                        )
                                    } else {
                                        stringResource(commonR.string.matter_shared_status_confirmation)
                                    }
                                }
                                CommissioningFlowStep.Success -> stringResource(
                                    commonR.string.matter_shared_status_success,
                                )
                                is CommissioningFlowStep.Failure -> {
                                    if (step.errorCode != null) {
                                        stringResource(commonR.string.matter_shared_status_failure_code, step.errorCode)
                                    } else {
                                        stringResource(commonR.string.matter_shared_status_failure)
                                    }
                                }
                                else -> "" // not used because everything above is not in loadingSteps
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (step == CommissioningFlowStep.SelectServer) {
                Spacer(modifier = Modifier.height(16.dp))
                servers.forEach {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onSelectServer(it.id) },
                    ) {
                        Text(it.friendlyName)
                    }
                    Divider()
                }
            }

            if (step !in loadingSteps) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, bottom = 16.dp),
                ) {
                    if (
                        step == CommissioningFlowStep.SelectServer ||
                        step == CommissioningFlowStep.Confirmation ||
                        step is CommissioningFlowStep.Failure
                    ) {
                        TextButton(onClick = { onClose() }) {
                            Text(stringResource(commonR.string.cancel))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    when (step) {
                        CommissioningFlowStep.NotRegistered,
                        CommissioningFlowStep.NotSupported,
                        -> {
                            Button(onClick = { onClose() }) {
                                Text(stringResource(commonR.string.close))
                            }
                        }
                        CommissioningFlowStep.Confirmation -> {
                            Button(onClick = { onConfirmCommissioning() }) {
                                Text(stringResource(commonR.string.add_device))
                            }
                        }
                        CommissioningFlowStep.Success -> {
                            Button(onClick = { onContinue() }) {
                                Text(stringResource(commonR.string.continue_connect))
                            }
                        }
                        is CommissioningFlowStep.Failure -> {
                            Button(onClick = { onConfirmCommissioning() }) {
                                Text(stringResource(commonR.string.retry))
                            }
                        }
                        else -> { /* No button */ }
                    }
                }
            }
        }
    }
}

@Composable
fun MatterCommissioningViewHeader() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(32.dp))
        Image(
            imageVector = ImageVector.vectorResource(R.drawable.ic_matter),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.CenterHorizontally),
        )
        Text(
            text = stringResource(commonR.string.matter_shared_title),
            style = MaterialTheme.typography.h5,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .align(Alignment.CenterHorizontally),
        )
    }
}

@Preview
@Composable
fun PreviewMatterCommissioningView(
    @PreviewParameter(MatterCommissioningViewPreviewStates::class) step: CommissioningFlowStep,
) {
    HomeAssistantAppTheme {
        MatterCommissioningView(
            step = step,
            deviceName = "Manufacturer Matter Light",
            servers = listOf(
                Server(
                    id = 0,
                    _name = "Home",
                    listOrder = -1,
                    connection = ServerConnectionInfo(externalUrl = ""),
                    session = ServerSessionInfo(),
                    user = ServerUserInfo(),
                ),
            ),
            onSelectServer = { },
            onConfirmCommissioning = { },
            onClose = { },
            onContinue = { },
        )
    }
}
