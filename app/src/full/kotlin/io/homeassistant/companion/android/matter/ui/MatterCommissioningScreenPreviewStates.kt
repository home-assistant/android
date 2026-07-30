package io.homeassistant.companion.android.matter.ui

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.homeassistant.companion.android.matter.MatterCommissioningViewModel

class MatterCommissioningScreenPreviewStates :
    PreviewParameterProvider<MatterCommissioningViewModel.CommissioningFlowStep> {
    override val values = sequenceOf(
        MatterCommissioningViewModel.CommissioningFlowStep.NotRegistered,
        MatterCommissioningViewModel.CommissioningFlowStep.CheckingCore,
        MatterCommissioningViewModel.CommissioningFlowStep.SelectServer,
        MatterCommissioningViewModel.CommissioningFlowStep.NotSupported,
        MatterCommissioningViewModel.CommissioningFlowStep.Confirmation,
        MatterCommissioningViewModel.CommissioningFlowStep.Working,
        MatterCommissioningViewModel.CommissioningFlowStep.Success,
        MatterCommissioningViewModel.CommissioningFlowStep.Failure(errorCode = 99),
    )
}
