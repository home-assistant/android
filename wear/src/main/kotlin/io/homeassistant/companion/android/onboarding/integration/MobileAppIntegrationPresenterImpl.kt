package io.homeassistant.companion.android.onboarding.integration

import android.content.Context
import androidx.wear.tiles.TileService
import dagger.hilt.android.qualifiers.ActivityContext
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.common.util.MessagingTokenProvider
import io.homeassistant.companion.android.tiles.CameraTile
import io.homeassistant.companion.android.tiles.ConversationTile
import io.homeassistant.companion.android.tiles.ShortcutsTile
import io.homeassistant.companion.android.tiles.TemplateTile
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class MobileAppIntegrationPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    private val serverManager: ServerManager,
    private val appVersionProvider: AppVersionProvider,
    private val messagingTokenProvider: MessagingTokenProvider,
) : MobileAppIntegrationPresenter {
    private val view = context as MobileAppIntegrationView
    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private suspend fun createRegistration(deviceName: String): DeviceRegistration {
        return DeviceRegistration(
            appVersionProvider(),
            deviceName,
            messagingTokenProvider(),
            false,
        )
    }

    override fun onRegistrationAttempt(serverId: Int, deviceName: String) {
        view.showLoading()
        mainScope.launch {
            val deviceRegistration = createRegistration(deviceName)
            try {
                serverManager.integrationRepository(serverId).registerDevice(deviceRegistration)
                serverManager.convertTemporaryServer(serverId)
            } catch (e: Exception) {
                Timber.e(e, "Unable to register with Home Assistant")
                view.showError()
                return@launch
            }
            updateTiles()
            view.deviceRegistered()
        }
    }

    private fun updateTiles() = mainScope.launch {
        try {
            val context = view as Context
            val updater = TileService.getUpdater(context)
            updater.requestUpdate(CameraTile::class.java)
            updater.requestUpdate(ConversationTile::class.java)
            updater.requestUpdate(ShortcutsTile::class.java)
            updater.requestUpdate(TemplateTile::class.java)
        } catch (e: Exception) {
            Timber.w("Unable to request tiles update")
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
