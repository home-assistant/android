package io.homeassistant.companion.android.webview.addto

import android.content.Context
import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CAMERA_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.IMAGE_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.TODO_DOMAIN
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.util.QuestUtil
import io.homeassistant.companion.android.util.vehicle.isVehicleDomain
import io.homeassistant.companion.android.widgets.camera.CameraWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.entity.EntityWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.mediaplayer.MediaPlayerControlsWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.todo.TodoWidgetConfigureActivity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles the "Add To" functionality for Home Assistant entities, allowing users to add entities
 * to various Android platform features and connected devices.
 *
 * This class provides two main capabilities:
 * 1. Determining which actions are available for a given entity based on its type and domain
 * 2. Executing the selected action to add the entity to the chosen platform feature
 *
 * The available actions depend on the entity's domain and current system state (for example, watch connectivity,
 * shortcut limits).
 */
class EntityAddToHandler @Inject constructor(
    private val serverManager: ServerManager,
    private val prefsRepository: PrefsRepository,
) {

    /**
     * Returns the list of available actions for the specified entity.
     *
     * The available actions depend on the entity's domain. For example, media player entities
     * will include both the general entity widget and a specialized media player widget option.
     * Vehicle-related entities will include Android Auto favorites. Camera and image entities
     * will include camera widget options.
     *
     * @param context An android context
     * @param entityId The entity ID to get available actions for (for example, "light.living_room")
     * @return List of actions that can be performed for this entity. Returns an empty list if the
     *         entity is not found or if the server is unavailable.
     */
    suspend fun actionsForEntity(context: Context, entityId: String): List<EntityAddToAction> {
        return actionsForEntity(
            isFullFlavor = BuildConfig.FLAVOR == "full",
            isAutomotive = context.isAutomotive(),
            isQuest = QuestUtil.isQuest,
            entityId,
        )
    }

    @VisibleForTesting
    suspend fun actionsForEntity(
        isFullFlavor: Boolean,
        isAutomotive: Boolean,
        isQuest: Boolean,
        entityId: String,
    ): List<EntityAddToAction> {
        return withContext(Dispatchers.Default) {
            val actions = mutableListOf<EntityAddToAction>()
            serverManager.getServer()?.let { server ->
                serverManager.integrationRepository(server.id).getEntity(entityId)
                    ?.let { entity ->
                        if (!isAutomotive && !isQuest) {
                            actions.add(EntityAddToAction.EntityWidget)

                            if (entity.domain == MEDIA_PLAYER_DOMAIN) {
                                actions.add(EntityAddToAction.MediaPlayerWidget)
                            }

                            if (entity.domain == TODO_DOMAIN) {
                                actions.add(EntityAddToAction.TodoWidget)
                            }

                            if (entity.domain == CAMERA_DOMAIN || entity.domain == IMAGE_DOMAIN) {
                                actions.add(EntityAddToAction.CameraWidget)
                            }
                        }

                        if (isVehicleDomain(entity) && (isFullFlavor || isAutomotive)) {
                            // We could check if it already exist but the action won't do anything so we can keep it
                            actions.add(EntityAddToAction.AndroidAutoFavorite)
                        }
                    }
            }
            actions
        }
    }

    /**
     * Executes the specified action to add the entity to the chosen platform feature.
     *
     * This function performs the appropriate operation based on the action type.
     *
     * @param context Android context used for starting activities and accessing system services
     * @param action The action to execute (determines what platform feature to add the entity to)
     * @param entityId The entity ID to add (for example, "light.living_room")
     * @param onShowSnackbar A lambda to show a snackbar that takes a message and an action as parameters
     *  and returns true when the action is performed
     */
    suspend fun execute(
        context: Context,
        action: EntityAddToAction,
        entityId: String,
        onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    ) {
        when (action) {
            is EntityAddToAction.AndroidAutoFavorite -> {
                addToAndroidAutoFavorite(entityId)
                onShowSnackbar(context.getString(commonR.string.add_to_android_auto_success), null)
            }

            is EntityAddToAction.Tile -> {
                // TODO go to a new tile https://github.com/home-assistant/android/issues/5623
            }
            is EntityAddToAction.EntityWidget -> {
                context.startActivity(
                    EntityWidgetConfigureActivity.newInstance(
                        context = context,
                        entityId = entityId,
                    ),
                )
            }
            is EntityAddToAction.MediaPlayerWidget -> {
                context.startActivity(
                    MediaPlayerControlsWidgetConfigureActivity.newInstance(
                        context = context,
                        entityId = entityId,
                    ),
                )
            }
            is EntityAddToAction.CameraWidget -> {
                context.startActivity(
                    CameraWidgetConfigureActivity.newInstance(
                        context = context,
                        entityId = entityId,
                    ),
                )
            }
            is EntityAddToAction.TodoWidget -> {
                context.startActivity(
                    TodoWidgetConfigureActivity.newInstance(
                        context = context,
                        entityId = entityId,
                    ),
                )
            }
            is EntityAddToAction.Shortcut -> {
                // TODO support shortcut https://github.com/home-assistant/android/issues/5625
            }
            is EntityAddToAction.Watch -> {
                // TODO support watch favorite https://github.com/home-assistant/android/issues/5624
            }
        }
    }

    private suspend fun addToAndroidAutoFavorite(entityId: String) {
        serverManager.getServer()?.id?.let { serverId ->
            prefsRepository.addAutoFavorite(AutoFavorite(serverId, entityId))
        } ?: FailFast.fail { "Server is null" }
    }
}
