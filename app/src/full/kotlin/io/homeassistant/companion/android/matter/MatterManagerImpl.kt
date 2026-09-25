package io.homeassistant.companion.android.matter

import android.app.Activity
import android.content.ComponentName
import android.os.Build
import android.os.SystemClock
import androidx.activity.result.ActivityResult
import androidx.annotation.ChecksSdkIntAtLeast
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstallClient
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState
import com.google.android.gms.home.matter.commissioning.CommissioningClient
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.home.matter.commissioning.CommissioningResult
import com.google.android.gms.home.matter.commissioning.CommissioningWindow
import com.google.android.gms.home.matter.commissioning.ShareDeviceRequest
import com.google.android.gms.home.matter.common.DeviceDescriptor
import com.google.android.gms.home.matter.common.Discriminator
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MatterCommissionResponse
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.di.qualifiers.IsAutomotive
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class MatterManagerImpl @Inject constructor(
    private val serverManager: ServerManager,
    @param:IsAutomotive private val isAutomotive: Boolean,
    private val commissioningClient: CommissioningClient,
    private val moduleInstallClient: ModuleInstallClient,
    @param:MatterCommissioningServiceComponent private val commissioningServiceComponent: ComponentName,
) : MatterManager {

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O_MR1)
    override fun appSupportsCommissioning(): Boolean = SdkVersion.isAtLeast(Build.VERSION_CODES.O_MR1) &&
        !isAutomotive

    override suspend fun coreSupportsCommissioning(serverId: Int): Boolean {
        if (!serverManager.isRegistered() || serverManager.getServer(serverId)?.user?.isAdmin != true) return false
        val config = try {
            serverManager.webSocketRepository(serverId).getConfig()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to get config")
            null
        }
        return config != null && config.components.contains("matter")
    }

    override fun suppressDiscoveryBottomSheet() {
        if (!appSupportsCommissioning()) return
        commissioningClient.suppressHalfSheetNotification()
    }

    override suspend fun prepareMatterDeviceCommissioning(): MatterManager.CommissioningResult {
        if (!appSupportsCommissioning()) {
            return MatterManager.CommissioningResult.Error(
                IllegalStateException("Matter commissioning is not supported on this device"),
            )
        }
        return suspendCancellableCoroutine { cont ->
            commissioningClient
                .commissionDevice(
                    CommissioningRequest.builder()
                        .setCommissioningService(commissioningServiceComponent)
                        .build(),
                )
                .addOnSuccessListener { intentSender ->
                    if (cont.isActive) cont.resume(MatterManager.CommissioningResult.Ready(intentSender))
                }
                .addOnFailureListener { exception ->
                    if (cont.isActive) cont.resume(MatterManager.CommissioningResult.Error(exception))
                }
        }
    }

    override suspend fun commissionDevice(code: String, serverId: Int): MatterCommissionResponse? {
        return try {
            serverManager.webSocketRepository(serverId).commissionMatterDevice(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error while executing server commissioning request")
            null
        }
    }

    override suspend fun commissionOnNetworkDevice(pin: Long, ip: String, serverId: Int): MatterCommissionResponse? {
        return try {
            serverManager.webSocketRepository(serverId).commissionMatterDeviceOnNetwork(pin, ip)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error while executing server commissioning request")
            null
        }
    }

    override fun parseCommissioningIntentResult(result: ActivityResult): MatterManager.CommissioningRequestResult {
        if (result.resultCode != Activity.RESULT_OK) {
            return MatterManager.CommissioningRequestResult.Failed
        }
        return try {
            val commissioningResult = CommissioningResult.fromIntentSenderResult(result.resultCode, result.data)
            MatterManager.CommissioningRequestResult.Success(
                deviceName = commissioningResult.deviceName.takeIf { it.isNotBlank() },
            )
        } catch (e: ApiException) {
            // RESULT_OK means our commissioning service completed, so the device was added even
            // when the result payload cannot be read; only the user-entered name is lost.
            Timber.w(e, "Commissioning succeeded but its result could not be parsed")
            MatterManager.CommissioningRequestResult.Success(deviceName = null)
        }
    }

    override fun appSupportsSharing(): Boolean = appSupportsCommissioning()

    override suspend fun prepareDeviceSharing(request: MatterShareRequest): MatterManager.CommissioningResult {
        if (!appSupportsSharing()) {
            return MatterManager.CommissioningResult.Error(
                IllegalStateException("Matter sharing is not supported on this device"),
            )
        }
        val window = CommissioningWindow.builder()
            .setDiscriminator(Discriminator.forLongValue(request.discriminator))
            .setPasscode(request.passcode)
            // Play Services counts the window in elapsed realtime; the frontend reports what is left of it.
            .setWindowOpenMillis(SystemClock.elapsedRealtime())
            .setDurationSeconds(request.remainingSeconds ?: DEFAULT_COMMISSIONING_WINDOW_SECONDS)
            .build()
        val descriptor = DeviceDescriptor.Builder().apply {
            request.vendorId?.let { setVendorId(it) }
            request.productId?.let { setProductId(it) }
        }.build()
        val shareRequest = ShareDeviceRequest.builder()
            .setDeviceDescriptor(descriptor)
            .setDeviceName(request.deviceName.orEmpty())
            .setCommissioningWindow(window)
            .build()
        // One deadline for the whole preparation: Play Services has been seen to never answer, which would
        // leave the frontend waiting for good while the commissioning window runs out.
        return withTimeoutOrNull(SHARE_PREPARE_TIMEOUT_MILLIS) {
            moduleInstallClient.installMatterModule(commissioningClient)
            try {
                MatterManager.CommissioningResult.Ready(commissioningClient.shareDevice(shareRequest).await())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MatterManager.CommissioningResult.Error(e)
            }
        } ?: MatterManager.CommissioningResult.Error(
            TimeoutException("Play Services did not prepare the share sheet in time"),
        )
    }

    override fun parseSharingIntentResult(result: ActivityResult): MatterManager.SharingRequestResult =
        when (result.resultCode) {
            Activity.RESULT_OK -> MatterManager.SharingRequestResult.Shared
            Activity.RESULT_CANCELED -> MatterManager.SharingRequestResult.Cancelled
            else -> MatterManager.SharingRequestResult.Failed
        }
}

