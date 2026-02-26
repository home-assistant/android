package io.homeassistant.companion.android.common.data.integration

import timber.log.Timber

const val ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY = 2

private fun Entity.isAlarmControlPanelEntity(): Boolean {
    return domain == "alarm_control_panel"
}

private fun Entity.alarmHasNoCode(): Boolean {
    return isAlarmControlPanelEntity() && (attributes["code_format"] as? String)?.isNotEmpty() != true
}

private fun Entity.alarmCanBeArmedWithoutCode(): Boolean {
    return isAlarmControlPanelEntity() && attributes["code_arm_required"] as? Boolean == false
}

private fun Entity.supportsAlarmControlPanelArmAway(): Boolean {
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

private fun Entity.alarmIsDisarmed(): Boolean {
    return isAlarmControlPanelEntity() && state == "disarmed"
}

private fun Entity.alarmCanBeArmedAway(): Boolean {
    if(!isAlarmControlPanelEntity()) {
        return false
    }

    if (!alarmIsDisarmed() || !supportsAlarmControlPanelArmAway()) {
        return false
    }

    return alarmHasNoCode() || alarmCanBeArmedWithoutCode()
}

private fun Entity.alarmCanBeDisarmed(): Boolean {
    return isAlarmControlPanelEntity() && !alarmIsDisarmed() && alarmHasNoCode()
}

fun Entity.isAlarmActionable(): Boolean {
    return isAlarmControlPanelEntity() && alarmCanBeDisarmed() || alarmCanBeArmedAway()
}

fun Entity.getAlarmOnPressedAction(): String? {
    if(!isAlarmControlPanelEntity()) {
        return null
    }

    if (alarmCanBeDisarmed()) {
        return "alarm_disarm"
    }

    if (alarmCanBeArmedAway()) {
        return "alarm_arm_away"
    }

    return null
}
