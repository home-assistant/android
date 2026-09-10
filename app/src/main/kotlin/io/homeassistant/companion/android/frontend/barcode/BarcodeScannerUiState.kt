package io.homeassistant.companion.android.frontend.barcode

/**
 * UI state for the barcode scanner overlay.
 *
 * @param messageId Correlation id from the `bar_code/scan` message; echoed on the result/aborted reply
 * @param title Bold header shown above the cutout
 * @param description Subtitle shown below the title
 * @param alternativeOptionLabel Optional "enter manually" button label, or null for none
 */
data class BarcodeScannerUiState(
    val messageId: Int,
    val title: String,
    val description: String,
    val alternativeOptionLabel: String?,
)
