package io.homeassistant.companion.android.nfc

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.util.Navigator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@HiltViewModel
class NfcViewModel @Inject constructor(
    private val integrationUseCase: IntegrationRepository
) : ViewModel() {

    companion object {
        const val TAG = "NfcViewModel"
    }

    val nfcTagIdentifier = mutableStateOf<String?>(null)
    var nfcEventShouldWrite = false
        private set

    val navigator = Navigator()

    private val _nfcResultSnackbar = MutableSharedFlow<Int>()
    val nfcResultSnackbar = _nfcResultSnackbar.asSharedFlow()

    fun setDestination(destination: String?) {
        nfcEventShouldWrite = nfcTagIdentifier.value != null && destination == NfcSetupActivity.NAV_WRITE
    }

    fun setTagIdentifierForSimple(value: String) {
        nfcTagIdentifier.value = value
        // We don't need to perform navigation here because it will be set as the startDestination
    }

    fun writeNewTag() {
        nfcTagIdentifier.value = UUID.randomUUID().toString()
        navigator.navigateTo(NfcSetupActivity.NAV_WRITE)
    }

    fun onNfcReadSuccess(identifier: String) {
        nfcTagIdentifier.value = identifier

        navigator.navigateTo(
            Navigator.NavigatorItem(
                id = NfcSetupActivity.NAV_EDIT,
                popBackstackTo = NfcSetupActivity.NAV_WELCOME
            )
        )
    }

    suspend fun onNfcReadEmpty() = _nfcResultSnackbar.emit(commonR.string.nfc_invalid_tag)

    suspend fun onNfcWriteSuccess(identifier: String) {
        _nfcResultSnackbar.emit(commonR.string.nfc_write_tag_success)
        nfcTagIdentifier.value = identifier

        navigator.navigateTo(
            Navigator.NavigatorItem(
                id = NfcSetupActivity.NAV_EDIT,
                popBackstackTo = NfcSetupActivity.NAV_WELCOME
            )
        )
    }

    suspend fun onNfcWriteFailure() = _nfcResultSnackbar.emit(commonR.string.nfc_write_tag_error)

    fun duplicateNfcTag() = navigator.navigateTo(NfcSetupActivity.NAV_WRITE)

    fun fireNfcTagEvent() {
        viewModelScope.launch {
            nfcTagIdentifier.value?.let {
                try {
                    integrationUseCase.scanTag(
                        hashMapOf("tag_id" to it)
                    )
                    _nfcResultSnackbar.emit(commonR.string.nfc_event_fired_success)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to send tag to Home Assistant.", e)
                    _nfcResultSnackbar.emit(commonR.string.nfc_event_fired_fail)
                }
            } ?: _nfcResultSnackbar.emit(commonR.string.nfc_event_fired_fail)
        }
    }
}
