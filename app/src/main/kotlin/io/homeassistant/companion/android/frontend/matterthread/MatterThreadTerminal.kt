package io.homeassistant.companion.android.frontend.matterthread

import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R as commonR

/**
 * Terminal outcome of a Matter commissioning or Thread credential export flow.
 *
 * Split into two sub-shapes:
 *  - [Dialog] — informational acknowledgement that requires the user to read it. Routed through
 *    [io.homeassistant.companion.android.frontend.dialog.FrontendDialogManager].
 *  - [Snackbar] — transient feedback or recoverable error with an
 *    optional "Get help" action. Emitted as a [FrontendMatterThreadHandler.Event.ShowSnackbar]
 *    for the screen's snackbar host to consume.
 */
sealed interface MatterThreadTerminal {
    @get:StringRes val messageRes: Int

    /** Informational AlertDialog with an OK button */
    sealed interface Dialog : MatterThreadTerminal {

        /** The device has no Thread dataset to share. */
        data object ThreadNoDataset : Dialog {
            override val messageRes: Int = commonR.string.thread_export_none
        }

        /** Device is not on the local network — Play Services cannot read the dataset. */
        data object ThreadNotConnected : Dialog {
            override val messageRes: Int = commonR.string.thread_export_not_connected
        }
    }

    /**
     * Transient snackbar feedback. When [helpUrl] is non-null the snackbar surfaces a "Get help"
     * action that opens the troubleshooting URL externally; otherwise it's a plain message.
     */
    sealed interface Snackbar : MatterThreadTerminal {
        val helpUrl: String?

        /** Thread credentials were successfully shared with the server. */
        data object ThreadSuccess : Snackbar {
            override val messageRes: Int = commonR.string.thread_export_success
            override val helpUrl: String? = null
        }

        /** User cancelled the Play Services Matter sheet. */
        data object MatterCancelled : Snackbar {
            override val messageRes: Int = commonR.string.matter_commissioning_cancelled
            override val helpUrl: String = MATTER_TROUBLESHOOTING_URL
        }

        /** Play Services could not prepare the Matter flow. */
        data object MatterError : Snackbar {
            override val messageRes: Int = commonR.string.matter_commissioning_unavailable
            override val helpUrl: String = MATTER_INSTALL_TROUBLESHOOTING_URL
        }

        /** Other Thread export error. */
        data object ThreadError : Snackbar {
            override val messageRes: Int = commonR.string.thread_export_unavailable
            override val helpUrl: String = MATTER_INSTALL_TROUBLESHOOTING_URL
        }
    }
}

private const val MATTER_TROUBLESHOOTING_URL = "https://www.home-assistant.io/integrations/matter#troubleshooting"
private const val MATTER_INSTALL_TROUBLESHOOTING_URL =
    "https://www.home-assistant.io/integrations/matter#troubleshooting-the-installation"
