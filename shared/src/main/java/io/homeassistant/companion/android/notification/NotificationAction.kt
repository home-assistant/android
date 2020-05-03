package io.homeassistant.companion.android.notification

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class NotificationAction(
    val key: String,
    val title: String,
    val uri: String?,
    val data: Map<String, String>
) : Parcelable
