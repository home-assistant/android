package io.homeassistant.companion.android.matter

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MatterCommissioningViewModel @Inject constructor(
    private val matterManager: MatterManager,
    private val serverManager: ServerManager,
    application: Application
) : AndroidViewModel(application) {

    sealed class CommissioningFlowStep {
        object NotStarted : CommissioningFlowStep()
        object NotRegistered : CommissioningFlowStep()
        object CheckingCore : CommissioningFlowStep()
        object NotSupported : CommissioningFlowStep()
        object Confirmation : CommissioningFlowStep()
        object Working : CommissioningFlowStep()
        object Success : CommissioningFlowStep()
        class Failure(val errorCode: Int? = null) : CommissioningFlowStep()
    }

    var step by mutableStateOf<CommissioningFlowStep>(CommissioningFlowStep.NotStarted)
        private set

    fun checkSupport() {
        viewModelScope.launch {
            if (step != CommissioningFlowStep.NotStarted) return@launch

            if (!serverManager.isRegistered()) {
                step = CommissioningFlowStep.NotRegistered
                return@launch
            }

            step = CommissioningFlowStep.CheckingCore

            val coreSupport = matterManager.coreSupportsCommissioning()
            step =
                if (coreSupport) CommissioningFlowStep.Confirmation
                else CommissioningFlowStep.NotSupported
        }
    }

    fun commissionDeviceWithCode(code: String) {
        viewModelScope.launch {
            step = CommissioningFlowStep.Working

            val result = matterManager.commissionDevice(code)
            step =
                if (result?.success == true) CommissioningFlowStep.Success
                else CommissioningFlowStep.Failure(result?.errorCode)
        }
    }
}
