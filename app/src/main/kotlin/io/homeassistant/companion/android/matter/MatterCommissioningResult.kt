package io.homeassistant.companion.android.matter

import android.content.IntentSender

/**
 * Terminal result of [MatterManager.commissionMatterDevice].
 *
 * Callers launch [Ready.intentSender] to continue the Google Play Services Matter commissioning
 * flow, or display an error message derived from [Error.cause].
 */
sealed interface MatterCommissioningResult {

    /**
     * Play Services produced an [IntentSender] for the commissioning flow. Callers must launch it
     * from an Activity (typically via an `ActivityResultLauncher`) to continue.
     */
    data class Ready(val intentSender: IntentSender) : MatterCommissioningResult

    /**
     * Play Services could not prepare the commissioning flow — common causes are an unsupported
     * device (SDK < O_MR1 or Automotive), Play Services unavailable, or a network failure
     * resolving the request. The caller should surface a user-facing error.
     */
    data class Error(val cause: Throwable) : MatterCommissioningResult
}
