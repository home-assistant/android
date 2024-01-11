package io.homeassistant.companion.android.matter

import android.app.Application
import android.content.IntentSender
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.thread.ThreadManager
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MatterCommissioningViewModel @Inject constructor(
    private val matterManager: MatterManager,
    private val threadManager: ThreadManager,
    private val serverManager: ServerManager,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MatterCommissioningView"
    }

    sealed class CommissioningFlowStep {
        object NotStarted : CommissioningFlowStep()
        object NotRegistered : CommissioningFlowStep()
        object SelectServer : CommissioningFlowStep()
        object CheckingCore : CommissioningFlowStep()
        object NotSupported : CommissioningFlowStep()
        object Confirmation : CommissioningFlowStep()
        object Working : CommissioningFlowStep()
        object Success : CommissioningFlowStep()
        class Failure(val errorCode: Int? = null) : CommissioningFlowStep()
    }

    var step by mutableStateOf<CommissioningFlowStep>(CommissioningFlowStep.NotStarted)
        private set

    var serverId by mutableStateOf(0)
        private set

    fun checkSetup(isNewDevice: Boolean) {
        viewModelScope.launch {
            if (!isNewDevice && step != CommissioningFlowStep.NotStarted) {
                return@launch
            } else {
                step = CommissioningFlowStep.NotStarted
            }

            if (!serverManager.isRegistered()) {
                step = CommissioningFlowStep.NotRegistered
                return@launch
            }

            if (serverManager.defaultServers.size > 1) {
                step = CommissioningFlowStep.SelectServer
            } else {
                serverManager.getServer()?.id?.let {
                    checkSupport(it)
                } ?: run {
                    step = CommissioningFlowStep.NotSupported
                }
            }
        }
    }

    fun checkSupport(id: Int) {
        viewModelScope.launch {
            val server = serverManager.getServer(id)
            if (server == null) {
                step = CommissioningFlowStep.NotSupported
                return@launch
            }

            serverId = id
            step = CommissioningFlowStep.CheckingCore

            val coreSupport = matterManager.coreSupportsCommissioning(id)
            step =
                if (coreSupport) {
                    CommissioningFlowStep.Confirmation
                } else {
                    CommissioningFlowStep.NotSupported
                }
        }
    }

    suspend fun syncThreadIfNecessary(): IntentSender? {
        step = CommissioningFlowStep.Working
        return try {
            val result = threadManager.syncPreferredDataset(
                getApplication<Application>().applicationContext,
                serverId,
                false,
                viewModelScope
            )
            when (result) {
                is ThreadManager.SyncResult.OnlyOnDevice -> result.exportIntent
                is ThreadManager.SyncResult.AllHaveCredentials -> result.exportIntent
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to sync preferred Thread dataset, continuing", e)
            null
        }
    }

    fun onThreadPermissionResult(result: ActivityResult, code: String) {
        viewModelScope.launch {
            threadManager.sendThreadDatasetExportResult(result, serverId)
            commissionDeviceWithCode(code)
        }
    }

    fun commissionDeviceWithCode(code: String) {
        viewModelScope.launch {
            step = CommissioningFlowStep.Working

            val result = matterManager.commissionDevice(code, serverId)
            step =
                if (result?.success == true) {
                    CommissioningFlowStep.Success
                } else {
                    CommissioningFlowStep.Failure(result?.errorCode)
                }
        }
    }
}
