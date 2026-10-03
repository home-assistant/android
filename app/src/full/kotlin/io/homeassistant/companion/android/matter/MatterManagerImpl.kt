package io.homeassistant.companion.android.matter

import android.app.Activity
import android.content.ComponentName
import android.os.Build
import android.os.SystemClock
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.annotation.ChecksSdkIntAtLeast
import com.google.android.gms.common.api.ApiException
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
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

// `shareDevice` may never answer. A fixed cap rather than the window's remaining time, which can be very short.
private val SHARE_PREPARE_TIMEOUT = 60.seconds

class MatterManagerImpl @Inject constructor(
    private val serverManager: ServerManager,
    @param:IsAutomotive private val isAutomotive: Boolean,
    private val commissioningClient: CommissioningClient,
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
        return withTimeoutOrNull(SHARE_PREPARE_TIMEOUT) {
            try {
                // Inside the try, so a value Play Services rejects is reported like any other failure.
                val window = CommissioningWindow.builder()
                    .setDiscriminator(Discriminator.forLongValue(request.discriminator))
                    .setPasscode(request.passcode)
                    // As in Google's Matter sample; the API does not say which clock it means.
                    .setWindowOpenMillis(SystemClock.elapsedRealtime())
                    .setDurationSeconds(request.remainingSeconds)
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
                MatterManager.CommissioningResult.Ready(commissioningClient.shareDevice(shareRequest).await())
            } catch (e: CancellationException) {
                // A cancelled Task throws this too, and only a cancelled coroutine may propagate it.
                ensureActive()
                MatterManager.CommissioningResult.Error(e)
            } catch (e: Exception) {
                MatterManager.CommissioningResult.Error(e)
            }
        } ?: MatterManager.CommissioningResult.Error(
            TimeoutException("Play Services did not prepare the share sheet in time"),
        )
    }

    override fun parseSharingIntentResult(result: ActivityResult): MatterManager.SharingRequestResult {
        // A sender that could not be launched also comes back as cancelled, with the reason attached.
        val launchFailed =
            result.data?.hasExtra(StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION) == true
        return when {
            result.resultCode == Activity.RESULT_OK -> MatterManager.SharingRequestResult.Shared
            result.resultCode == Activity.RESULT_CANCELED && !launchFailed ->
                MatterManager.SharingRequestResult.Cancelled

            else -> MatterManager.SharingRequestResult.Failed
        }
    }
}
