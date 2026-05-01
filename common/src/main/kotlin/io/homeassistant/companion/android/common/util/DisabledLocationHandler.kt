package io.homeassistant.companion.android.common.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR

object DisabledLocationHandler {
    private const val DISABLED_LOCATION_WARN_ID = "DisabledLocationWarning"

    fun hasGPS(context: Context): Boolean {
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
    }

    fun isLocationEnabled(context: Context): Boolean {
        val lm: LocationManager = context.getSystemService() ?: return false

        return if (VERSION.SDK_INT >= VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                lm.isProviderEnabled(
                    LocationManager.GPS_PROVIDER,
                )
        }
    }

    fun containsLocationPermission(permissions: Array<String>, fineLocation: Boolean? = null): Boolean {
        val containsFineLocation = permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)
        val containsCoarseLocation = permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)

        return if (fineLocation == null) {
            containsFineLocation && containsCoarseLocation
        } else {
            if (fineLocation) {
                containsFineLocation
            } else {
                containsCoarseLocation
            }
        }
    }

    fun removeLocationDisabledWarning(context: Context) {
        NotificationManagerCompat.from(context)
            .cancel(DISABLED_LOCATION_WARN_ID, DISABLED_LOCATION_WARN_ID.hashCode())
    }

    /**
     * Creates an [Intent] that opens the system location settings screen.
     *
     * Falls back to general settings if the location settings activity is not available.
     */
    @SuppressLint("QueryPermissionsNeeded")
    fun locationSettingsIntent(context: Context): Intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        if (resolveActivity(context.packageManager) == null) {
            action = Settings.ACTION_SETTINGS
        }
    }

    /**
     * Shows a persistent notification warning the user that location is disabled,
     * listing the [settings] that require it. Tapping the notification opens location settings.
     *
     * No-ops if the notification is already visible.
     */
    @SuppressLint("MissingPermission")
    fun showLocationDisabledNotification(context: Context, settings: Array<String>) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (notificationManager.getActiveNotification(
                DISABLED_LOCATION_WARN_ID,
                DISABLED_LOCATION_WARN_ID.hashCode(),
            ) != null
        ) {
            return
        }

        val parameters = settings.joinToString(separator = "\n") { "- $it" }

        if (VERSION.SDK_INT >= VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_LOCATION_DISABLED,
                context.applicationContext.getString(commonR.string.location_warn_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            locationSettingsIntent(context),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_LOCATION_DISABLED)
            .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
            .setColor(Color.RED)
            .setOngoing(true)
            .setContentTitle(context.applicationContext.getString(commonR.string.location_disabled_title))
            .setContentText(
                context.applicationContext.getString(
                    commonR.string.location_disabled_notification_short_message,
                ),
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        context.applicationContext.getString(
                            commonR.string.location_disabled_notification_message,
                            parameters,
                        ),
                    ),
            )
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(DISABLED_LOCATION_WARN_ID, DISABLED_LOCATION_WARN_ID.hashCode(), notification)
    }

    /**
     * Shows a dialog warning the user that location is disabled, listing the [settings] that
     * require it. The positive button opens location settings.
     *
     * @param withDisableOption when true and [callback] is provided, the negative button label
     *   becomes a "disable" option that triggers [callback] instead of simply dismissing.
     */
    fun showLocationDisabledWarnDialog(
        context: Context,
        settings: Array<String>,
        withDisableOption: Boolean = false,
        callback: (() -> Unit)? = null,
    ) {
        val positionTextId = commonR.string.confirm_positive
        val negativeTextId = if (withDisableOption && callback != null) {
            commonR.string.location_disabled_option_disable
        } else {
            commonR.string.confirm_negative
        }

        val parameters = settings.joinToString(separator = "\n") { "- $it" }

        AlertDialog.Builder(context)
            .setTitle(commonR.string.location_disabled_title)
            .setMessage(
                context.applicationContext.getString(
                    commonR.string.location_disabled_dialog_message,
                    context.applicationContext.getString(
                        commonR.string.location_disabled_notification_message,
                        parameters,
                    ),
                ),
            )
            .setPositiveButton(positionTextId) { _, _ ->
                context.applicationContext.startActivity(locationSettingsIntent(context))
            }
            .setNegativeButton(negativeTextId) { _, _ ->
                if (withDisableOption && callback != null) {
                    callback()
                }
            }.show()
    }
}
