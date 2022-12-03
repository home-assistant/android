package io.homeassistant.companion.android.matter

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MatterCommissioningViewModel @Inject constructor(
    private val matterManager: MatterManager,
    private val integrationRepository: IntegrationRepository,
    application: Application
) : AndroidViewModel(application) {

    enum class CommissioningFlowStep {
        NOT_STARTED,
        NOT_REGISTERED,
        CHECKING_CORE,
        NOT_SUPPORTED,
        CONFIRMATION,
        WORKING,
        SUCCESS,
        FAILURE
    }

    var step by mutableStateOf(CommissioningFlowStep.NOT_STARTED)
        private set

    fun checkSupport() {
        viewModelScope.launch {
            if (step != CommissioningFlowStep.NOT_STARTED) return@launch

            if (!integrationRepository.isRegistered()) {
                step = CommissioningFlowStep.NOT_REGISTERED
                return@launch
            }

            step = CommissioningFlowStep.CHECKING_CORE

            val coreSupport = matterManager.coreSupportsCommissioning()
            step =
                if (coreSupport) CommissioningFlowStep.CONFIRMATION
                else CommissioningFlowStep.NOT_SUPPORTED
        }
    }

    fun commissionDeviceWithCode(code: String) {
        viewModelScope.launch {
            step = CommissioningFlowStep.WORKING

            val result = matterManager.commissionDevice(code)
            step =
                if (result) CommissioningFlowStep.SUCCESS
                else CommissioningFlowStep.FAILURE
        }
    }
}
