package io.homeassistant.companion.android.tiles.dashboard

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.homeassistant.companion.android.common.data.integration.onEntityPressedWithoutState
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardAction
import io.homeassistant.companion.android.home.HomeActivity
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import timber.log.Timber

/**
 * Executes Wear Dashboard actions triggered from tiles.
 */
class WearDashboardActionExecutor @Inject constructor(
    private val serverManager: ServerManager,
    private val wearPrefsRepository: WearPrefsRepository,
) {

    /**
     * Executes [action] from a tile interaction.
     */
    suspend fun execute(context: Context, action: WearDashboardAction, tileId: Int?) {
        when (action) {
            is WearDashboardAction.ToggleEntity -> executeToggleEntity(action)
            is WearDashboardAction.CallService -> executeCallService(action)
            is WearDashboardAction.Refresh -> WearDashboardTile.requestUpdate(context)
            is WearDashboardAction.OpenFullDashboard -> openFullDashboard(context, action)
            is WearDashboardAction.Navigate -> openFullDashboard(
                context,
                WearDashboardAction.OpenFullDashboard(
                    dashboardId = action.dashboardId,
                    pageId = action.pageId,
                ),
            )
        }
    }

    /**
     * Returns whether [action] should be confirmed before execution on a tile.
     */
    fun requiresConfirmation(action: WearDashboardAction): Boolean = when (action) {
        is WearDashboardAction.CallService -> {
            action.requiresConfirmation ||
                action.domain in SENSITIVE_DOMAINS ||
                action.service in SENSITIVE_SERVICES
        }
        is WearDashboardAction.ToggleEntity -> action.entityId.substringBefore('.') in SENSITIVE_DOMAINS
        else -> false
    }

    private suspend fun executeToggleEntity(action: WearDashboardAction.ToggleEntity) {
        onEntityPressedWithoutState(
            entityId = action.entityId,
            integrationRepository = serverManager.integrationRepository(),
        )
    }

    private suspend fun executeCallService(action: WearDashboardAction.CallService) {
        val actionData = jsonMapToActionData(action.data)
        serverManager.integrationRepository().callAction(
            domain = action.domain,
            action = action.service,
            actionData = actionData,
        )
    }

    private fun openFullDashboard(context: Context, action: WearDashboardAction.OpenFullDashboard) {
        val pageSegment = action.pageId?.let { "/$it" }.orEmpty()
        val intent = Intent(
            Intent.ACTION_VIEW,
            "homeassistant://wear-dashboard/${action.dashboardId}$pageSegment".toUri(),
            context,
            HomeActivity::class.java,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun jsonMapToActionData(data: Map<String, JsonElement>): Map<String, Any?> =
        data.mapValues { (_, element) -> jsonElementToAny(element) }

    private fun jsonElementToAny(element: JsonElement): Any? = when (element) {
        is JsonPrimitive -> when {
            element.isString -> element.contentOrNull
            element.booleanOrNull != null -> element.booleanOrNull
            element.intOrNull != null -> element.intOrNull
            element.longOrNull != null -> element.longOrNull
            element.doubleOrNull != null -> element.doubleOrNull
            element.floatOrNull != null -> element.floatOrNull
            else -> null
        }
        is JsonObject -> element.mapValues { (_, value) -> jsonElementToAny(value) }
        is JsonArray -> element.map { jsonElementToAny(it) }
    }

    companion object {
        private val SENSITIVE_DOMAINS = setOf("lock", "cover", "alarm_control_panel")
        private val SENSITIVE_SERVICES = setOf("lock", "unlock", "open_cover", "close_cover", "arm_away", "disarm")
    }
}
