package io.homeassistant.companion.android.barcode

data class BarcodeScannerAction(val type: BarcodeActionType, val message: String? = null)

enum class BarcodeActionType(val externalBusType: String) {
    NOTIFY("bar_code/notify"),
    CLOSE("bar_code/close"),
    ;

    companion object {
        fun fromExternalBus(type: String) = entries.firstOrNull { it.externalBusType == type }
    }
}
