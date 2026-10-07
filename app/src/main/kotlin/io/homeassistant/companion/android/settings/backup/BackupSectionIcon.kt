package io.homeassistant.companion.android.settings.backup

import androidx.annotation.DrawableRes
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR

internal val BackupSection.icon: Int
    @DrawableRes
    get() = when (this) {
        BackupSection.AndroidAutoFavorites -> R.drawable.ic_car
        BackupSection.Sensors, BackupSection.SensorOptions -> commonR.drawable.leak
        BackupSection.Connection -> R.drawable.ic_websocket
        BackupSection.Frequency -> R.drawable.ic_clock_fast
    }
