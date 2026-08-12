package io.homeassistant.companion.android.frontend.addto

import android.content.Context
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CAMERA_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.IMAGE_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.TODO_DOMAIN
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.di.qualifiers.IsAutomotive
import io.homeassistant.companion.android.frontend.navigation.FrontendEvent
import io.homeassistant.companion.android.frontend.navigation.WidgetType
import io.homeassistant.companion.android.util.QuestUtil
import io.homeassistant.companion.android.util.vehicle.isVehicleDomain
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages the "Add To" functionality for Home Assistant entities, allowing users to add entities
 * to various Android platform features and connected devices.
 *
 * This class provides two main capabilities:
 * 1. Determining which actions are available for a given entity based on its type and domain
 * 2. Executing the selected action to add the entity to the chosen platform feature
 *
 * The available actions depend on the entity's domain and current system state (for example, watch connectivity,
 * shortcut limits).
 */
@ViewModelScoped
class FrontendEntityAddToManager @VisibleForTesting constructor(
    private val context: Context,
    private val serverManager: ServerManager,
    private val prefsRepository: PrefsRepository,
    private val isAutomotive: Boolean,
    private val isQuest: Boolean,
    private val isFullFlavor: Boolean,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        serverManager: ServerManager,
        prefsRepository: PrefsRepository,
        @IsAutomotive isAutomotive: Boolean,
    ) : this(
        context = context,
        serverManager = serverManager,
        prefsRepository = prefsRepository,
        isAutomotive = isAutomotive,
        isQuest = QuestUtil.isQuest,
        isFullFlavor = BuildConfig.FLAVOR == "full",
    )

    /**
     * Returns the list of available actions for the specified entity.
     *
     * The available actions depend on the entity's domain. For example, media player entities
     * will include both the general entity widget and a specialized media player widget option.
     * Vehicle-related entities will include Android Auto favorites. Camera and image entities
     * will include camera widget options.
     *
     * @param entityId The entity ID to get available actions for (for example, "light.living_room")
     * @return List of actions that can be performed for this entity. Returns an empty list if the
     *         entity is not found or if the server is unavailable.
     */
    suspend fun getActionsForEntity(entityId: String): List<ExternalEntityAddToAction> =
        withContext(Dispatchers.Default) {
            val server = serverManager.getServer() ?: return@withContext emptyList()
            val entity = try {
                serverManager.integrationRepository(server.id).getEntity(entityId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to get entity for id $entityId")
                null
            } ?: return@withContext emptyList()

            val actions = mutableListOf<EntityAddToAction>()

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
                actions.add(EntityAddToAction.AndroidAutoFavorite)
            }

            actions.map { action -> ExternalEntityAddToAction.fromAction(context, action) }
        }

    /**
     * Executes an EntityAddTo action and returns the corresponding [FrontendEvent].
     *
     * For [EntityAddToAction.AndroidAutoFavorite], persists the favorite and returns a snackbar event.
     * For widget actions, returns a [FrontendEvent.NavigateToWidgetConfig].
     * For unimplemented actions (Shortcut, Tile, Watch), returns null.
     */
    suspend fun execute(entityId: String, action: EntityAddToAction): FrontendEvent? {
        return when (action) {
            is EntityAddToAction.AndroidAutoFavorite -> {
                addToAndroidAutoFavorite(entityId)
                FrontendEvent.ShowSnackbar(commonR.string.add_to_android_auto_success)
            }
            is EntityAddToAction.EntityWidget ->
                FrontendEvent.NavigateToWidgetConfig(entityId, WidgetType.Entity)
            is EntityAddToAction.MediaPlayerWidget ->
                FrontendEvent.NavigateToWidgetConfig(entityId, WidgetType.MediaPlayer)
            is EntityAddToAction.CameraWidget ->
                FrontendEvent.NavigateToWidgetConfig(entityId, WidgetType.Camera)
            is EntityAddToAction.TodoWidget ->
                FrontendEvent.NavigateToWidgetConfig(entityId, WidgetType.Todo)
            is EntityAddToAction.Shortcut,
            is EntityAddToAction.Tile,
            is EntityAddToAction.Watch,
            -> null
        }
    }

    /**
     * Persists the entity as an Android Auto favorite for the active server.
     *
     * Called from both the frontend [execute] path and the legacy webview handler so
     * the favorite-storage logic stays in a single place.
     */
    private suspend fun addToAndroidAutoFavorite(entityId: String) {
        val serverId = serverManager.getServer()?.id
        if (serverId != null) {
            prefsRepository.addAutoFavorite(AutoFavorite(serverId, entityId))
        } else {
            FailFast.fail { "Server is null when adding auto favorite" }
        }
    }
}
