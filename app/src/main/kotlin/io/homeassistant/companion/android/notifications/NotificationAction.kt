package io.homeassistant.companion.android.notifications

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class NotificationAction(
    val key: String,
    val title: String,
    val uri: String?,
    val behavior: String?,
    var data: Map<String, String>,
) : Parcelable
