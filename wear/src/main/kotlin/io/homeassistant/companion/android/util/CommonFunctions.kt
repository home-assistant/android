package io.homeassistant.companion.android.util

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.mikepenz.iconics.typeface.IIcon
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.home.HomePresenterImpl
import java.time.LocalDateTime

fun stringForDomain(domain: String, context: Context): String? = (
    HomePresenterImpl.domainsWithNames + mapOf(
        "automation" to commonR.string.automation,
        "binary_sensor" to commonR.string.binary_sensor,
        "device_tracker" to commonR.string.device_tracker,
        "input_number" to commonR.string.domain_input_number,
        MEDIA_PLAYER_DOMAIN to commonR.string.media_player,
        "persistent_notification" to commonR.string.persistent_notification,
        "person" to commonR.string.person,
        "select" to commonR.string.select,
        "sensor" to commonR.string.sensor,
        "sun" to commonR.string.sun,
        "update" to commonR.string.update,
        "weather" to commonR.string.weather,
        "zone" to commonR.string.zone,
    )
    )[domain]?.let { context.getString(it) }

fun getIcon(icon: String?, domain: String, context: Context): IIcon {
    val simpleEntity = Entity(
        "$domain.ha_android_placeholder",
        "",
        mapOf("icon" to icon),
        LocalDateTime.now(),
        LocalDateTime.now(),
    )
    return simpleEntity.getIcon(context)
}

fun onEntityClickedFeedback(
    isToastEnabled: Boolean,
    isHapticEnabled: Boolean,
    context: Context,
    friendlyName: String,
    haptic: HapticFeedback,
) {
    val message = context.getString(commonR.string.toast_message, friendlyName)
    onEntityFeedback(isToastEnabled, isHapticEnabled, message, context, haptic)
}

fun onEntityFeedback(
    isToastEnabled: Boolean,
    isHapticEnabled: Boolean,
    message: String,
    context: Context,
    haptic: HapticFeedback,
) {
    if (isToastEnabled) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    if (isHapticEnabled) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}
