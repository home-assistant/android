package io.homeassistant.companion.android.dashboard.ongoing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.wear.ongoing.OngoingActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardDependencyExtractor
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardResolvedState
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.home.HomeActivity
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

private const val ONGOING_NOTIFICATION_CHANNEL = "wear_dashboard_ongoing"
private const val LIVE_UPDATES_MIN_SDK = 36

/**
 * Shows or hides Wear [OngoingActivity] surfaces for dashboards with `surfaces.ongoingActivity`.
 *
 * On API 36 and above, prefer Wear Live Updates when available; this manager uses
 * [OngoingActivity] for API levels below 36 for backwards compatibility.
 */
@Singleton
class WearDashboardOngoingActivityManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val activeDashboardIds = mutableSetOf<String>()

    /**
     * Updates ongoing activity visibility from resolved [state] for [config].
     */
    fun sync(config: WearDashboardConfig, state: WearDashboardResolvedState) {
        val ongoingPageId = config.surfaces.ongoingActivity?.page ?: run {
            stop(config.id)
            return
        }

        if (SdkVersion.isAtLeast(LIVE_UPDATES_MIN_SDK)) {
            // Live Updates (API 36+) will replace OngoingActivity; gate until integrated.
            Timber.d("Skipping OngoingActivity for dashboard id=${config.id}; Live Updates require API 36+")
            stop(config.id)
            return
        }

        if (!shouldShowOngoing(config, ongoingPageId, state)) {
            stop(config.id)
            return
        }

        start(config)
    }

    /** Stops the ongoing activity for [dashboardId], if shown. */
    fun stop(dashboardId: String) {
        if (!activeDashboardIds.remove(dashboardId)) return
        NotificationManagerCompat.from(context).cancel(notificationId(dashboardId))
    }

    private fun shouldShowOngoing(
        config: WearDashboardConfig,
        ongoingPageId: String,
        state: WearDashboardResolvedState,
    ): Boolean {
        val page = config.pages.firstOrNull { it.id == ongoingPageId } ?: return false
        val dependencies = WearDashboardDependencyExtractor.extract(
            config.copy(pages = listOf(page)),
        )
        return dependencies.entityIds.any { entityId ->
            val key = "entity:$entityId"
            val display = state.bindingValues[key].orEmpty().lowercase()
            display.isNotEmpty() && display !in INACTIVE_ENTITY_STATES
        }
    }

    private fun start(config: WearDashboardConfig) {
        if (activeDashboardIds.contains(config.id)) return
        activeDashboardIds.add(config.id)

        val notificationId = notificationId(config.id)
        val pageId = config.surfaces.ongoingActivity?.page
        val touchIntent = PendingIntent.getActivity(
            context,
            notificationId,
            wearDashboardIntent(config.id, pageId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        ensureNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(context, ONGOING_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(config.title ?: context.getString(io.homeassistant.companion.android.common.R.string.wear_dashboard))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(touchIntent)

        val ongoingActivity = OngoingActivity.Builder(context, notificationId, notificationBuilder)
            .setStaticIcon(R.mipmap.ic_launcher)
            .setTouchIntent(touchIntent)
            .build()

        ongoingActivity.apply(context)
        NotificationManagerCompat.from(context).notify(notificationId, notificationBuilder.build())
    }

    private fun wearDashboardIntent(dashboardId: String, pageId: String?): Intent {
        val pageSegment = pageId?.let { "/$it" }.orEmpty()
        return Intent(
            Intent.ACTION_VIEW,
            "homeassistant://wear-dashboard/$dashboardId$pageSegment".toUri(),
            context,
            HomeActivity::class.java,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun notificationId(dashboardId: String): Int = dashboardId.hashCode() and 0x7FFFFFFF

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            ONGOING_NOTIFICATION_CHANNEL,
            context.getString(io.homeassistant.companion.android.common.R.string.wear_dashboard),
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    companion object {
        private val INACTIVE_ENTITY_STATES = setOf(
            "off",
            "closed",
            "locked",
            "unavailable",
            "unknown",
            "idle",
            "not_home",
        )
    }
}
