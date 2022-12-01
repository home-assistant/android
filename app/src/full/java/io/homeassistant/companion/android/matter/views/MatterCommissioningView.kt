package io.homeassistant.companion.android.matter.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.matter.MatterCommissioningViewModel.CommissioningFlowStep
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun MatterCommissioningView(
    step: CommissioningFlowStep,
    onConfirmCommissioning: () -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit
) {
    if (step != CommissioningFlowStep.NOT_STARTED) {
        // TODO more design
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(commonR.string.matter_shared_title),
                style = MaterialTheme.typography.h5,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .align(Alignment.CenterHorizontally)
            )
            ProvideTextStyle(MaterialTheme.typography.body1) {
                Box(modifier = Modifier.padding(vertical = 16.dp)) {
                    when (step) {
                        CommissioningFlowStep.NOT_REGISTERED -> {
                            Text(stringResource(commonR.string.matter_shared_status_not_registered))
                        }
                        CommissioningFlowStep.NOT_SUPPORTED -> {
                            Text(stringResource(commonR.string.matter_shared_status_not_supported))
                        }
                        CommissioningFlowStep.CONFIRMATION_REQUIRED -> {
                            Text(stringResource(commonR.string.matter_shared_status_confirmation_required))
                        }
                        CommissioningFlowStep.SUCCESS -> {
                            Text(stringResource(commonR.string.matter_shared_status_success))
                        }
                        CommissioningFlowStep.FAILURE -> {
                            Text(stringResource(commonR.string.matter_shared_status_failure))
                        }
                        else -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }

            if (step in listOf(
                    CommissioningFlowStep.NOT_REGISTERED,
                    CommissioningFlowStep.NOT_SUPPORTED,
                    CommissioningFlowStep.CONFIRMATION_REQUIRED,
                    CommissioningFlowStep.SUCCESS,
                    CommissioningFlowStep.FAILURE
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    if (step == CommissioningFlowStep.CONFIRMATION_REQUIRED) {
                        TextButton(onClick = { onClose() }) {
                            Text(stringResource(commonR.string.no))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (step == CommissioningFlowStep.FAILURE) {
                        TextButton(onClick = { onClose() }) {
                            Text(stringResource(commonR.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    when (step) {
                        CommissioningFlowStep.NOT_REGISTERED,
                        CommissioningFlowStep.NOT_SUPPORTED -> {
                            TextButton(onClick = { onClose() }) {
                                Text(stringResource(commonR.string.close))
                            }
                        }
                        CommissioningFlowStep.CONFIRMATION_REQUIRED -> {
                            Button(onClick = { onConfirmCommissioning() }) {
                                Text(stringResource(commonR.string.yes))
                            }
                        }
                        CommissioningFlowStep.FAILURE -> {
                            Button(onClick = { onConfirmCommissioning() }) {
                                Text(stringResource(commonR.string.retry))
                            }
                        }
                        CommissioningFlowStep.SUCCESS -> {
                            TextButton(onClick = { onContinue() }) {
                                Text(stringResource(commonR.string.continue_connect))
                            }
                        }
                        else -> { /* No button */ }
                    }
                }
            }
        }
    }
}
