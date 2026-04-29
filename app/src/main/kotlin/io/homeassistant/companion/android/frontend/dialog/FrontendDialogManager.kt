package io.homeassistant.companion.android.frontend.dialog

import android.content.Context
import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.util.SingleSlotQueue
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.StateFlow

/**
 * The user's response to a [FrontendDialog.HttpAuth].
 */
internal sealed interface HttpAuthOutcome {
    data class Proceed(val username: String, val password: String, val remember: Boolean) : HttpAuthOutcome
    data object Cancel : HttpAuthOutcome
}

/**
 * Owns the lifetime of [FrontendDialog]s shown over the frontend WebView.
 *
 * Only one dialog can be on screen at a time. A second `show*` call suspends until the user has
 * responded to the first, so callers can trigger a dialog without first checking whether another
 * one is already on screen.
 *
 * Each `show*` function constructs the appropriate [FrontendDialog], emits it to the queue,
 * suspends until the user responds, frees the slot, and returns the outcome. Callers therefore
 * never see [FrontendDialog] internals.
 */
@ViewModelScoped
internal class FrontendDialogManager @Inject constructor() {

    private val queue = SingleSlotQueue<FrontendDialog>()

    /** The current pending dialog, or `null` if none. */
    val pendingDialog: StateFlow<FrontendDialog?> = queue

    /**
     * Shows a confirmation dialog and suspends until the user responds.
     *
     * Returns `true` if the user confirmed, `false` if they cancelled. The slot is freed
     * before returning, including on cancellation of the calling coroutine.
     */
    suspend fun showJsConfirm(message: String): Boolean = showAndAwait { outcome ->
        FrontendDialog.Confirm(
            message = message,
            onConfirm = { outcome.complete(true) },
            onCancel = { outcome.complete(false) },
        )
    }

    /**
     * Shows an HTTP Basic Auth dialog and suspends until the user responds.
     *
     * Pass `isAuthError = true` to surface a "credentials were rejected".
     *
     * The slot is freed before returning, including on cancellation of the calling coroutine.
     */
    suspend fun showHttpAuth(host: String, message: (Context) -> String, isAuthError: Boolean): HttpAuthOutcome =
        showAndAwait { outcome ->
            FrontendDialog.HttpAuth(
                host = host,
                message = message,
                isAuthError = isAuthError,
                onProceed = { username, password, remember ->
                    outcome.complete(
                        HttpAuthOutcome.Proceed(username = username, password = password, remember = remember),
                    )
                },
                onCancel = { outcome.complete(HttpAuthOutcome.Cancel) },
            )
        }

    /**
     * Builds a dialog via [buildDialog], emits it, suspends until the user responds, then frees the slot.
     *
     * [buildDialog] receives a [CompletableDeferred] that the dialog's callbacks complete with the
     * user's response. The slot is cleared before returning, including on cancellation of the calling
     * coroutine, so a cancelled show never leaves the queue stuck.
     */
    private suspend fun <T> showAndAwait(buildDialog: (CompletableDeferred<T>) -> FrontendDialog): T {
        val outcome = CompletableDeferred<T>()
        queue.emit(buildDialog(outcome))
        return try {
            outcome.await()
        } finally {
            queue.clear()
        }
    }
}
