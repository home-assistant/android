package io.homeassistant.companion.android.matter

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

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
