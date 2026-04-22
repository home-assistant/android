package io.homeassistant.companion.android.matter

import android.content.ComponentName
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MatterCommissionResponse
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.util.sensitive
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

class MatterManagerImpl @Inject constructor(
    private val serverManager: ServerManager,
    private val packageManager: PackageManager,
) : MatterManager {

    override fun appSupportsCommissioning(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
        !packageManager.isAutomotive()

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

    override fun suppressDiscoveryBottomSheet(context: Context) {
        if (!appSupportsCommissioning()) return
        Matter.getCommissioningClient(context).suppressHalfSheetNotification()
    }

    override fun startNewCommissioningFlow(
        context: Context,
        onSuccess: (IntentSender) -> Unit,
        onFailure: (Exception) -> Unit,
    ) {
        if (appSupportsCommissioning()) {
            Matter.getCommissioningClient(context)
                .commissionDevice(
                    CommissioningRequest.builder()
                        .setCommissioningService(ComponentName(context, MatterCommissioningService::class.java))
                        .build(),
                )
                .addOnSuccessListener { onSuccess(it) }
                .addOnFailureListener { onFailure(it) }
        } else {
            onFailure(IllegalStateException("Matter commissioning is not supported on SDK <27"))
        }
    }

    override suspend fun commissionDevice(code: String, serverId: Int): MatterCommissionResponse? {
        logCommissioningUrlState(serverId, "commissionDevice")
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
        logCommissioningUrlState(serverId, "commissionOnNetworkDevice")
        return try {
            serverManager.webSocketRepository(serverId).commissionMatterDeviceOnNetwork(pin, ip)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error while executing server commissioning request")
            null
        }
    }

    /**
     * Issue #6367: Matter commissioning is a local-network operation and can
     * only succeed when the WebSocket talks to Home Assistant on a URL that is
     * reachable from the phone's current network. If the app has fallen back
     * to the external URL while the phone is on the home network the
     * commissioning request times out silently — the symptom reported in
     * #6367. Surface the URL state at the start of every commissioning attempt
     * so the failure mode is diagnosable from `Settings → Companion App
     * Troubleshooting → Show and Share Logs` without re-running with extra
     * instrumentation. The actual URL routing decision is made inside
     * `ServerConnectionStateProvider`/`WebSocketRepository`; this is purely an
     * observability change and cannot regress a working setup.
     *
     * The server URL itself is wrapped in [sensitive] so it is only emitted in
     * debug builds, matching the rest of the codebase's logging convention.
     */
    private fun logCommissioningUrlState(serverId: Int, source: String) {
        val connection = serverManager.getServer(serverId)?.connection ?: return
        val hasInternalUrl = !connection.internalUrl.isNullOrBlank()
        val prioritizeInternal = connection.prioritizeInternal
        val useCloud = connection.useCloud
        val hasHomeNetworkSetup = connection.hasHomeNetworkSetup
        val externalUrl = sensitive(connection.externalUrl)
        val internalUrl = sensitive(connection.internalUrl.orEmpty())
        Timber.w(
            "Matter $source starting for server #$serverId " +
                "(hasInternalUrl=$hasInternalUrl, prioritizeInternal=$prioritizeInternal, " +
                "useCloud=$useCloud, hasHomeNetworkSetup=$hasHomeNetworkSetup, " +
                "externalUrl=$externalUrl, internalUrl=$internalUrl). " +
                "If this request times out check Companion → Settings → Server → " +
                "'Prioritize internal URL' — Matter requires a local-network connection.",
        )
    }
}
