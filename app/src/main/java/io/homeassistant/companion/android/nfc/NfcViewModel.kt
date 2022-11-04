package io.homeassistant.companion.android.nfc

import android.app.Application
import android.nfc.NfcAdapter
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
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
    private val integrationUseCase: IntegrationRepository,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        const val TAG = "NfcViewModel"
    }

    var isNfcEnabled by mutableStateOf(false)
        private set
    var nfcTagIdentifier by mutableStateOf<String?>(null)
        private set
    var nfcIdentifierIsEditable by mutableStateOf(true)
        private set
    var nfcEventShouldWrite = false
        private set

    val navigator = Navigator()

    private val _nfcResultSnackbar = MutableSharedFlow<Int>()
    var nfcResultSnackbar = _nfcResultSnackbar.asSharedFlow()

    fun setDestination(destination: String?) {
        nfcEventShouldWrite = nfcTagIdentifier != null && destination == NfcSetupActivity.NAV_WRITE
    }

    fun checkNfcEnabled() {
        isNfcEnabled = NfcAdapter.getDefaultAdapter(getApplication()).isEnabled
    }

    fun setTagIdentifier(value: String) {
        if (nfcIdentifierIsEditable && value.trim().isNotEmpty()) nfcTagIdentifier = value
    }

    fun writeNewTagSimple(value: String) {
        nfcTagIdentifier = value
        nfcIdentifierIsEditable = false
        // We don't need to perform navigation here because it will be set as the startDestination
    }

    fun writeNewTag() {
        nfcTagIdentifier = UUID.randomUUID().toString()
        nfcIdentifierIsEditable = true
        navigator.navigateTo(NfcSetupActivity.NAV_WRITE)
    }

    fun onNfcReadSuccess(identifier: String) {
        nfcTagIdentifier = identifier

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
        nfcTagIdentifier = identifier

        navigator.navigateTo(
            Navigator.NavigatorItem(
                id = NfcSetupActivity.NAV_EDIT,
                popBackstackTo = NfcSetupActivity.NAV_WELCOME
            )
        )
    }

    suspend fun onNfcWriteFailure() = _nfcResultSnackbar.emit(commonR.string.nfc_write_tag_error)

    fun duplicateNfcTag() {
        nfcIdentifierIsEditable = false
        navigator.navigateTo(NfcSetupActivity.NAV_WRITE)
    }

    fun fireNfcTagEvent() {
        viewModelScope.launch {
            nfcTagIdentifier?.let {
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
