package io.homeassistant.companion.android.matter

import android.content.ComponentName
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import com.google.android.gms.home.matter.commissioning.CommissioningClient
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MatterCommissionResponse
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.di.qualifiers.IsAutomotive
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

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
}
