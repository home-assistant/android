package io.homeassistant.companion.android.tiles.dashboard

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardUpdateCoordinator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Requests Wear Dashboard tile updates when resolved dashboard state changes.
 */
@Singleton
class WearDashboardTileUpdateWorker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateCoordinator: WearDashboardUpdateCoordinator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            updateCoordinator.tileUpdateRequests.collect {
                try {
                    WearDashboardTile.requestUpdate(context)
                } catch (e: Exception) {
                    Timber.w(e, "Unable to request wear dashboard tile update")
                }
            }
        }
    }
}
