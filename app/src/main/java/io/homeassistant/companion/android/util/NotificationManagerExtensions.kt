package io.homeassistant.companion.android.util

import android.app.NotificationManager
import androidx.car.app.notification.CarNotificationManager
import io.homeassistant.companion.android.common.util.cancelNotificationGroupIfNeeded

fun CarNotificationManager.cancelGroupIfNeeded(manager: NotificationManager, tag: String?, id: Int): Boolean =
    cancelNotificationGroupIfNeeded(
        manager,
        tag,
        id,
        this::cancel
    )
