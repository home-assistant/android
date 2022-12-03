package io.homeassistant.companion.android.matter.views

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.homeassistant.companion.android.matter.MatterCommissioningViewModel

class MatterCommissioningViewPreviewStates :
    PreviewParameterProvider<MatterCommissioningViewModel.CommissioningFlowStep> {
    override val values = sequenceOf(
        MatterCommissioningViewModel.CommissioningFlowStep.NOT_REGISTERED,
        MatterCommissioningViewModel.CommissioningFlowStep.CHECKING_CORE,
        MatterCommissioningViewModel.CommissioningFlowStep.NOT_SUPPORTED,
        MatterCommissioningViewModel.CommissioningFlowStep.CONFIRMATION,
        MatterCommissioningViewModel.CommissioningFlowStep.WORKING,
        MatterCommissioningViewModel.CommissioningFlowStep.SUCCESS,
        MatterCommissioningViewModel.CommissioningFlowStep.FAILURE
    )
}
