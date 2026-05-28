package io.homeassistant.companion.android.frontend.improv

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.wifi.improv.DeviceState
import com.wifi.improv.ErrorState
import com.wifi.improv.ImprovDevice
import com.wifi.improv.ImprovManager
import com.wifi.improv.ImprovManagerCallback
import io.homeassistant.companion.android.common.util.PermissionChecker
import io.homeassistant.companion.android.common.util.SdkVersion
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

/**
 * Idle window before a refcount-zero `scanDevices()` subscription actually tears down the BLE
 * scan. Lets brief subscriber gaps (e.g. configuration changes, recomposition) avoid the
 * start/stop churn.
 */
@VisibleForTesting
internal const val SCAN_IDLE_WINDOW_MS: Long = 500L

@Singleton
class ImprovRepositoryImpl @VisibleForTesting constructor(
    private val permissionChecker: PermissionChecker,
    improvManagerFactory: ImprovManagerFactory,
    private val shareInScope: CoroutineScope,
    // Injected so tests can pin BLE start/stop hops onto the test scheduler.
    private val backgroundDispatcher: CoroutineDispatcher,
) : ImprovRepository,
    ImprovManagerCallback {

    @Inject
    constructor(permissionChecker: PermissionChecker, improvManagerFactory: ImprovManagerFactory) : this(
        permissionChecker = permissionChecker,
        improvManagerFactory = improvManagerFactory,
        shareInScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        backgroundDispatcher = Dispatchers.IO,
    )

    private val manager: ImprovManager = improvManagerFactory.create(this)

    private val devices = MutableStateFlow(emptyList<ImprovDevice>())
    private val stateEvents = MutableSharedFlow<DeviceState>(extraBufferCapacity = 16)
    private val errorEvents = MutableSharedFlow<ErrorState>(extraBufferCapacity = 16)

    /**
     * Latest RPC result reported by the library. Read once when [ProvisioningEvent.Provisioned]
     * is emitted to extract the integration `domain`. Volatile for cross-thread visibility — the
     * callback may run on the library's internal thread.
     */
    @Volatile
    private var lastRpcResult: List<String> = emptyList()

    private val sharedScanFlow: SharedFlow<List<ImprovDevice>> = devices.asStateFlow()
        .onStart { startScanInternal() }
        .onCompletion { stopScanInternal() }
        .shareIn(shareInScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = SCAN_IDLE_WINDOW_MS), replay = 1)

    override val requiredPermissions: List<String>
        @SuppressLint("InlinedApi")
        get() = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (SdkVersion.isAtLeast(Build.VERSION_CODES.S)) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }

    override fun hasPermissions(): Boolean = requiredPermissions.all { permissionChecker.hasPermission(it) }

    override fun scanDevices(): Flow<List<ImprovDevice>> = sharedScanFlow

    override fun provisionDevice(device: ImprovDevice, ssid: String, password: String): Flow<ProvisioningEvent> =
        channelFlow {
            var credentialsSent = false

            // Forward error events for the duration of the session.
            val errorJob = launch {
                errorEvents.collect { error ->
                    if (error != ErrorState.NO_ERROR) {
                        send(ProvisioningEvent.ErrorOccurred(error))
                    }
                }
            }

            // Forward state events; drive the AUTHORIZED → sendWifi step; close on PROVISIONED.
            val stateJob = launch {
                stateEvents.collect { state ->
                    send(ProvisioningEvent.StateChanged(state))

                    when (state) {
                        DeviceState.AUTHORIZED -> if (!credentialsSent) {
                            try {
                                manager.sendWifi(ssid, password)
                                credentialsSent = true
                            } catch (e: SecurityException) {
                                Timber.e(e, "Not allowed to send Wi-Fi credentials")
                                close(e)
                            }
                        }

                        DeviceState.PROVISIONED -> {
                            val domain = lastRpcResult.firstOrNull()
                                ?.toHttpUrlOrNull()
                                ?.queryParameter("domain")
                            send(ProvisioningEvent.Provisioned(domain))
                            close()
                        }

                        else -> Unit
                    }
                }
            }

            try {
                manager.connectToDevice(device)
            } catch (e: SecurityException) {
                Timber.e(e, "Not allowed to connect to device")
                close(e)
            }

            awaitClose {
                errorJob.cancel()
                stateJob.cancel()
            }
        }

    // region ImprovManagerCallback

    override fun onConnectionStateChange(device: ImprovDevice?) {
        if (device == null) {
            // Disconnect: reset rpc result so a future session starts clean.
            lastRpcResult = emptyList()
        }
    }

    override fun onDeviceFound(device: ImprovDevice) {
        val current = devices.value
        if (!current.contains(device)) {
            devices.tryEmit(current + device)
        }
    }

    override fun onErrorStateChange(errorState: ErrorState) {
        errorEvents.tryEmit(errorState)
    }

    override fun onScanningStateChange(scanning: Boolean) {
        // Library scanning state is implicit in subscription to scanDevices(); not re-exposed.
    }

    override fun onStateChange(state: DeviceState) {
        stateEvents.tryEmit(state)
        if (state == DeviceState.PROVISIONED) {
            // Clear the device list so the next scan starts fresh.
            devices.tryEmit(emptyList())
        }
    }

    override fun onRpcResult(result: List<String>) {
        lastRpcResult = result
    }

    // endregion

    private suspend fun startScanInternal() = withContext(backgroundDispatcher) {
        if (!hasPermissions()) return@withContext
        try {
            manager.findDevices()
        } catch (e: SecurityException) {
            Timber.e(e, "Not allowed to start scanning")
        } catch (e: Exception) {
            Timber.w(e, "Unexpectedly cannot start scanning")
        }
    }

    // [NonCancellable] is mandatory: `stopScanInternal()` is invoked from the upstream's
    // `onCompletion` after shareIn's WhileSubscribed timeout cancels it, so the caller's job is
    // already cancelled. Without [NonCancellable], `withContext` would short-circuit on
    // `ensureActive` and the BLE scan would never be torn down.
    private suspend fun stopScanInternal() = withContext(NonCancellable + backgroundDispatcher) {
        try {
            manager.stopScan()
        } catch (e: Exception) {
            Timber.w(e, "Cannot stop scanning")
        }
    }
}
