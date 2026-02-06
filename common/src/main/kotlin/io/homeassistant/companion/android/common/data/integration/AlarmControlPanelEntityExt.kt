package io.homeassistant.companion.android.common.data.integration

import timber.log.Timber

const val ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY = 2

fun Entity.isAlarmControlPanelEntity(): Boolean {
    return domain == "alarm_control_panel"
}

fun Entity.alarmHasNoCode(): Boolean {
    return isAlarmControlPanelEntity() && (attributes["code_format"] as? String)?.isNotEmpty() != true
}

fun Entity.alarmCanBeArmedWithoutCode(): Boolean {
    return isAlarmControlPanelEntity() && attributes["code_arm_required"] as? Boolean == false
}

fun Entity.supportsAlarmControlPanelArmAway(): Boolean {
    return try {
        if (!isAlarmControlPanelEntity()) {
            return false
        }

        (attributes["supported_features"] as Number).toInt() and
            ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY == ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY
    } catch (e: Exception) {
        Timber.tag(EntityExt.TAG).e(e, "Unable to get supportsArmedAway")
        false
    }
}

fun Entity.alarmIsDisarmed(): Boolean {
    return state == "disarmed"
}

fun Entity.alarmCanBeArmedAway(): Boolean {
    if (!alarmIsDisarmed() || !supportsAlarmControlPanelArmAway()) {
        return false
    }

    return alarmHasNoCode() || alarmCanBeArmedWithoutCode()
}

fun Entity.alarmCanBeDisarmed(): Boolean {
    return !alarmIsDisarmed() && alarmHasNoCode()
}

fun Entity.isAlarmActionable(): Boolean {
    return alarmCanBeDisarmed() || alarmCanBeArmedAway()
}

fun Entity.getAlarmOnPressedAction(): String? {
    if (alarmCanBeDisarmed()) {
        return "alarm_disarm"
    }

    if (alarmCanBeArmedAway()) {
        return "alarm_arm_away"
    }

    return null
}
