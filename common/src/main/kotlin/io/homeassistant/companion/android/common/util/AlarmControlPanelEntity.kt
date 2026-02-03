package io.homeassistant.companion.android.common.util

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityExt
import timber.log.Timber

fun isAlarmControlPanelEntity(entity: Entity): Boolean {
    return entity.domain == "alarm_control_panel"
}

fun alarmHasNoCode(entity: Entity): Boolean {
    return isAlarmControlPanelEntity(entity) && entity.attributes["code_format"] as? String == null
}

fun alarmCanBeArmedWithoutCode(entity: Entity): Boolean {
    return isAlarmControlPanelEntity(entity) && entity.attributes["code_arm_required"] as? Boolean == false
}

fun supportsAlarmControlPanelArmAway(entity: Entity): Boolean {
    return try {
        if (!isAlarmControlPanelEntity(entity)) {
            return false
        }

        (entity.attributes["supported_features"] as Number?)?.toInt() == EntityExt.ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY
    } catch (e: Exception) {
        Timber.tag(EntityExt.TAG).e(e, "Unable to get supportsArmedAway")
        false
    }
}

fun getAlarmOnPressedAction(entity: Entity): String? {
    if (!isAlarmControlPanelEntity(entity)) {
        return null
    }

    if (entity.state != "disarmed" && alarmHasNoCode(entity)) {
        return "alarm_disarm"
    }

    if (entity.state == "disarmed" &&
        supportsAlarmControlPanelArmAway(entity) &&
        alarmCanBeArmedWithoutCode(entity)
    ) {
        return "alarm_arm_away"
    }

    return null
}