/**
 * Play Services ships the Matter commissioning API as an optional module that is only downloaded
 * on demand, so a device may not have it yet. Requests it and waits for the install within the
 * caller's deadline; if that fails, the share request reports the missing API itself.
 */
private suspend fun ModuleInstallClient.installMatterModule(api: CommissioningClient) {
    try {
        if (areModulesAvailable(api).await().areModulesAvailable()) return
        Timber.i("Installing the Matter module of Google Play services")
        if (!awaitModuleInstall(api)) Timber.w("Matter module of Google Play services not installed")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Could not install the Matter module of Google Play services")
    }
}

private suspend fun ModuleInstallClient.awaitModuleInstall(api: CommissioningClient): Boolean =
    suspendCancellableCoroutine { cont ->
        val listener = object : InstallStatusListener {
            override fun onInstallStatusUpdated(update: ModuleInstallStatusUpdate) {
                val installed = when (update.installState) {
                    InstallState.STATE_COMPLETED -> true
                    InstallState.STATE_FAILED, InstallState.STATE_CANCELED -> false
                    else -> return
                }
                unregisterListener(this)
                if (cont.isActive) cont.resume(installed)
            }
        }
        cont.invokeOnCancellation { unregisterListener(listener) }
        installModules(ModuleInstallRequest.newBuilder().addApi(api).setListener(listener).build())
            .addOnSuccessListener { response ->
                if (response.areModulesAlreadyInstalled()) {
                    unregisterListener(listener)
                    if (cont.isActive) cont.resume(true)
                }
            }
            .addOnFailureListener { exception ->
                Timber.w(exception, "Matter module install request failed")
                unregisterListener(listener)
                if (cont.isActive) cont.resume(false)
            }
    }

/** Window length to assume when the frontend does not report the remaining time. */
private const val DEFAULT_COMMISSIONING_WINDOW_SECONDS = 300L

/**
 * How long preparing the share sheet may take, including downloading the Matter module of Play
 * Services when it is missing, so a stuck call cannot use up the commissioning window.
 */
private const val SHARE_PREPARE_TIMEOUT_MILLIS = 60_000L
