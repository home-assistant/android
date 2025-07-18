package io.homeassistant.companion.android.nfc

import android.app.Application
import android.nfc.NfcAdapter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.util.Navigator
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class NfcViewModel @Inject constructor(private val serverManager: ServerManager, application: Application) :
    AndroidViewModel(application) {

    var isNfcEnabled by mutableStateOf(false)
        private set
    var usesAndroidDeviceId by mutableStateOf(false)
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

    init {
        viewModelScope.launch {
            usesAndroidDeviceId = serverManager.getServer()?.version?.isAtLeast(2022, 12, 0) == false
        }
    }

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
                popBackstackTo = NfcSetupActivity.NAV_WELCOME,
            ),
        )
    }

    suspend fun onNfcReadEmpty() = _nfcResultSnackbar.emit(commonR.string.nfc_invalid_tag)

    suspend fun onNfcWriteSuccess(identifier: String) {
        _nfcResultSnackbar.emit(commonR.string.nfc_write_tag_success)
        nfcTagIdentifier = identifier

        navigator.navigateTo(
            Navigator.NavigatorItem(
                id = NfcSetupActivity.NAV_EDIT,
                popBackstackTo = NfcSetupActivity.NAV_WELCOME,
            ),
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
                val results = serverManager.defaultServers.map { server ->
                    async {
                        try {
                            serverManager.integrationRepository(server.id).scanTag(hashMapOf("tag_id" to it))
                            true
                        } catch (e: Exception) {
                            Timber.e(e, "Unable to send tag to Home Assistant.")
                            false
                        }
                    }
                }
                if (results.awaitAll().any { it }) {
                    _nfcResultSnackbar.emit(commonR.string.nfc_event_fired_success)
                } else {
                    _nfcResultSnackbar.emit(commonR.string.nfc_event_fired_fail)
                }
            } ?: _nfcResultSnackbar.emit(commonR.string.nfc_event_fired_fail)
        }
    }
}
