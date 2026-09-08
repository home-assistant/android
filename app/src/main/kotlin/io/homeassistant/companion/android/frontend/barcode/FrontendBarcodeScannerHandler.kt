package io.homeassistant.companion.android.frontend.barcode

import com.google.zxing.BarcodeFormat
import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.frontend.dialog.FrontendDialogManager
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.externalbus.outgoing.BarcodeScanAbortedMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.BarcodeScanResultMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the barcode scanner overlay state for the frontend screen and bridges it to the external bus.
 *
 * User outcomes ([onScanned]/[onCancelled]) reply to the frontend through [externalBusRepository]
 * with the same `id` the scan was requested with.
 *
 * Notify is surfaced through [dialogManager], so it shares
 * the single-dialog slot with the rest of the frontend.
 *
 * The scanner UI ([io.homeassistant.companion.android.frontend.barcode.ui.BarcodeScanner]) owns the
 * camera and its permission; this handler carries no camera or permission logic. Allowing the UI
 * to manage it itself.
 */
@ViewModelScoped
internal class FrontendBarcodeScannerHandler @Inject constructor(
    private val externalBusRepository: FrontendExternalBusRepository,
    private val dialogManager: FrontendDialogManager,
) {
    private val _state = MutableStateFlow<BarcodeScannerUiState?>(null)
    val state: StateFlow<BarcodeScannerUiState?> = _state.asStateFlow()

    /** Show the scanner, replacing any scan already in progress. */
    fun show(messageId: Int, title: String, description: String, alternativeOptionLabel: String?) {
        _state.value = BarcodeScannerUiState(
            messageId = messageId,
            title = title,
            description = description,
            alternativeOptionLabel = alternativeOptionLabel,
        )
    }

    /**
     * Surface an informational dialog over the scanner.
     * Suspends until the user dismisses it. Ignored when no scan is active (the frontend only sends
     * this during a scan); the frontend expects no reply.
     */
    suspend fun notify(message: String) {
        if (_state.value == null) return
        dialogManager.showInformation(message)
    }

    /** Dismiss the scanner. Fire-and-forget: no reply is sent to the frontend. */
    fun close() {
        _state.value = null
    }

    /**
     * A code was scanned: reply with `bar_code/scan_result` (mapping [format] to the frontend's wire
     * string). No-op if no scan is active.
     *
     * The scanner stays open after reporting a result: the frontend drives what happens next,
     * either dismissing it with `bar_code/close` ([close]) or surfacing a `bar_code/notify`
     * dialog ([notify]) and letting scanning continue. Duplicate scans are debounced in the scanner UI.
     */
    suspend fun onScanned(rawValue: String, format: BarcodeFormat) {
        val currentState = _state.value ?: return
        externalBusRepository.send(
            BarcodeScanResultMessage(
                id = currentState.messageId,
                rawValue = rawValue,
                format = format.toFrontendWireFormat(),
            ),
        )
    }

    /**
     * The user cancelled: reply with `bar_code/aborted` (`alternative_options` when [forAction] is
     * true, otherwise `canceled`) and dismiss the scanner. No-op if no scan is active.
     */
    suspend fun onCancelled(forAction: Boolean) {
        val currentState = _state.value ?: return
        externalBusRepository.send(BarcodeScanAbortedMessage(id = currentState.messageId, forAction = forAction))
        _state.value = null
    }
}

/**
 * Maps a zxing [BarcodeFormat] to the string the Home Assistant frontend expects. So the frontend
 */
private fun BarcodeFormat.toFrontendWireFormat(): String = when (this) {
    BarcodeFormat.PDF_417 -> "pdf417"
    BarcodeFormat.MAXICODE,
    BarcodeFormat.RSS_14,
    BarcodeFormat.RSS_EXPANDED,
    BarcodeFormat.UPC_EAN_EXTENSION,
    -> "unknown"

    BarcodeFormat.AZTEC -> "aztec"
    BarcodeFormat.CODABAR -> "codabar"
    BarcodeFormat.CODE_39 -> "code_39"
    BarcodeFormat.CODE_93 -> "code_93"
    BarcodeFormat.CODE_128 -> "code_128"
    BarcodeFormat.DATA_MATRIX -> "data_matrix"
    BarcodeFormat.EAN_8 -> "ean_8"
    BarcodeFormat.EAN_13 -> "ean_13"
    BarcodeFormat.ITF -> "itf"
    BarcodeFormat.QR_CODE -> "qr_code"
    BarcodeFormat.UPC_A -> "upc_a"
    BarcodeFormat.UPC_E -> "upc_e"
}
