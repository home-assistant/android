package io.homeassistant.companion.android.frontend.matterthread

import android.app.Activity
import android.content.IntentSender
import androidx.activity.result.ActivityResult
import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.frontend.dialog.FrontendDialogManager
import io.homeassistant.companion.android.matter.MatterManager
import io.homeassistant.companion.android.thread.ThreadManager
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import timber.log.Timber

/**
 * Coordinates the Matter commissioning and Thread credential export flows.
 * Sits between the external-bus handler events
 * ([io.homeassistant.companion.android.frontend.handler.FrontendHandlerEvent.StartMatterCommissioning],
 * [io.homeassistant.companion.android.frontend.handler.FrontendHandlerEvent.ImportThreadCredentials])
 * and the screen.
 *
 * **Stateful**: the handler owns mutable state in [inFlight] that spans the request →
 * Play-Services-intent → result round-trip. Method calls are not independent: callers must
 * invoke them in the natural sequence (start → result, or start → terminal). Calling
 * [onMatterThreadIntentResult] without a preceding start is a no-op (the handler logs and
 * ignores); calling a second start while a flow is in-flight is also a no-op. This is why the
 * scope is `@ViewModelScoped`.
 *
 * **User-facing dialogs** go through [FrontendDialogManager] just like all other dialogs on the
 * frontend screen — `showMatterThreadProgress()` for the "reading dataset" spinner and
 * `showMatterThreadTerminal(terminal)` for the post-flow alert.
 *
 * **One-shot screen events** flow through [events]:
 *  - [Event.LaunchIntent] when a Play Services `IntentSender` is ready to launch. The screen
 *    collects and forwards the resulting [ActivityResult] back through
 *    [onMatterThreadIntentResult].
 *  - [Event.ShowSnackbar] for transient feedback.
 *
 * The handler does not send any external-bus response messages.
 */
