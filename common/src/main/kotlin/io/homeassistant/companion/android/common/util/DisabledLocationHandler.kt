package io.homeassistant.companion.android.common.util

import android.Manifest
import android.app.Activity
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
        val lm: LocationManager = context.getSystemService()!!

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

    fun removeLocationDisabledWarning(activity: Activity) {
        NotificationManagerCompat.from(activity)
            .cancel(DISABLED_LOCATION_WARN_ID, DISABLED_LOCATION_WARN_ID.hashCode())
    }

    fun showLocationDisabledWarnDialog(
        context: Context,
        settings: Array<String>,
        showAsNotification: Boolean = false,
        withDisableOption: Boolean = false,
        callback: (() -> Unit)? = null,
    ) {
        val positionTextId = commonR.string.confirm_positive
        val negativeTextId = if (withDisableOption && callback != null) {
            commonR.string.location_disabled_option_disable
        } else {
            commonR.string.confirm_negative
        }

        val intent = Intent(
            Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

        if (intent.resolveActivity(context.packageManager) == null) {
            intent.action = Settings.ACTION_SETTINGS
        }

        var parameters = ""
        for (setting in settings) {
            parameters += "- $setting\n"
        }

        if ((!withDisableOption || callback == null) && showAsNotification) {
            val notificationManager = NotificationManagerCompat.from(context)
            if (notificationManager.getActiveNotification(
                    DISABLED_LOCATION_WARN_ID,
                    DISABLED_LOCATION_WARN_ID.hashCode(),
                ) ==
                null
            ) {
                if (VERSION.SDK_INT >= VERSION_CODES.O) {
                    val channel =
                        NotificationChannel(
                            CHANNEL_LOCATION_DISABLED,
                            context.applicationContext.getString(commonR.string.location_warn_channel),
                            NotificationManager.IMPORTANCE_DEFAULT,
                        )
                    notificationManager.createNotificationChannel(channel)
                }

                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE,
                )

                val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_LOCATION_DISABLED)
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

                NotificationManagerCompat.from(
                    context,
                ).notify(DISABLED_LOCATION_WARN_ID, DISABLED_LOCATION_WARN_ID.hashCode(), notificationBuilder.build())
            }
        } else {
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
                    context.applicationContext.startActivity(intent)
                }
                .setNegativeButton(negativeTextId) { _, _ ->
                    if (withDisableOption && callback != null) {
                        callback()
                    }
                }.show()
        }
    }
}
