package io.homeassistant.companion.android.tiles.dashboard

import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardAction
import io.homeassistant.companion.android.common.data.wear.dashboard.model.wearDashboardJson
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps tile clickable IDs to dashboard actions for the current tile render cycle.
 */
@Singleton
class WearDashboardActionSerializer @Inject constructor() {

    private val actions = ConcurrentHashMap<String, WearDashboardAction>()

    /**
     * Clears actions registered during a previous tile render.
     */
    fun clear() {
        actions.clear()
    }

    /**
     * Registers [action] and returns the clickable ID assigned to it.
     */
    fun registerAction(action: WearDashboardAction, componentId: String?): String {
        val clickableId = componentId?.let { "$ACTION_ID_PREFIX$it" } ?: "$ACTION_ID_PREFIX${actions.size}"
        actions[clickableId] = action
        return clickableId
    }

    /**
     * Returns the action associated with [clickableId], if any.
     */
    fun getAction(clickableId: String): WearDashboardAction? = actions[clickableId]

    /**
     * Serializes [action] for intent transport.
     */
    fun serializeAction(action: WearDashboardAction): String =
        wearDashboardJson.encodeToString(WearDashboardAction.serializer(), action)

    /**
     * Deserializes an action payload from a tile intent extra.
     */
    fun deserializeAction(payload: String): WearDashboardAction =
        wearDashboardJson.decodeFromString(WearDashboardAction.serializer(), payload)

    companion object {
        const val ACTION_ID_PREFIX = "wd_action:"
        const val ACTION_BROADCAST = "io.homeassistant.companion.android.WEAR_DASHBOARD_TILE_ACTION"
        const val EXTRA_ACTION_JSON = "wear_dashboard_action_json"
        const val EXTRA_TILE_ID = "wear_dashboard_tile_id"
    }
}