@ViewModelScoped
internal class FrontendMatterThreadHandler @Inject constructor(
    private val matterManager: MatterManager,
    private val threadManager: ThreadManager,
    private val dialogManager: FrontendDialogManager,
) {

    /**
     * What kind of flow is currently in-flight, if any. Non-null between request and the moment
     * the flow resolves (terminal shown, or [onMatterThreadIntentResult] processed). Kept set
     * across the Play Services intent round-trip so [onMatterThreadIntentResult] can dispatch
     * correctly. `compareAndSet(null, …)` enforces the single-flow invariant.
     */
    private val inFlight = AtomicReference<InFlight?>(null)

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    /**
     * Start the Matter commissioning flow. Drives [MatterManager.prepareMatterDeviceCommissioning], emits
     * [Event.LaunchIntent] when an `IntentSender` is ready, or shows the
     * [MatterThreadTerminal.Snackbar.MatterError] feedback if Play Services cannot prepare the flow.
     *
     * No-op if another flow is already in-flight.
     */
    suspend fun onStartMatterCommissioning() {
        if (!inFlight.compareAndSet(null, InFlight.Matter)) {
            Timber.w("matter/commission ignored: another flow is in-flight")
            return
        }
        var awaitingIntentResult = false
        try {
            when (val result = matterManager.prepareMatterDeviceCommissioning()) {
                is MatterManager.CommissioningResult.Ready -> {
                    awaitingIntentResult = true
                    _events.emit(Event.LaunchIntent(result.intentSender))
                }

                is MatterManager.CommissioningResult.Error -> {
                    Timber.e(result.cause, "Matter commissioning couldn't be prepared")
                    showTerminal(MatterThreadTerminal.Snackbar.MatterError)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error preparing Matter commissioning")
            showTerminal(MatterThreadTerminal.Snackbar.MatterError)
        } finally {
            // Keep inFlight set across the intent round-trip — onMatterThreadIntentResult clears
            // it after dispatching. Clear here only on the no-intent paths (error, cancellation).
            if (!awaitingIntentResult) inFlight.set(null)
        }
    }

    /**
     * Start the Thread credential export flow. Drives [ThreadManager.exportPreferredDataset],
     * shows the progress dialog while reading the dataset, then either emits [Event.LaunchIntent]
     * or shows a terminal dialog/snackbar.
     *
     * No-op if another flow is already in-flight.
     */
    suspend fun onImportThreadCredentials(serverId: Int) {
        if (!inFlight.compareAndSet(null, InFlight.Thread(serverId))) {
            Timber.w("thread/import_credentials ignored: another flow is in-flight")
            return
        }
        var awaitingIntentResult = false
        try {
            supervisorScope {
                // Show the progress dialog from a child coroutine so we can dismiss it
                // programmatically by cancelling the job when the flow advances.
                val progressJob = launch { dialogManager.showMatterThreadProgress() }
                try {
                    val result = try {
                        threadManager.exportPreferredDataset(serverId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Error while trying to export preferred dataset")
                        null
                    }
                    progressJob.cancel()
                    when (result) {
                        is ThreadManager.SyncResult.OnlyOnDevice -> {
                            val intent = result.exportIntent
                            if (intent != null) {
                                awaitingIntentResult = true
                                _events.emit(Event.LaunchIntent(intent))
                            } else {
                                showTerminal(MatterThreadTerminal.Dialog.ThreadNoDataset)
                            }
                        }

                        is ThreadManager.SyncResult.NoneHaveCredentials,
                        is ThreadManager.SyncResult.OnlyOnServer,
                        -> showTerminal(MatterThreadTerminal.Dialog.ThreadNoDataset)

                        is ThreadManager.SyncResult.NotConnected ->
                            showTerminal(MatterThreadTerminal.Dialog.ThreadNotConnected)

                        is ThreadManager.SyncResult.AppUnsupported,
                        is ThreadManager.SyncResult.ServerUnsupported,
                        null,
                        -> {
                            Timber.w("Thread export returned unsupported variant: $result")
                            showTerminal(MatterThreadTerminal.Snackbar.ThreadError)
                        }
                    }
                } finally {
                    progressJob.cancel()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error preparing Thread export")
            showTerminal(MatterThreadTerminal.Snackbar.ThreadError)
        } finally {
            if (!awaitingIntentResult) inFlight.set(null)
        }
    }

    /**
     * Process the result of the Play Services intent launched in response to [Event.LaunchIntent].
     * Dispatches based on what's [inFlight] — Matter just surfaces a "cancelled" snackbar on
     * non-OK, Thread forwards the dataset to the server and reports success / no-dataset.
     *
     * Silently ignores stale results when nothing is in-flight (shouldn't happen in practice
     * since only one launcher exists, but guards against bugs).
     */
    suspend fun onMatterThreadIntentResult(result: ActivityResult) {
        val current = inFlight.get() ?: run {
            Timber.w("Matter/Thread intent result received but nothing is in-flight; ignoring")
            return
        }
        try {
            when (current) {
                is InFlight.Matter -> handleMatterIntentResult(result)
                is InFlight.Thread -> handleThreadIntentResult(result, current.serverId)
            }
        } finally {
            inFlight.set(null)
        }
    }

    private suspend fun handleMatterIntentResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            Timber.d("Matter commissioning returned success")
        } else {
            Timber.d("Matter commissioning returned with non-OK code ${result.resultCode}")
            showTerminal(MatterThreadTerminal.Snackbar.MatterCancelled)
        }
    }

    private suspend fun handleThreadIntentResult(result: ActivityResult, serverId: Int) {
        val networkName = try {
            threadManager.sendThreadDatasetExportResult(result, serverId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to send Thread dataset export result")
            showTerminal(MatterThreadTerminal.Snackbar.ThreadError)
            return
        }
        if (!networkName.isNullOrBlank()) {
            Timber.d("Thread sent credential for $networkName")
            showTerminal(MatterThreadTerminal.Snackbar.ThreadSuccess)
        } else {
            Timber.d("Thread did not send credential")
            showTerminal(MatterThreadTerminal.Dialog.ThreadNoDataset)
        }
    }

    /**
     * Routes a terminal to either the dialog queue (informational acknowledgements that need an
     * explicit OK) or the snackbar event stream (transient feedback, possibly with a "Get help"
     * action). Suspends only for [MatterThreadTerminal.Dialog] — snackbars are fire-and-forget
     * so a follow-up flow can start immediately.
     */
    private suspend fun showTerminal(terminal: MatterThreadTerminal) {
        when (terminal) {
            is MatterThreadTerminal.Dialog -> dialogManager.showMatterThreadTerminal(terminal)
            is MatterThreadTerminal.Snackbar -> _events.emit(Event.ShowSnackbar(terminal))
        }
    }

    /** One-shot side effects the screen must consume. */
    sealed interface Event {
        /**
         * Launch this Play Services `IntentSender` and forward the [ActivityResult] back through
         * [onMatterThreadIntentResult]. The handler knows internally which flow the result
         * belongs to.
         */
        data class LaunchIntent(val intentSender: IntentSender) : Event

        /**
         * Show a snackbar with the variant's message. When [MatterThreadTerminal.Snackbar.helpUrl]
         * is non-null, surface a "Get help" action and open the URL externally if tapped.
         */
        data class ShowSnackbar(val snackbar: MatterThreadTerminal.Snackbar) : Event
    }

    /** Tracks which flow is awaiting completion. Carries the [Thread.serverId] needed by the result handler. */
    private sealed interface InFlight {
        data object Matter : InFlight
        data class Thread(val serverId: Int) : InFlight
    }
}
