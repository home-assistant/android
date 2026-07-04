package io.homeassistant.companion.android.barcode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.util.getStringOrElse
import io.homeassistant.companion.android.webview.externalbus.ExternalBusMessage
import io.homeassistant.companion.android.webview.externalbus.ExternalBusRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import timber.log.Timber

@HiltViewModel
class BarcodeScannerViewModel @Inject constructor(
    private val externalBusRepository: ExternalBusRepository,
    val app: Application,
) : AndroidViewModel(app) {

    private val frontendActionsFlow = MutableSharedFlow<BarcodeScannerAction>()
    val actionsFlow = frontendActionsFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            externalBusRepository.receive(
                listOf(BarcodeActionType.NOTIFY.externalBusType, BarcodeActionType.CLOSE.externalBusType),
            ).collect { message ->
                when (val type = BarcodeActionType.fromExternalBus(message.getStringOrElse("type", ""))) {
                    BarcodeActionType.NOTIFY -> frontendActionsFlow.emit(
                        BarcodeScannerAction(type, message["payload"]?.jsonObject?.getStringOrElse("message", "")),
                    )
                    BarcodeActionType.CLOSE -> frontendActionsFlow.emit(
                        BarcodeScannerAction(type),
                    )
                    else -> Timber.w("Received unexpected external bus message of type ${type?.name}")
                }
            }
        }
    }

    fun sendScannerResult(messageId: Int, text: String, format: String) {
        viewModelScope.launch {
            externalBusRepository.send(
                ExternalBusMessage(
                    id = messageId,
                    type = "command",
                    command = "bar_code/scan_result",
                    payload = mapOf(
                        "rawValue" to text,
                        "format" to format,
                    ),
                ),
            )
        }
    }

    fun sendScannerClosing(messageId: Int, forAction: Boolean) {
        viewModelScope.launch {
            externalBusRepository.send(
                ExternalBusMessage(
                    id = messageId,
                    type = "command",
                    command = "bar_code/aborted",
                    payload = mapOf(
                        "reason" to (if (forAction) "alternative_options" else "canceled"),
                    ),
                ),
            )
        }
    }
}
