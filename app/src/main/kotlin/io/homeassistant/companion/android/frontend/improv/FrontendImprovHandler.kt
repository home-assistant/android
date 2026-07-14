package io.homeassistant.companion.android.frontend.improv

import com.wifi.improv.ImprovDevice
import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.data.network.WifiHelper
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ImprovDeviceSetupDoneMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ImprovDiscoveredDeviceMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.NavigateToMessage
import io.homeassistant.companion.android.frontend.permissions.PermissionManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Owns the runtime orchestration of the Improv Wi-Fi onboarding flow on top of [ImprovRepository],
 * exposing a single [uiState] that drives the UI and reacting to the outcome:
 *
 * - [scanRequested] is flipped on by `improv/scan` and off on dismiss. While true, the UI layer
 *   collects [processImprovScanRequests] inside a lifecycle-bound effect — that collect forwards
 *   each newly-seen device name to the frontend as [ImprovDiscoveredDeviceMessage] and resolves
 *   the BLE address for the [ImprovUIState.ConfiguringDevice] target as it appears.
 * - A provisioning job is launched on [onConnectDevice] and transitions [uiState] through
 *   [ImprovUIState.Provisioning] → ([ImprovUIState.Provisioned] | [ImprovUIState.Errored]) as the
 *   [ProvisioningEvent]s arrive, sending [ImprovDeviceSetupDoneMessage] on success and capturing
 *   the integration `domain` on the terminal [ImprovUIState.Provisioned] for the dismiss-time
 *   navigation.
 * - On dismiss, if a domain was captured, sends [NavigateToMessage] when the HA server is on
 *   2025.6+, otherwise emits [Event.ReloadAtPath] for the VM to translate into a server-URL
 *   reload.
 *
 * **Concurrency contract.** Entry points are safe to call from any coroutine. Var-backed internal
 * state ([provisioningJob], [discoveredDevices], [sentDeviceNames]) is guarded by [mutex];
 * StateFlow-backed state ([scanRequested], [uiState]) is atomic by construction and not
 * lock-guarded. The handler does not own a long-lived scope: the caller passes its scope to
 * [onConnectDevice] for provisioning, and the UI layer hosts the scan collect through
 * [processImprovScanRequests] so navigation away from the frontend route naturally pauses BLE work.
 */
