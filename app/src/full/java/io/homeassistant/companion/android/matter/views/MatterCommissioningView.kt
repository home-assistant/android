package io.homeassistant.companion.android.matter.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.google.android.material.composethemeadapter.MdcTheme
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.matter.MatterCommissioningViewModel.CommissioningFlowStep
import kotlin.math.min
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun MatterCommissioningView(
    step: CommissioningFlowStep,
    onConfirmCommissioning: () -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit
) {
    if (step == CommissioningFlowStep.NOT_STARTED) return

    val screenWidth = LocalConfiguration.current.screenWidthDp
    val notLoadingSteps = listOf(
        CommissioningFlowStep.NOT_REGISTERED,
        CommissioningFlowStep.NOT_SUPPORTED,
        CommissioningFlowStep.CONFIRMATION,
        CommissioningFlowStep.SUCCESS,
        CommissioningFlowStep.FAILURE
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .width(min(screenWidth, 600).dp)
                .align(Alignment.Center)
        ) {
            MatterCommissioningViewHeader()

            ProvideTextStyle(MaterialTheme.typography.body1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                ) {
                    if (step !in notLoadingSteps) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            if (step == CommissioningFlowStep.WORKING) {
                                Text(
                                    text = stringResource(commonR.string.matter_shared_status_working),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth(0.67f)
                                        .padding(top = 16.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(
                                when (step) {
                                    CommissioningFlowStep.NOT_REGISTERED -> commonR.string.matter_shared_status_not_registered
                                    CommissioningFlowStep.NOT_SUPPORTED -> commonR.string.matter_shared_status_not_supported
                                    CommissioningFlowStep.CONFIRMATION -> commonR.string.matter_shared_status_confirmation
                                    CommissioningFlowStep.SUCCESS -> commonR.string.matter_shared_status_success
                                    CommissioningFlowStep.FAILURE -> commonR.string.matter_shared_status_failure
                                    else -> 0 // not used because everything above is in notLoadingSteps
                                }
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (step in notLoadingSteps) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, bottom = 16.dp)
                ) {
                    if (step == CommissioningFlowStep.CONFIRMATION || step == CommissioningFlowStep.FAILURE) {
                        TextButton(onClick = { onClose() }) {
                            Text(stringResource(commonR.string.cancel))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    when (step) {
                        CommissioningFlowStep.NOT_REGISTERED,
                        CommissioningFlowStep.NOT_SUPPORTED -> {
                            Button(onClick = { onClose() }) {
                                Text(stringResource(commonR.string.close))
                            }
                        }
                        CommissioningFlowStep.CONFIRMATION -> {
                            Button(onClick = { onConfirmCommissioning() }) {
                                Text(stringResource(commonR.string.add_device))
                            }
                        }
                        CommissioningFlowStep.SUCCESS -> {
                            Button(onClick = { onContinue() }) {
                                Text(stringResource(commonR.string.continue_connect))
                            }
                        }
                        CommissioningFlowStep.FAILURE -> {
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
            colorFilter = ColorFilter.tint(colorResource(commonR.color.colorAccent)),
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.CenterHorizontally)
        )
        Text(
            text = stringResource(commonR.string.matter_shared_title),
            style = MaterialTheme.typography.h5,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .align(Alignment.CenterHorizontally)
        )
    }
}

@Preview
@Composable
fun PreviewMatterCommissioningView(
    @PreviewParameter(MatterCommissioningViewPreviewStates::class) step: CommissioningFlowStep
) {
    MdcTheme {
        MatterCommissioningView(
            step = step,
            onConfirmCommissioning = { },
            onClose = { },
            onContinue = { }
        )
    }
}