@ViewModelScoped
internal class FrontendImprovHandler @Inject constructor(
    private val improvRepository: ImprovRepository,
    private val bluetoothCapabilities: BluetoothCapabilities,
    private val externalBusRepository: FrontendExternalBusRepository,
    private val permissionManager: PermissionManager,
    private val serverManager: ServerManager,
    private val wifiHelper: WifiHelper,
) {

    private val mutex = Mutex()
    private var provisioningJob: Job? = null

    private val _scanRequested = MutableStateFlow(false)

    /**
     * Whether there is an active session that wants to keep scanning for Improv Wi-Fi devices. Flipped
     * on by [onStartImprovScan] (after permissions) and off by [onDismissed].
     */
    val scanRequested: StateFlow<Boolean> = _scanRequested.asStateFlow()

    /**
     * Mirror of the latest scan emission, written by [forwardDiscoveredDevices]. Read by
     * [onConfigureImprovDevice] so a device already surfaced by the scan can land directly in
     * [ImprovUIState.ConfiguringDevice] — without this snapshot, that device would have to wait
     * for the next scan emission to surface.
     *
     * Reads and writes go through [mutex].
     */
    private var discoveredDevices: List<ImprovDevice> = emptyList()

    /**
     * Device names already forwarded to the frontend during the current session (from
     * [onStartImprovScan] until [onDismissed]). Survives `processImprovScanRequests` restarts
     * caused by the UI's lifecycle-bound collect tearing down on PAUSE and re-running on RESUME —
     * the scan flow replays its current device list to the new collector, and re-sending those
     * names would surface duplicates in the frontend's device list. Cleared on [onDismissed].
     *
     * Reads and writes go through [mutex].
     */
    private val sentDeviceNames: MutableSet<String> = mutableSetOf()

    private val _uiState = MutableStateFlow<ImprovUIState?>(null)

    /**
     * Current Improv sheet state. `null` while no flow is active; non-null while the UI should be on screen.
     */
    val uiState: StateFlow<ImprovUIState?> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)

    /** Side-effect events to consume. */
    val events: SharedFlow<Event> = _events.asSharedFlow()

    /**
     * Handles `improv/scan` from the frontend.
     *
     * Drops on devices without Bluetooth LE. Drives the rationale + system-permission flow, then
     * flips [scanRequested] so the UI layer's lifecycle-bound effect picks up and starts
     * [processImprovScanRequests].
     */
    suspend fun onStartImprovScan() {
        if (!bluetoothCapabilities.hasBluetoothLe()) {
            Timber.d("Improv scan ignored: device has no Bluetooth LE")
            return
        }
        if (!ensurePermissions()) return

        _scanRequested.value = true
    }

    /**
     * Runs the discovered-device forwarder while [scanRequested] is true — meant to be invoked by
     * the UI layer from a lifecycle-bound effect so leaving the frontend route naturally tears
     * down the BLE scan.
     *
     * Suspends indefinitely (until the caller's coroutine is cancelled): observes [scanRequested]
     * and, while it's true, runs the forwarder; when it flips back to false the forwarder is
     * cancelled but this call keeps observing so a re-flip to true (same composition,
     * back-to-back sessions) restarts forwarding. Permissions are re-checked each time the flag
     * becomes true because the user may have revoked them while we were paused.
     */
    suspend fun processImprovScanRequests() {
        _scanRequested.collectLatest { requested ->
            if (!requested) return@collectLatest
            if (!improvRepository.hasPermissions()) return@collectLatest
            forwardDiscoveredDevices()
        }
    }

    /**
     * Handles `improv/configure_device(name)` from the frontend.
     *
     * Transitions [uiState] to [ImprovUIState.SearchingDevice]; then snapshots
     * [discoveredDevices] and, if the target is already known, follows up with an immediate
     * [ImprovUIState.ConfiguringDevice] update. Without that snapshot check, a device discovered
     * *before* `configure_device` arrived would have to wait for the next scan emission to be
     * promoted. Devices discovered *after* `configure_device` are handled by
     * [forwardDiscoveredDevices]' promotion logic.
     */
    suspend fun onConfigureImprovDevice(deviceName: String) {
        mutex.withLock {
            // Cancel any prior provisioning attempt from a previous session.
            provisioningJob?.cancel()
            provisioningJob = null
            // Order matters: set SearchingDevice BEFORE snapshotting discoveredDevices. If a scan
            // emission lands between the two lines, forwardDiscoveredDevices' promotion path must
            // already see SearchingDevice in place — reversing the order would let that emission
            // slip through both the snapshot match below and the promotion guard above.
            _uiState.value = ImprovUIState.SearchingDevice(deviceName = deviceName)
            val matched = discoveredDevices.firstOrNull { it.name == deviceName }?.address
            if (matched != null) {
                _uiState.value = configuringDeviceFor(deviceName = deviceName, deviceAddress = matched)
            }
        }
    }

    /**
     * Forwards user-entered Wi-Fi credentials to the device on the current
     * [ImprovUIState.ConfiguringDevice]. Launches a provisioning job that drives the
     * connect → authorize → submit-Wi-Fi → provisioned sequence, transitioning [uiState] through
     * [ImprovUIState.Provisioning] → ([ImprovUIState.Provisioned] |
     * [ImprovUIState.Errored]) as the [ProvisioningEvent]s arrive. Logs a warning and no-ops
     * if the current state isn't [ImprovUIState.ConfiguringDevice] — the view never exposes
     * the Wi-Fi form before then, so hitting this path indicates a misrouted call. The job is
     * tracked in [provisioningJob] so [onRestart] / [onDismissed] can cancel an in-flight session.
     */
    suspend fun onConnectDevice(scope: CoroutineScope, ssid: String, password: String) {
        mutex.withLock {
            val current = _uiState.value
            if (current !is ImprovUIState.ConfiguringDevice) {
                Timber.w(
                    "onConnectDevice ignored: expected ConfiguringDevice, got ${current?.let {
                        it::class.simpleName
                    } ?: "null"}",
                )
                return@withLock
            }
            provisioningJob?.cancel()
            provisioningJob = scope.launch {
                runProvisioning(
                    deviceName = current.deviceName,
                    deviceAddress = current.deviceAddress,
                    ssid = ssid,
                    password = password,
                )
            }
        }
    }

    /**
     * Resets per-device provisioning state, to be used with the "Try again" button after an
     * error. Reads the target device from the current [ImprovUIState.WithResolvedDevice] and
     * reverts to [ImprovUIState.ConfiguringDevice] so the user can re-submit Wi-Fi
     * credentials. No-op when searching ([ImprovUIState.SearchingDevice]), terminal
     * ([ImprovUIState.Provisioned]) or absent — the "Try again" affordance only ever exists in
     * [ImprovUIState.Errored].
     */
    suspend fun onRestart() {
        mutex.withLock {
            provisioningJob?.cancel()
            provisioningJob = null
            val current = _uiState.value as? ImprovUIState.WithResolvedDevice ?: return@withLock
            _uiState.value = configuringDeviceFor(
                deviceName = current.deviceName,
                deviceAddress = current.deviceAddress,
            )
        }
    }

    /**
     * UI dismissed. If the device reported a `domain` during provisioning, redirects the frontend
     * to start a config flow for the domain. Flips [scanRequested] off so the UI's lifecycle-bound
     * effect can cancel its [processImprovScanRequests] collect, which in turn ends the BLE scan.
     * A follow-up flow needs another `improv/scan` from the frontend.
     */
    suspend fun onDismissed(serverId: Int) {
        val domain: String?
        mutex.withLock {
            // Read the domain off the terminal state — only [ImprovUIState.Provisioned] carries
            // one; every other variant means we landed here without finishing.
            domain = (_uiState.value as? ImprovUIState.Provisioned)?.domain
            provisioningJob?.cancel()
            provisioningJob = null
            _scanRequested.value = false
            discoveredDevices = emptyList()
            sentDeviceNames.clear()
            _uiState.value = null
        }
        if (domain != null) {
            navigateToConfigFlow(domain = domain, serverId = serverId)
        }
    }

    /**
     * Builds a [ImprovUIState.ConfiguringDevice] for the given device, prefilled with the
     * currently connected Wi-Fi SSID.
     */
    private fun configuringDeviceFor(deviceName: String, deviceAddress: String): ImprovUIState.ConfiguringDevice =
        ImprovUIState.ConfiguringDevice(
            deviceName = deviceName,
            deviceAddress = deviceAddress,
            activeSsid = wifiHelper.getWifiSsid()?.removeSurrounding("\""),
        )

    /**
     * Drives one provisioning attempt for the given device. Sets [uiState] to
     * [ImprovUIState.Provisioning], then collects [ImprovRepository.provisionDevice] events
     * and resolves to [ImprovUIState.Errored] (on [ProvisioningEvent.ErrorOccurred]) or
     * [ImprovUIState.Provisioned] carrying the integration `domain` (on
     * [ProvisioningEvent.Provisioned]). Every state transition is guarded against late events
     * arriving after the user dismissed or restarted — writes only while still in
     * [ImprovUIState.Provisioning], so a cancelled attempt can't resurrect the sheet.
     */
    private suspend fun runProvisioning(deviceName: String, deviceAddress: String, ssid: String, password: String) {
        _uiState.value = ImprovUIState.Provisioning(deviceName = deviceName, deviceAddress = deviceAddress)
        improvRepository.provisionDevice(ImprovDevice(deviceName, deviceAddress), ssid, password).collect { event ->
            when (event) {
                is ProvisioningEvent.StateChanged -> _uiState.update { current ->
                    // Stay in Provisioning only — a late state update mustn't overwrite a terminal
                    // Errored/Provisioned that was set by a previous event.
                    if (current is ImprovUIState.Provisioning) current.copy(state = event.state) else current
                }
                is ProvisioningEvent.ErrorOccurred -> _uiState.update { current ->
                    if (current is ImprovUIState.Provisioning) {
                        ImprovUIState.Errored(deviceName, deviceAddress, event.error)
                    } else {
                        current
                    }
                }
                is ProvisioningEvent.Provisioned -> {
                    var transitioned = false
                    _uiState.update { current ->
                        // Guard against a Provisioned event arriving after the user dismissed,
                        // restarted, or after an Errored event overwriting any of those would
                        // resurrect the UI or misrepresent the outcome.
                        if (current is ImprovUIState.Provisioning) {
                            transitioned = true
                            ImprovUIState.Provisioned(domain = event.domain)
                        } else {
                            transitioned = false
                            current
                        }
                    }
                    // Gate the frontend signal on the same condition telling the frontend setup
                    // is done while the UI is errored/closed would be inconsistent.
                    if (transitioned) {
                        externalBusRepository.send(ImprovDeviceSetupDoneMessage)
                    }
                }
            }
        }
    }

    /**
     * Subscribes to [ImprovRepository.scanDevices] for the lifetime of the call. On each
     * emission: mirrors the list into [discoveredDevices], promotes
     * [ImprovUIState.SearchingDevice] to [ImprovUIState.ConfiguringDevice] when the target
     * appears, and forwards each newly-seen device name to the frontend via
     * [ImprovDiscoveredDeviceMessage] — deduped against [sentDeviceNames] so the frontend sees
     * each name exactly once per session (across collector restarts).
     */
    private suspend fun forwardDiscoveredDevices() {
        improvRepository.scanDevices().collect { devices ->
            val namesToForward = mutex.withLock {
                discoveredDevices = devices
                // Compute the diff inside the lock so it's atomic with the [sentDeviceNames]
                // update; emit outside the lock so the suspending [send] doesn't hold the mutex.
                devices.mapNotNull { it.name }.filter { sentDeviceNames.add(it) }
            }
            // Promote SearchingDevice → ConfiguringDevice as soon as the scan resolves the BLE
            // address. Once we leave SearchingDevice, late scan emissions must not overwrite the
            // user-driven transitions (Provisioning, Errored, …).
            _uiState.update { current ->
                if (current is ImprovUIState.SearchingDevice) {
                    val matched = devices.firstOrNull { it.name == current.deviceName }?.address
                    if (matched != null) {
                        configuringDeviceFor(deviceName = current.deviceName, deviceAddress = matched)
                    } else {
                        current
                    }
                } else {
                    current
                }
            }
            namesToForward.forEach { name ->
                externalBusRepository.send(ImprovDiscoveredDeviceMessage(name = name))
            }
        }
    }

    /**
     * Delegates the full permission flow
     * to [PermissionManager.checkImprovPermissions]. Returns `true` only when every required
     * permission ends up granted.
     */
    private suspend fun ensurePermissions(): Boolean {
        if (improvRepository.hasPermissions()) return true
        return permissionManager.checkImprovPermissions(
            requiredPermissions = improvRepository.requiredPermissions,
        )
    }

    /**
     * HA 2025.6+: `command/navigate` (in-frontend route change), sent directly.
     * Older HA: emits [Event.ReloadAtPath] so the VM can transition its loading state.
     */
    private suspend fun navigateToConfigFlow(domain: String, serverId: Int) {
        val path = "/_my_redirect/config_flow_start?domain=$domain"
        val version = serverManager.getServer(serverId)?.version
        if (NavigateToMessage.isAvailable(version)) {
            externalBusRepository.send(NavigateToMessage(path = path))
        } else {
            _events.emit(Event.ReloadAtPath(path = path, serverId = serverId))
        }
    }

    /**
     * Side-effects the handler can't carry out on its own — the VM has to translate them
     * into view-state transitions.
     */
    sealed interface Event {
        /**
         * Reload the WebView at the given [path] for [serverId]. Emitted only when
         * [NavigateToMessage] isn't available (HA<2025.6).
         */
        data class ReloadAtPath(val path: String, val serverId: Int) : Event
    }
}
